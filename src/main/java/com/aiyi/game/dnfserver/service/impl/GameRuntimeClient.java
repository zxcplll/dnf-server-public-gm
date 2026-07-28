package com.aiyi.game.dnfserver.service.impl;

public interface GameRuntimeClient {

    OnlineCharacter findOnlineCharacter(int accountId);

    GoldChange addGold(String requestId, int accountId, int characNo, int amount);

    final class GoldChange {
        private final int characNo;
        private final long before;
        private final long added;
        private final long after;

        public GoldChange(int characNo, long before, long added, long after) {
            this.characNo = characNo;
            this.before = before;
            this.added = added;
            this.after = after;
        }

        public int getCharacNo() {
            return characNo;
        }

        public long getBefore() {
            return before;
        }

        public long getAdded() {
            return added;
        }

        public long getAfter() {
            return after;
        }
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

        public int getAccountId() {
            return accountId;
        }

        public int getCharacNo() {
            return characNo;
        }

        public long getGold() {
            return gold;
        }
    }
}
