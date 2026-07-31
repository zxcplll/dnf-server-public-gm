package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.exception.ValidationException;
import com.aiyi.game.dnfserver.entity.common.Item;
import com.aiyi.game.dnfserver.pvf.PvfManager;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.InflaterInputStream;

@Service
public class GmInventoryService {

    static final int RECORD_SIZE = 61;
    private static final int MAX_UNCOMPRESSED_BYTES = 4 * 1024 * 1024;

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private PvfManager pvfManager;
    @Resource
    private GameRuntimeClient gameRuntimeClient;

    public Map<String, Object> snapshot(int characNo) {
        if (characNo <= 0) {
            throw new ValidationException("角色编号无效");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT i.charac_no AS characNo,c.m_id AS accountId,i.money,i.coin,i.pay_coin AS payCoin," +
                        "i.event_coin AS eventCoin,i.avatar_coin AS avatarCoin,i.inventory," +
                        "i.equipslot,i.creature,i.inventory_capacity AS inventoryCapacity " +
                        "FROM taiwan_cain_2nd.inventory i " +
                        "JOIN taiwan_cain.charac_info c ON c.charac_no=i.charac_no " +
                        "WHERE i.charac_no=?",
                characNo);
        if (rows.isEmpty()) {
            throw new ValidationException("角色背包不存在");
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("characNo", characNo);
        long accountId = longValue(row.get("accountId"));
        result.put("accountId", accountId);
        result.put("readOnly", true);
        result.put("inventoryCapacity", longValue(row.get("inventoryCapacity")));
        Map<String, Object> wallet = new LinkedHashMap<>();
        wallet.put("gold", longValue(row.get("money")));
        wallet.put("coin", longValue(row.get("coin")));
        wallet.put("payCoin", longValue(row.get("payCoin")));
        wallet.put("eventCoin", longValue(row.get("eventCoin")));
        wallet.put("avatarCoin", longValue(row.get("avatarCoin")));
        result.put("wallet", wallet);
        List<Map<String, Object>> inventory = enrich(parseBlob("inventory", bytes(row.get("inventory"))));
        List<Map<String, Object>> equipment = enrich(parseBlob("equipslot", bytes(row.get("equipslot"))));
        List<Map<String, Object>> creatureSlots = enrich(parseBlob("creature", bytes(row.get("creature"))));
        result.put("inventory", inventory);
        result.put("equipment", equipment);
        result.put("creatureSlots", creatureSlots);

        Map<String, Object> sources = new LinkedHashMap<>();
        result.put("avatarItems", queryNormalizedItems(
                "SELECT ui_id AS uniqueId,slot,it_id AS itemId,expire_date AS expireDate," +
                        "ability_no AS abilityNo,stat FROM taiwan_cain_2nd.user_items " +
                        "WHERE charac_no=? ORDER BY slot,ui_id",
                characNo,
                "user_items",
                sources));
        result.put("creatureItems", queryNormalizedItems(
                "SELECT ui_id AS uniqueId,slot,it_id AS itemId,name,stomach,exp,endurance," +
                        "creature_type AS creatureType,stat FROM taiwan_cain_2nd.creature_items " +
                        "WHERE charac_no=? ORDER BY slot,ui_id",
                characNo,
                "creature_items",
                sources));
        sources.put("inventory_blob", sourceStatus("AVAILABLE", null));
        Map<String, Object> runtimeValidation = runtimeValidation(accountId, characNo,
                equipment, inventory, creatureSlots);
        result.put("runtimeValidation", runtimeValidation);
        sources.put("runtime", sourceStatus(
                stringValue(runtimeValidation.get("status"), "DEGRADED"),
                stringValue(runtimeValidation.get("reason"), null)));
        result.put("sources", sources);
        return result;
    }

    private Map<String, Object> runtimeValidation(long accountId, int characNo,
                                                   List<Map<String, Object>> equipment,
                                                   List<Map<String, Object>> inventory,
                                                   List<Map<String, Object>> creatureSlots) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accountId", accountId);
        if (gameRuntimeClient == null) {
            return degradedRuntimeValidation(result, "运行时客户端未配置");
        }
        if (accountId <= 0 || accountId > Integer.MAX_VALUE) {
            return degradedRuntimeValidation(result, "账号编号超出运行时协议范围");
        }
        try {
            GameRuntimeClient.InventorySnapshot runtime = gameRuntimeClient.inventorySnapshot(
                    (int) accountId, characNo);
            result.put("scannedSlots", runtime.getScannedSlots());
            result.put("totalOccupied", runtime.getTotalOccupied());
            result.put("truncated", runtime.isTruncated());
            result.put("accountCargoAvailable", runtime.isAccountCargoAvailable());
            result.put("runtimeErrors", runtimeErrors(runtime.getErrors()));

            Map<String, Integer> databaseSlots = new LinkedHashMap<>();
            indexDatabaseSlots(databaseSlots, 0, equipment);
            indexDatabaseSlots(databaseSlots, 1, inventory);
            indexDatabaseSlots(databaseSlots, 3, creatureSlots);
            Map<String, Integer> runtimeSlots = new LinkedHashMap<>();
            for (GameRuntimeClient.InventorySlot slot : runtime.getSlots()) {
                if (isComparableSpace(slot.getSpace())) {
                    runtimeSlots.put(slotKey(slot.getSpace(), slot.getSlot()), slot.getItemId());
                }
            }

            List<Map<String, Object>> mismatches = new ArrayList<>();
            int matchedSlots = 0;
            int comparableSlots = 0;
            for (Map.Entry<String, Integer> runtimeSlot : runtimeSlots.entrySet()) {
                comparableSlots++;
                Integer databaseItemId = databaseSlots.get(runtimeSlot.getKey());
                if (databaseItemId != null && databaseItemId.equals(runtimeSlot.getValue())) {
                    matchedSlots++;
                } else {
                    addMismatch(mismatches, runtimeSlot.getKey(), databaseItemId, runtimeSlot.getValue());
                }
            }
            if (!runtime.isTruncated()) {
                for (Map.Entry<String, Integer> databaseSlot : databaseSlots.entrySet()) {
                    if (!runtimeSlots.containsKey(databaseSlot.getKey()) &&
                            !runtimeReadFailed(runtime.getErrors(), databaseSlot.getKey())) {
                        comparableSlots++;
                        addMismatch(mismatches, databaseSlot.getKey(), databaseSlot.getValue(), null);
                    }
                }
            }
            boolean complete = !runtime.isTruncated() && runtime.getErrors().isEmpty();
            result.put("comparableSlots", comparableSlots);
            result.put("matchedSlots", matchedSlots);
            result.put("mismatches", mismatches);
            result.put("mismatchCount", mismatches.size());
            result.put("consistent", complete ? mismatches.isEmpty() : null);
            if (complete) {
                result.put("status", "AVAILABLE");
                result.put("reason", null);
            } else {
                result.put("status", "DEGRADED");
                result.put("reason", runtime.isTruncated()
                        ? "运行时背包结果已截断"
                        : "运行时背包存在槽位读取失败");
            }
            return result;
        } catch (RuntimeException exception) {
            return degradedRuntimeValidation(result, compact(exception.getMessage()));
        }
    }

