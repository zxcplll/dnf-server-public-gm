package com.aiyi.game.dnfserver.service.impl;

import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GmInventoryServiceTest {

    @Test
    public void parsesFixedSizeSlotsAndIsolatesInvalidRecords() throws Exception {
        byte[] records = new byte[61 * 3];
        writeRecord(records, 0, 1, 1001, 3);
        writeRecord(records, 1, 2, 2002, 1);
        writeRecord(records, 2, 3, -1, 5);
        GmInventoryService service = new GmInventoryService();

        List<Map<String, Object>> slots = service.parseBlob("inventory", compress(records));

        assertEquals(3, slots.size());
        assertEquals(1001, slots.get(0).get("itemId"));
        assertEquals(3L, slots.get(0).get("quantity"));
        assertTrue((Boolean) slots.get(0).get("available"));
        assertEquals(2002, slots.get(1).get("itemId"));
        assertFalse((Boolean) slots.get(2).get("available"));
        assertTrue(String.valueOf(slots.get(2).get("error")).contains("物品 ID"));
    }

    @Test
    public void reportsWholeBlobDamageWithoutGuessingSlots() {
        GmInventoryService service = new GmInventoryService();

        List<Map<String, Object>> slots = service.parseBlob(
                "equipslot", new byte[]{10, 0, 0, 0, 1, 2, 3});

        assertEquals(1, slots.size());
        assertFalse((Boolean) slots.get(0).get("available"));
        assertTrue(String.valueOf(slots.get(0).get("error")).contains("解压"));
    }

    @Test
    public void crossChecksDatabaseSlotsAgainstRuntimeWhenAvailable() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GameRuntimeClient runtimeClient = mock(GameRuntimeClient.class);
        GmInventoryService service = snapshotService(jdbcTemplate, runtimeClient);
        stubInventory(jdbcTemplate);
        GameRuntimeClient.InventorySlot runtimeSlot =
                new GameRuntimeClient.InventorySlot(1, 0, 1001, 0L);
        when(runtimeClient.inventorySnapshot(42, 23)).thenReturn(
                new GameRuntimeClient.InventorySnapshot(
                        42,
                        23,
                        1,
                        1,
                        false,
                        false,
                        Collections.singletonList(runtimeSlot),
                        Collections.<GameRuntimeClient.InventoryReadError>emptyList()));

        Map<String, Object> result = service.snapshot(23);

        Map<?, ?> validation = (Map<?, ?>) result.get("runtimeValidation");
        assertNotNull(validation);
        assertEquals("AVAILABLE", validation.get("status"));
        assertEquals(1, validation.get("matchedSlots"));
        assertEquals(Boolean.TRUE, validation.get("consistent"));
        Map<?, ?> runtimeSource = (Map<?, ?>) ((Map<?, ?>) result.get("sources")).get("runtime");
        assertEquals("AVAILABLE", runtimeSource.get("status"));
    }

    @Test
    public void keepsDatabaseSnapshotAndMarksRuntimeDegradedWhenBridgeFails() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GameRuntimeClient runtimeClient = mock(GameRuntimeClient.class);
        GmInventoryService service = snapshotService(jdbcTemplate, runtimeClient);
        stubInventory(jdbcTemplate);
        when(runtimeClient.inventorySnapshot(42, 23))
                .thenThrow(new IllegalStateException("bridge offline"));

        Map<String, Object> result = service.snapshot(23);

        assertEquals(1, ((List<?>) result.get("inventory")).size());
        Map<?, ?> validation = (Map<?, ?>) result.get("runtimeValidation");
        assertEquals("DEGRADED", validation.get("status"));
        assertTrue(String.valueOf(validation.get("reason")).contains("bridge offline"));
        Map<?, ?> runtimeSource = (Map<?, ?>) ((Map<?, ?>) result.get("sources")).get("runtime");
        assertEquals("DEGRADED", runtimeSource.get("status"));
    }

    private GmInventoryService snapshotService(JdbcTemplate jdbcTemplate,
                                                GameRuntimeClient runtimeClient) {
        GmInventoryService service = new GmInventoryService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "gameRuntimeClient", runtimeClient);
        return service;
    }

    private void stubInventory(JdbcTemplate jdbcTemplate) throws Exception {
        byte[] inventoryRecords = new byte[GmInventoryService.RECORD_SIZE];
        writeRecord(inventoryRecords, 0, 1, 1001, 3);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("accountId", 42L);
        row.put("money", 1200L);
        row.put("coin", 3L);
        row.put("payCoin", 4L);
        row.put("eventCoin", 5L);
        row.put("avatarCoin", 6L);
        row.put("inventoryCapacity", 112L);
        row.put("inventory", compress(inventoryRecords));
        row.put("equipslot", compress(new byte[0]));
        row.put("creature", compress(new byte[0]));
        when(jdbcTemplate.queryForList(
                contains("FROM taiwan_cain_2nd.inventory i"),
                eq(23)))
                .thenReturn(Collections.singletonList(row));
        when(jdbcTemplate.queryForList(
                contains("FROM taiwan_cain_2nd.user_items"),
                eq(23)))
                .thenReturn(Collections.<Map<String, Object>>emptyList());
        when(jdbcTemplate.queryForList(
                contains("FROM taiwan_cain_2nd.creature_items"),
                eq(23)))
                .thenReturn(Collections.<Map<String, Object>>emptyList());
    }

    private void writeRecord(byte[] records, int slot, int kind, int itemId, int quantity) {
        int offset = slot * 61;
        records[offset + 1] = (byte) kind;
        ByteBuffer.wrap(records, offset + 2, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(itemId);
        ByteBuffer.wrap(records, offset + 7, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(quantity);
    }

    private byte[] compress(byte[] records) throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(compressed)) {
            out.write(records);
        }
        ByteBuffer result = ByteBuffer.allocate(4 + compressed.size()).order(ByteOrder.LITTLE_ENDIAN);
        result.putInt(records.length);
        result.put(compressed.toByteArray());
        return result.array();
    }
}
