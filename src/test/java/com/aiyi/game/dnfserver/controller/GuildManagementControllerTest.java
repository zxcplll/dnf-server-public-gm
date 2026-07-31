package com.aiyi.game.dnfserver.controller;

import com.aiyi.core.exception.ValidationException;
import com.aiyi.game.dnfserver.entity.gm.GmOperationContext;
import com.aiyi.game.dnfserver.service.impl.GmAuthorizationService;
import com.aiyi.game.dnfserver.service.impl.GmOperationAuditService;
import com.aiyi.game.dnfserver.service.impl.GuildManagementService;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GuildManagementControllerTest {

    @Test
    public void guildUpdateRequiresHighestAdministratorBeforeServiceCall() {
        GuildManagementService guildService = mock(GuildManagementService.class);
        GmAuthorizationService authorizationService = mock(GmAuthorizationService.class);
        GmOperationAuditService auditService = mock(GmOperationAuditService.class);
        when(authorizationService.authorize(any(MockHttpServletRequest.class), any(String.class),
                any(String.class), isNull(Long.class), isNull(Integer.class)))
                .thenThrow(new ValidationException("只有最高管理员才能执行此操作"));
        GuildManagementController controller = controller(guildService, authorizationService, auditService);
        MockHttpServletRequest request = new MockHttpServletRequest();

        try {
            controller.updateGuild(7, new LinkedHashMap<String, Object>(), request);
            fail("Expected guild update authorization to be enforced by the controller");
        } catch (ValidationException expected) {
            assertTrue(expected.getMessage().contains("最高管理员"));
        }

        verify(guildService, never()).updateGuild(anyInt(), any(Map.class));
        verify(auditService, never()).begin(any(GmOperationContext.class), any(Object.class));
    }

    @Test
    public void memberWriteAuditsRequestWithCharacterTargetAndReplaysNoServiceCall() throws Exception {
        GuildManagementService guildService = mock(GuildManagementService.class);
        GmAuthorizationService authorizationService = mock(GmAuthorizationService.class);
        GmOperationAuditService auditService = mock(GmOperationAuditService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        GmOperationContext context = new GmOperationContext(
                "guild-request-1", 100L, "root", "10.0.0.7",
                "GUILD", "ADD_MEMBER", null, 23);
        when(authorizationService.authorize(same(request), eq("GUILD"), eq("ADD_MEMBER"),
                isNull(Long.class), eq(23))).thenReturn(context);
        when(auditService.begin(eq(context), any(Map.class))).thenReturn(replayAudit(context));
        GuildManagementController controller = controller(guildService, authorizationService, auditService);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("characNo", 23);

        Map<String, Object> result = controller.addMember(7, payload, request);

        assertEquals(23, result.get("characNo"));
        verify(guildService, never()).addMember(anyInt(), any(Map.class));
        verify(auditService).begin(eq(context), eq(expectedPayload(7, 23, payload)));
    }

    private GuildManagementController controller(GuildManagementService guildService,
                                                  GmAuthorizationService authorizationService,
                                                  GmOperationAuditService auditService) {
        GuildManagementController controller = new GuildManagementController();
        ReflectionTestUtils.setField(controller, "guildManagementService", guildService);
        ReflectionTestUtils.setField(controller, "authorizationService", authorizationService);
        ReflectionTestUtils.setField(controller, "auditService", auditService);
        return controller;
    }

    private Map<String, Object> expectedPayload(int guildId, int characNo, Map<String, Object> payload) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("guildId", guildId);
        result.put("characNo", characNo);
        result.putAll(payload);
        return result;
    }

    private GmOperationAuditService.AuditRecord replayAudit(GmOperationContext context) throws Exception {
        Constructor<?> constructor = null;
        for (Constructor<?> candidate : GmOperationAuditService.AuditRecord.class
                .getDeclaredConstructors()) {
            if (candidate.getParameterTypes().length == 19) {
                constructor = candidate;
                break;
            }
        }
        if (constructor == null) {
            throw new IllegalStateException("AuditRecord constructor shape changed");
        }
        constructor.setAccessible(true);
        return (GmOperationAuditService.AuditRecord) constructor.newInstance(
                9L,
                context.getRequestId(),
                context.getOperatorUid(),
                context.getOperatorName(),
                context.getClientIp(),
                context.getModule(),
                context.getAction(),
                context.getTargetAccountId(),
                context.getTargetCharacNo(),
                "{}",
                null,
                null,
                "{\"characNo\":23}",
                GmOperationAuditService.STATUS_SUCCESS,
                "DB_COMMITTED",
                "done",
                null,
                null,
                true);
    }
}
