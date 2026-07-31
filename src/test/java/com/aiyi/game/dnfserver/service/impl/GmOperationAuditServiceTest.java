package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.exception.ValidationException;
import com.aiyi.game.dnfserver.entity.gm.GmOperationContext;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GmOperationAuditServiceTest {

    @Test
    public void startsPendingAuditAndReturnsGeneratedId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("FROM dnf_service.gm_operation_audit"), eq("req-1")))
                .thenReturn(Collections.emptyList());
        when(jdbcTemplate.update(any(org.springframework.jdbc.core.PreparedStatementCreator.class),
                any(KeyHolder.class))).thenAnswer(invocation -> {
            KeyHolder keyHolder = invocation.getArgument(1);
            Map<String, Object> key = new LinkedHashMap<>();
            key.put("GENERATED_KEY", 41L);
            keyHolder.getKeyList().add(key);
            return 1;
        });
        GmOperationAuditService service = service(jdbcTemplate);
        Map<String, Object> request = Collections.<String, Object>singletonMap("delta", 100);

        GmOperationAuditService.AuditRecord record = service.begin(context("req-1"), request);

        assertEquals(41L, record.getId());
        assertEquals("req-1", record.getRequestId());
        assertEquals(GmOperationAuditService.STATUS_PENDING, record.getStatus());
        assertFalse(record.isReplay());
        verify(jdbcTemplate).update(any(org.springframework.jdbc.core.PreparedStatementCreator.class),
                any(KeyHolder.class));
    }

    @Test
    public void returnsOriginalResultForDuplicateRequestIdWithoutReexecutingInsert() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("FROM dnf_service.gm_operation_audit"), eq("req-replay")))
                .thenReturn(Collections.singletonList(auditRow()));
        GmOperationAuditService service = service(jdbcTemplate);

        GmOperationAuditService.AuditRecord record = service.begin(
                context("req-replay"), Collections.singletonMap("delta", 100));

        assertEquals(9L, record.getId());
        assertEquals(GmOperationAuditService.STATUS_SUCCESS, record.getStatus());
        assertEquals("{\"balance\":200}", record.getResponseJson());
        assertTrue(record.isReplay());
        verify(jdbcTemplate, never()).update(
                any(org.springframework.jdbc.core.PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test
    public void rejectsDuplicateRequestIdWhenOperationIdentityOrRequestBodyDiffers() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("FROM dnf_service.gm_operation_audit"), eq("req-replay")))
                .thenReturn(Collections.singletonList(auditRow()));
        GmOperationAuditService service = service(jdbcTemplate);

        expectReplayConflict(service,
                context("req-replay", "contracts", "change", 18000013L, 23),
                Collections.singletonMap("delta", 100));
        expectReplayConflict(service,
                context("req-replay", "currency", "set", 18000013L, 23),
                Collections.singletonMap("delta", 100));
        expectReplayConflict(service,
                context("req-replay", "currency", "change", 18000014L, 23),
                Collections.singletonMap("delta", 100));
        expectReplayConflict(service,
                context("req-replay", "currency", "change", 18000013L, 24),
                Collections.singletonMap("delta", 100));
        expectReplayConflict(service,
                context("req-replay", "currency", "change", 18000013L, 23),
                Collections.singletonMap("delta", 101));

        verify(jdbcTemplate, never()).update(
                any(org.springframework.jdbc.core.PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test
    public void initializesAuditSchemaAtApplicationStartupFromMigrationResource() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        GmOperationAuditService service = service(jdbcTemplate);

        service.initializeSchema();

        Method initializer = GmOperationAuditService.class.getMethod("initializeSchema");
        assertNotNull(initializer.getAnnotation(PostConstruct.class));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(statement).execute(sql.capture());
        assertTrue(sql.getValue().contains("CREATE TABLE IF NOT EXISTS dnf_service.gm_operation_audit"));
        assertTrue(sql.getValue().contains("CHARACTER SET ascii COLLATE ascii_bin"));
        assertTrue(sql.getValue().contains("UNIQUE KEY uk_gm_operation_audit_request"));
    }

    @Test
    public void failureAuditUsesIndependentTransaction() throws Exception {
        Method method = GmOperationAuditService.class.getMethod(
                "fail", String.class, Object.class, Object.class, Object.class, String.class, String.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }

    @Test
    public void writesTerminalFailureStateAndKeepsOriginalResultReadable() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        Map<String, Object> failed = auditRow();
        failed.put("status", GmOperationAuditService.STATUS_FAILED);
        failed.put("message", "runtime timeout");
        when(jdbcTemplate.queryForList(contains("FROM dnf_service.gm_operation_audit"), eq("req-replay")))
                .thenReturn(Collections.singletonList(failed));
        GmOperationAuditService service = service(jdbcTemplate);

        GmOperationAuditService.AuditRecord record = service.fail(
                "req-replay",
                Collections.singletonMap("balance", 100),
                Collections.singletonMap("balance", 100),
                Collections.singletonMap("accepted", false),
                "UNCERTAIN",
                "runtime timeout");

        assertEquals(GmOperationAuditService.STATUS_FAILED, record.getStatus());
        assertEquals("runtime timeout", record.getMessage());
        verify(jdbcTemplate).update(
                contains("status='FAILED'"),
                eq("{\"balance\":100}"),
                eq("{\"balance\":100}"),
                eq("{\"accepted\":false}"),
                eq("UNCERTAIN"),
                eq("runtime timeout"),
                eq("req-replay"));
    }

    private GmOperationAuditService service(JdbcTemplate jdbcTemplate) {
        GmOperationAuditService service = new GmOperationAuditService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        return service;
    }

    private GmOperationContext context(String requestId) {
        return context(requestId, "currency", "change", 18000013L, 23);
    }

    private GmOperationContext context(String requestId, String module, String action,
                                       Long accountId, Integer characNo) {
        return new GmOperationContext(
                requestId, 100L, "root-gm", "10.0.0.7",
                module, action, accountId, characNo);
    }

    private void expectReplayConflict(GmOperationAuditService service,
                                      GmOperationContext context,
                                      Object requestPayload) {
        try {
            service.begin(context, requestPayload);
            fail("Expected duplicate request id conflict");
        } catch (ValidationException expected) {
            assertTrue(expected.getMessage().contains("请求号冲突"));
        }
    }

    private Map<String, Object> auditRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 9L);
        row.put("requestId", "req-replay");
        row.put("operatorUid", 100L);
        row.put("operatorName", "root-gm");
        row.put("clientIp", "10.0.0.7");
        row.put("module", "currency");
        row.put("action", "change");
        row.put("targetAccountId", 18000013L);
        row.put("targetCharacNo", 23);
        row.put("requestJson", "{\"delta\":100}");
        row.put("beforeJson", "{\"balance\":100}");
        row.put("afterJson", "{\"balance\":200}");
        row.put("responseJson", "{\"balance\":200}");
        row.put("status", GmOperationAuditService.STATUS_SUCCESS);
        row.put("syncStatus", "SYNCED");
        row.put("message", "done");
        row.put("createdAt", "2026-07-31 10:00:00");
        row.put("completedAt", "2026-07-31 10:00:01");
        return row;
    }
}
