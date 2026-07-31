package com.aiyi.game.dnfserver.service.impl;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GmRuntimeHealthServiceTest {

    @Test
    public void oneFailedProbeDegradesOverallWithoutDroppingOtherResults() {
        GmRuntimeHealthService service = new GmRuntimeHealthService() {
            @Override
            Map<String, Object> probeGameProcess() {
                return healthy("game", 12L);
            }

            @Override
            Map<String, Object> probeBridge() {
                throw new IllegalStateException("bridge timeout");
            }

            @Override
            List<Map<String, Object>> probePorts() {
                return Arrays.asList(healthy("27043", 1L), healthy("9001", 1L));
            }

            @Override
            List<Map<String, Object>> probeServices() {
                return Arrays.asList(healthy("java", 1L));
            }

            @Override
            List<Map<String, Object>> probeLogs() {
                return Arrays.asList(healthy("web", 1L));
            }
        };

        Map<String, Object> health = service.health();

        assertEquals("DEGRADED", health.get("status"));
        assertEquals("HEALTHY", ((Map<?, ?>) health.get("gameProcess")).get("status"));
        assertEquals("DEGRADED", ((Map<?, ?>) health.get("bridge")).get("status"));
        assertEquals(2, ((List<?>) health.get("ports")).size());
        assertTrue(((Map<?, ?>) health.get("bridge")).get("reason").toString().contains("bridge timeout"));
    }

    private static Map<String, Object> healthy(String name, long latency) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("status", "HEALTHY");
        result.put("latencyMs", latency);
        return result;
    }
}
