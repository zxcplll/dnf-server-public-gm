package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.exception.ValidationException;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GmContractServiceTest {

    @Test
    public void onlyAllowsKnownContractTypesAndForwardTimeWindows() {
        GmContractService service = new GmContractService();
        assertTrue(service.isSupportedType(9));
        assertTrue(service.isSupportedType(22));
        expectRejected(service, 99, LocalDateTime.now(), LocalDateTime.now().plusDays(1), "不支持");
        expectRejected(service, 9, LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(1), "结束");
    }

    @Test
    public void auditPayloadKeepsCompleteBusinessRequestButDropsCapabilityToken() {
        GmContractService service = new GmContractService();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation", "extend");
        payload.put("preType", 9);
        payload.put("eventId", 77L);
        payload.put("durationDays", 5);
        payload.put("days", 5);
        payload.put("serverId", 3);
        payload.put("serviceStart", "2026-07-31 10:00:00");
        payload.put("serviceEnd", "2026-08-05 10:00:00");
        payload.put("targetToken", "secret-capability");

        Map<String, Object> auditPayload = service.auditPayload(payload);

        assertEquals(8, auditPayload.size());
        assertEquals(payload.get("serviceStart"), auditPayload.get("serviceStart"));
        assertEquals(payload.get("serviceEnd"), auditPayload.get("serviceEnd"));
        assertEquals(payload.get("serverId"), auditPayload.get("serverId"));
        assertEquals(payload.get("days"), auditPayload.get("days"));
        assertFalse(auditPayload.containsKey("targetToken"));
    }

    private void expectRejected(GmContractService service, int type, LocalDateTime start,
                                LocalDateTime end, String expected) {
        try {
            service.validateContractWindow(type, start, end);
            fail("Expected contract validation to fail");
        } catch (ValidationException exception) {
            assertTrue(exception.getMessage().contains(expected));
        }
    }
}
