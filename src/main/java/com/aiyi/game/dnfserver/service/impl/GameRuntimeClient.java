package com.aiyi.game.dnfserver.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface GameRuntimeClient {

    RuntimePing ping();

    OnlineSnapshot onlineSnapshot(int offset, int limit);

    default OnlineSnapshot onlineSnapshot() {
        return onlineSnapshot(0, 100);
    }

    PlayerSnapshot inspectPlayer(int accountId, int characNo);

    CurrencySnapshot currencySnapshot(int accountId, int characNo);

    GoldChange changeGold(String requestId, int accountId, int characNo, long delta);

    InventorySnapshot inventorySnapshot(int accountId, int characNo);

    ContractSnapshot inspectContracts(int accountId, int characNo);

    OnlineCharacter findOnlineCharacter(int accountId);

    GoldChange addGold(String requestId, int accountId, int characNo, int amount);

    final class RuntimePing {
        private final int protocolVersion;
        private final List<String> capabilities;

        public RuntimePing(int protocolVersion, List<String> capabilities) {
            this.protocolVersion = protocolVersion;
            this.capabilities = immutableCopy(capabilities);
        }

        public int getProtocolVersion() {
            return protocolVersion;
        }

        public List<String> getCapabilities() {
            return capabilities;
        }
    }

    final class OnlineSnapshot {
        private final int offset;
        private final int limit;
        private final int total;
        private final boolean truncated;
        private final List<PlayerSnapshot> players;

        public OnlineSnapshot(int offset, int limit, int total, boolean truncated,
                              List<PlayerSnapshot> players) {
            this.offset = offset;
            this.limit = limit;
            this.total = total;
            this.truncated = truncated;
            this.players = immutableCopy(players);
        }

        public int getOffset() {
            return offset;
        }

        public int getLimit() {
            return limit;
        }

        public int getTotal() {
            return total;
        }

        public boolean isTruncated() {
            return truncated;
        }

        public List<PlayerSnapshot> getPlayers() {
            return players;
        }
    }

    final class PlayerSnapshot {
        private final int accountId;
        private final int characNo;
        private final int state;
        private final Integer level;
        private final Integer job;
        private final Integer growType;
        private final Long gold;
        private final Integer fatigue;
        private final Integer maxFatigue;
        private final Integer village;
        private final Integer area;
        private final Integer posX;
        private final Integer posY;
        private final boolean inParty;
        private final Integer guildId;
        private final boolean inTrade;
        private final List<String> unavailable;

        public PlayerSnapshot(int accountId, int characNo, int state, Integer level, Integer job,
                              Integer growType, Long gold, Integer fatigue, Integer maxFatigue,
                              Integer village, Integer area, Integer posX, Integer posY,
                              boolean inParty, Integer guildId, boolean inTrade,
                              List<String> unavailable) {
            this.accountId = accountId;
            this.characNo = characNo;
            this.state = state;
            this.level = level;
            this.job = job;
            this.growType = growType;
            this.gold = gold;
            this.fatigue = fatigue;
            this.maxFatigue = maxFatigue;
            this.village = village;
            this.area = area;
            this.posX = posX;
            this.posY = posY;
            this.inParty = inParty;
            this.guildId = guildId;
            this.inTrade = inTrade;
            this.unavailable = immutableCopy(unavailable);
        }

        public int getAccountId() { return accountId; }
        public int getCharacNo() { return characNo; }
        public int getState() { return state; }
        public Integer getLevel() { return level; }
        public Integer getJob() { return job; }
        public Integer getGrowType() { return growType; }
        public Long getGold() { return gold; }
        public Integer getFatigue() { return fatigue; }
        public Integer getMaxFatigue() { return maxFatigue; }
        public Integer getVillage() { return village; }
        public Integer getArea() { return area; }
        public Integer getPosX() { return posX; }
        public Integer getPosY() { return posY; }
        public boolean isInParty() { return inParty; }
        public Integer getGuildId() { return guildId; }
        public boolean isInTrade() { return inTrade; }
        public List<String> getUnavailable() { return unavailable; }
    }

    final class CurrencySnapshot {
        private final int accountId;
        private final int characNo;
        private final Long gold;
        private final Long cera;
        private final Long ceraPoint;
        private final Long winPoint;
        private final List<String> unavailable;

        public CurrencySnapshot(int accountId, int characNo, Long gold, Long cera,
                                Long ceraPoint, Long winPoint, List<String> unavailable) {
            this.accountId = accountId;
            this.characNo = characNo;
            this.gold = gold;
            this.cera = cera;
            this.ceraPoint = ceraPoint;
            this.winPoint = winPoint;
            this.unavailable = immutableCopy(unavailable);
        }

        public int getAccountId() { return accountId; }
        public int getCharacNo() { return characNo; }
        public Long getGold() { return gold; }
        public Long getCera() { return cera; }
        public Long getCeraPoint() { return ceraPoint; }
        public Long getWinPoint() { return winPoint; }
        public List<String> getUnavailable() { return unavailable; }
    }

    final class GoldChange {
        private final int characNo;
        private final long before;
        private final long delta;
        private final long after;

        public GoldChange(int characNo, long before, long delta, long after) {
            this.characNo = characNo;
            this.before = before;
            this.delta = delta;
            this.after = after;
        }

        public int getCharacNo() { return characNo; }
        public long getBefore() { return before; }
        public long getDelta() { return delta; }

        /** Retained for the existing positive-only online reward flow. */
        public long getAdded() { return delta; }

        public long getAfter() { return after; }
    }

    final class InventorySnapshot {
        private final int accountId;
        private final int characNo;
        private final int scannedSlots;
        private final int totalOccupied;
        private final boolean truncated;
        private final boolean accountCargoAvailable;
        private final List<InventorySlot> slots;
        private final List<InventoryReadError> errors;

        public InventorySnapshot(int accountId, int characNo, int scannedSlots, int totalOccupied,
                                 boolean truncated, boolean accountCargoAvailable,
                                 List<InventorySlot> slots, List<InventoryReadError> errors) {
            this.accountId = accountId;
            this.characNo = characNo;
            this.scannedSlots = scannedSlots;
            this.totalOccupied = totalOccupied;
            this.truncated = truncated;
            this.accountCargoAvailable = accountCargoAvailable;
            this.slots = immutableCopy(slots);
            this.errors = immutableCopy(errors);
        }

        public int getAccountId() { return accountId; }
        public int getCharacNo() { return characNo; }
        public int getScannedSlots() { return scannedSlots; }
        public int getTotalOccupied() { return totalOccupied; }
        public boolean isTruncated() { return truncated; }
        public boolean isAccountCargoAvailable() { return accountCargoAvailable; }
        public List<InventorySlot> getSlots() { return slots; }
        public List<InventoryReadError> getErrors() { return errors; }
    }

    final class InventorySlot {
        private final int space;
        private final int slot;
        private final int itemId;
        private final long addInfo;

        public InventorySlot(int space, int slot, int itemId, long addInfo) {
            this.space = space;
            this.slot = slot;
            this.itemId = itemId;
            this.addInfo = addInfo;
        }

        public int getSpace() { return space; }
        public int getSlot() { return slot; }
        public int getItemId() { return itemId; }
        public long getAddInfo() { return addInfo; }
    }

    final class InventoryReadError {
        private final int space;
        private final int slot;
        private final String code;

        public InventoryReadError(int space, int slot, String code) {
            this.space = space;
            this.slot = slot;
            this.code = code;
        }

        public int getSpace() { return space; }
        public int getSlot() { return slot; }
        public String getCode() { return code; }
    }

    final class ContractSnapshot {
        private final String status;
        private final int accountId;
        private final int characNo;
        private final Integer characLevel;
        private final Integer configuredLevel;
        private final boolean hookInstalled;
        private final List<Integer> activePremiumTypes;
        private final List<ContractLevel> levels;

        public ContractSnapshot(String status, int accountId, int characNo, Integer characLevel,
                                Integer configuredLevel, boolean hookInstalled,
                                List<Integer> activePremiumTypes, List<ContractLevel> levels) {
            this.status = status;
            this.accountId = accountId;
            this.characNo = characNo;
            this.characLevel = characLevel;
            this.configuredLevel = configuredLevel;
            this.hookInstalled = hookInstalled;
            this.activePremiumTypes = immutableCopy(activePremiumTypes);
            this.levels = immutableCopy(levels);
        }

        public String getStatus() { return status; }
        public int getAccountId() { return accountId; }
        public int getCharacNo() { return characNo; }
        public Integer getCharacLevel() { return characLevel; }
        public Integer getConfiguredLevel() { return configuredLevel; }
        public boolean isHookInstalled() { return hookInstalled; }
        public List<Integer> getActivePremiumTypes() { return activePremiumTypes; }
        public List<ContractLevel> getLevels() { return levels; }
    }

    final class ContractLevel {
        private final int equipmentType;
        private final int rawLevel;
        private final int effectiveLevel;

        public ContractLevel(int equipmentType, int rawLevel, int effectiveLevel) {
            this.equipmentType = equipmentType;
            this.rawLevel = rawLevel;
            this.effectiveLevel = effectiveLevel;
        }

        public int getEquipmentType() { return equipmentType; }
        public int getRawLevel() { return rawLevel; }
        public int getEffectiveLevel() { return effectiveLevel; }
    }

    final class OnlineCharacter {
        private final int accountId;
        private final int characNo;
        private final long gold;

        public OnlineCharacter(int accountId, int characNo, long gold) {
            this.accountId = accountId;
            this.characNo = characNo;
            this.gold = gold;
        }

        public int getAccountId() { return accountId; }
        public int getCharacNo() { return characNo; }
        public long getGold() { return gold; }
    }

    static <T> List<T> immutableCopy(List<T> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
