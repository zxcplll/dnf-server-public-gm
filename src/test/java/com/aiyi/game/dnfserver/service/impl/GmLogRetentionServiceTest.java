package com.aiyi.game.dnfserver.service.impl;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GmLogRetentionServiceTest {

    @Test
    public void deletesOnlyExpiredBackendLogRowsWithSevenDayRetention() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GmLogRetentionService service = new GmLogRetentionService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);

        service.cleanupExpiredLogs();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(5)).update(sql.capture());
        List<String> statements = sql.getAllValues();
        assertEquals("DELETE FROM dnf_service.gm_online_reward_log " +
                "WHERE awarded_at < DATE_SUB(NOW(), INTERVAL 7 DAY) LIMIT 5000", statements.get(0));
        assertEquals("DELETE FROM dnf_service.gm_cdk_redemption " +
                "WHERE redeemed_at < DATE_SUB(NOW(), INTERVAL 7 DAY) LIMIT 5000", statements.get(1));
        assertEquals("DELETE FROM dnf_service.gm_backup_entry " +
                "WHERE status='FAILED' AND created_at < DATE_SUB(NOW(), INTERVAL 7 DAY) LIMIT 5000", statements.get(2));
        assertEquals("DELETE FROM d_guild.guild_grade_log " +
                "WHERE occ_time < DATE_SUB(NOW(), INTERVAL 7 DAY) LIMIT 5000", statements.get(3));
        assertEquals("DELETE FROM dnf_service.gm_operation_audit " +
                "WHERE created_at < DATE_SUB(NOW(), INTERVAL 7 DAY) LIMIT 5000", statements.get(4));
    }

    @Test
    public void continuesCleaningWhenAnOptionalLogTableDoesNotExist() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GmLogRetentionService service = new GmLogRetentionService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        when(jdbcTemplate.update(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("gm_cdk_redemption")) {
                throw new DataAccessResourceFailureException("table missing");
            }
            return 0;
        });

        service.cleanupExpiredLogs();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(5)).update(sql.capture());
        assertTrue(sql.getAllValues().get(2).contains("gm_backup_entry"));
        assertTrue(sql.getAllValues().get(3).contains("d_guild.guild_grade_log"));
        assertTrue(sql.getAllValues().get(4).contains("gm_operation_audit"));
    }

    @Test
    public void deletesLargeLogSetsInBoundedBatches() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GmLogRetentionService service = new GmLogRetentionService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        when(jdbcTemplate.update(anyString())).thenReturn(5000, 0);

        ReflectionTestUtils.invokeMethod(service, "cleanupTable",
                "gm_online_reward_log", "awarded_at", null);

        verify(jdbcTemplate, times(2)).update(anyString());
    }
}