    private Map<String, Object> degradedRuntimeValidation(Map<String, Object> result, String reason) {
        result.put("status", "DEGRADED");
        result.put("reason", reason);
        result.put("consistent", null);
        result.put("comparableSlots", 0);
        result.put("matchedSlots", 0);
        result.put("mismatches", new ArrayList<Map<String, Object>>());
        result.put("mismatchCount", 0);
        return result;
    }

    private void indexDatabaseSlots(Map<String, Integer> result, int space,
                                    List<Map<String, Object>> slots) {
        for (Map<String, Object> slot : slots) {
            if (!Boolean.TRUE.equals(slot.get("available")) || !Boolean.TRUE.equals(slot.get("occupied"))) {
                continue;
            }
            int index = intValue(slot.get("slot"));
            int itemId = intValue(slot.get("itemId"));
            if (index >= 0 && itemId > 0) {
                result.put(slotKey(space, index), itemId);
            }
        }
    }

    private boolean runtimeReadFailed(List<GameRuntimeClient.InventoryReadError> errors, String key) {
        for (GameRuntimeClient.InventoryReadError error : errors) {
            if (slotKey(error.getSpace(), error.getSlot()).equals(key)) {
                return true;
            }
        }
        return false;
    }

    private List<Map<String, Object>> runtimeErrors(List<GameRuntimeClient.InventoryReadError> errors) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (GameRuntimeClient.InventoryReadError error : errors) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("space", error.getSpace());
            item.put("slot", error.getSlot());
            item.put("code", error.getCode());
            result.add(item);
        }
        return result;
    }

    private void addMismatch(List<Map<String, Object>> mismatches, String key,
                             Integer databaseItemId, Integer runtimeItemId) {
        if (mismatches.size() >= 100) {
            return;
        }
        String[] parts = key.split(":", 2);
        Map<String, Object> mismatch = new LinkedHashMap<>();
        mismatch.put("space", Integer.parseInt(parts[0]));
        mismatch.put("slot", Integer.parseInt(parts[1]));
        mismatch.put("databaseItemId", databaseItemId);
        mismatch.put("runtimeItemId", runtimeItemId);
        mismatches.add(mismatch);
    }

    private boolean isComparableSpace(int space) {
        return space == 0 || space == 1 || space == 3;
    }

    private String slotKey(int space, int slot) {
        return space + ":" + slot;
    }

    List<Map<String, Object>> parseBlob(String group, byte[] blob) {
        if (blob == null || blob.length < 5) {
            return singletonError(group, "背包数据为空或长度不足");
        }
        int expectedLength = ByteBuffer.wrap(blob, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (expectedLength < 0 || expectedLength > MAX_UNCOMPRESSED_BYTES) {
            return singletonError(group, "背包解压长度超出限制");
        }
        byte[] uncompressed;
        try {
            uncompressed = inflate(Arrays.copyOfRange(blob, 4, blob.length), expectedLength);
        } catch (IOException e) {
            return singletonError(group, "背包数据解压失败");
        }
        if (uncompressed.length != expectedLength) {
            return singletonError(group, "背包解压长度不一致");
        }
        List<Map<String, Object>> slots = new ArrayList<>();
        int fullRecords = uncompressed.length / RECORD_SIZE;
        for (int slot = 0; slot < fullRecords; slot++) {
            slots.add(parseRecord(group, slot, uncompressed, slot * RECORD_SIZE));
        }
        if (uncompressed.length % RECORD_SIZE != 0) {
            Map<String, Object> invalid = slotBase(group, fullRecords);
            invalid.put("available", false);
            invalid.put("occupied", false);
            invalid.put("error", "槽位尾部长度不是 61 字节");
            slots.add(invalid);
        }
        return slots;
    }

    private Map<String, Object> parseRecord(String group, int slot, byte[] records, int offset) {
        Map<String, Object> result = slotBase(group, slot);
        try {
            int kind = records[offset + 1] & 0xff;
            int itemId = ByteBuffer.wrap(records, offset + 2, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            long quantity = Integer.toUnsignedLong(
                    ByteBuffer.wrap(records, offset + 7, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
            result.put("kind", kind);
            result.put("itemId", itemId);
            result.put("quantity", quantity);
            result.put("upgrade", records[offset + 6] & 0xff);
            result.put("occupied", kind != 0 || itemId != 0);
            if (itemId < 0) {
                result.put("available", false);
                result.put("error", "物品 ID 无效");
            } else {
                result.put("available", true);
            }
        } catch (RuntimeException e) {
            result.put("available", false);
            result.put("occupied", false);
            result.put("error", "槽位记录解析失败");
        }
        return result;
    }

    private List<Map<String, Object>> enrich(List<Map<String, Object>> slots) {
        for (Map<String, Object> slot : slots) {
            if (!Boolean.TRUE.equals(slot.get("available")) || !Boolean.TRUE.equals(slot.get("occupied"))) {
                continue;
            }
            int itemId = intValue(slot.get("itemId"));
            Item item = pvfManager == null ? null : pvfManager.findItem(itemId);
            if (item == null) {
                slot.put("resolved", false);
                continue;
            }
            slot.put("resolved", true);
            slot.put("name", item.getName());
            slot.put("type", item.getType() == null ? "other" : item.getType().name());
            slot.put("rarity", item.getRarity());
            slot.put("minimumLevel", item.getMinimumLevel());
            slot.put("attachType", item.getAttachTypeStr());
            slot.put("icon", item.getIcon());
        }
        return slots;
    }

    private List<Map<String, Object>> queryNormalizedItems(String sql, int characNo, String source,
                                                            Map<String, Object> sources) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, characNo);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>(row);
                int itemId = intValue(row.get("itemId"));
                Item template = pvfManager == null ? null : pvfManager.findItem(itemId);
                if (template != null) {
                    item.put("itemName", template.getName());
                    item.put("rarity", template.getRarity());
                    item.put("minimumLevel", template.getMinimumLevel());
                    item.put("icon", template.getIcon());
                }
                result.add(item);
            }
            sources.put(source, sourceStatus("AVAILABLE", null));
            return result;
        } catch (DataAccessException e) {
            sources.put(source, sourceStatus("UNAVAILABLE", "数据源查询失败"));
            return new ArrayList<>();
        }
    }

    private byte[] inflate(byte[] compressed, int expectedLength) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(32, expectedLength));
        try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (output.size() + read > MAX_UNCOMPRESSED_BYTES) {
                    throw new IOException("Uncompressed data exceeds limit");
                }
                output.write(buffer, 0, read);
            }
        }
        return output.toByteArray();
    }

    private List<Map<String, Object>> singletonError(String group, String error) {
        Map<String, Object> slot = slotBase(group, -1);
        slot.put("available", false);
        slot.put("occupied", false);
        slot.put("error", error);
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(slot);
        return result;
    }

    private Map<String, Object> slotBase(String group, int slot) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("group", group);
        result.put("slot", slot);
        return result;
    }

    private Map<String, Object> sourceStatus(String status, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("reason", reason);
        return result;
    }

    private byte[] bytes(Object value) {
        return value instanceof byte[] ? (byte[]) value : null;
    }

    private int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private String compact(String value) {
        String text = value == null ? "运行时背包不可用" : value.trim();
        if (text.isEmpty()) {
            return "运行时背包不可用";
        }
        return text.length() > 120 ? text.substring(0, 120) : text;
    }
}
