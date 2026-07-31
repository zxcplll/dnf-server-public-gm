package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.game.dnfserver.utils.ChinaseUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GmRealtimePlayerService {

    private static final int RUNTIME_PAGE_LIMIT = 200;
    private static final int MAX_KEYWORD_SCAN = 512;

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private GameRuntimeClient gameRuntimeClient;

    public Map<String, Object> online(String keyword, int offset, int limit) {
        int safeOffset = Math.max(0, Math.min(512, offset));
        int safeLimit = Math.max(1, Math.min(200, limit));
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("offset", safeOffset);
        result.put("limit", safeLimit);
        try {
            CollectedPlayers collected = normalizedKeyword.isEmpty()
                    ? page(safeOffset, safeLimit)
                    : scanForKeyword();
            List<GameRuntimeClient.PlayerSnapshot> players = collected.players;
            Map<Integer, Map<String, Object>> database = databaseRows(players);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (GameRuntimeClient.PlayerSnapshot player : players) {
                Map<String, Object> db = database.get(player.getCharacNo());
                Map<String, Object> row = mapPlayer(player, db);
                if (!normalizedKeyword.isEmpty() && !matches(row, normalizedKeyword)) continue;
                rows.add(row);
            }
            int total = normalizedKeyword.isEmpty() ? collected.total : rows.size();
            if (!normalizedKeyword.isEmpty()) {
                rows = pageRows(rows, safeOffset, safeLimit);
            }
            result.put("status", "AVAILABLE");
            result.put("reason", null);
            result.put("total", total);
            result.put("truncated", collected.truncated);
            result.put("list", rows);
        } catch (RuntimeException exception) {
            result.put("status", "DEGRADED");
            result.put("reason", compact(exception.getMessage()));
            result.put("total", 0);
            result.put("truncated", false);
            result.put("list", Collections.emptyList());
        }
        result.put("observedAt", System.currentTimeMillis());
        return result;
    }

    private CollectedPlayers page(int offset, int limit) {
        GameRuntimeClient.OnlineSnapshot snapshot = gameRuntimeClient.onlineSnapshot(offset, limit);
        return new CollectedPlayers(snapshot.getPlayers(), snapshot.getTotal(), snapshot.isTruncated());
    }

    private CollectedPlayers scanForKeyword() {
        GameRuntimeClient.OnlineSnapshot first = gameRuntimeClient.onlineSnapshot(0, RUNTIME_PAGE_LIMIT);
        List<GameRuntimeClient.PlayerSnapshot> players = new ArrayList<>(first.getPlayers());
        int nextOffset = players.size();
        int available = Math.min(first.getTotal(), MAX_KEYWORD_SCAN);
        while (nextOffset < available && players.size() < MAX_KEYWORD_SCAN) {
            int requestLimit = Math.min(RUNTIME_PAGE_LIMIT, MAX_KEYWORD_SCAN - nextOffset);
            GameRuntimeClient.OnlineSnapshot page = gameRuntimeClient.onlineSnapshot(nextOffset, requestLimit);
            List<GameRuntimeClient.PlayerSnapshot> pagePlayers = page.getPlayers();
            if (pagePlayers.isEmpty()) break;
            players.addAll(pagePlayers);
            nextOffset += pagePlayers.size();
        }
        if (players.size() > MAX_KEYWORD_SCAN) {
            players = new ArrayList<>(players.subList(0, MAX_KEYWORD_SCAN));
        }
        return new CollectedPlayers(players, first.getTotal(), first.getTotal() > players.size());
    }

    private List<Map<String, Object>> pageRows(List<Map<String, Object>> rows, int offset, int limit) {
        if (offset >= rows.size()) return Collections.emptyList();
        int end = Math.min(rows.size(), offset + limit);
        return new ArrayList<>(rows.subList(offset, end));
    }

    private Map<Integer, Map<String, Object>> databaseRows(List<GameRuntimeClient.PlayerSnapshot> players) {
        Map<Integer, Map<String, Object>> result = new LinkedHashMap<>();
        if (players == null || players.isEmpty()) return result;
        StringBuilder sql = new StringBuilder(
                "SELECT c.charac_no AS characNo,c.m_id AS accountId,c.charac_name AS characName," +
                        "a.accountname,c.guild_id AS guildId,gi.guild_name AS guildName " +
                        "FROM taiwan_cain.charac_info c LEFT JOIN d_taiwan.accounts a ON a.UID=c.m_id " +
                        "LEFT JOIN d_guild.guild_info gi ON gi.guild_id=c.guild_id WHERE c.charac_no IN (");
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            if (i > 0) sql.append(',');
            sql.append('?');
            args.add(players.get(i).getCharacNo());
        }
        sql.append(')');
        try {
            for (Map<String, Object> row : jdbcTemplate.queryForList(sql.toString(), args.toArray())) {
                result.put(intValue(row.get("characNo")), row);
            }
        } catch (RuntimeException ignored) {
            // Runtime truth is still useful when the enrichment database is temporarily unavailable.
        }
        return result;
    }

    private Map<String, Object> mapPlayer(GameRuntimeClient.PlayerSnapshot player, Map<String, Object> db) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accountId", player.getAccountId());
        result.put("characNo", player.getCharacNo());
        result.put("state", player.getState());
        result.put("online", player.getState() >= 3);
        result.put("level", player.getLevel());
        result.put("job", player.getJob());
        result.put("growType", player.getGrowType());
        result.put("gold", player.getGold());
        result.put("fatigue", player.getFatigue());
        result.put("maxFatigue", player.getMaxFatigue());
        result.put("village", player.getVillage());
        result.put("area", player.getArea());
        result.put("posX", player.getPosX());
        result.put("posY", player.getPosY());
        result.put("inParty", player.isInParty());
        result.put("guildId", player.getGuildId());
        result.put("inTrade", player.isInTrade());
        result.put("unavailable", player.getUnavailable());
        if (db != null) {
            result.put("characName", ChinaseUtil.toSimple(stringValue(db.get("characName"))));
            result.put("accountname", stringValue(db.get("accountname")));
            result.put("guildName", ChinaseUtil.toSimple(stringValue(db.get("guildName"))));
        } else {
            result.put("characName", "");
            result.put("accountname", "");
            result.put("guildName", "");
        }
        result.put("source", db == null ? "RUNTIME_ONLY" : "RUNTIME_DATABASE");
        return result;
    }

    private boolean matches(Map<String, Object> row, String keyword) {
        for (String key : new String[]{"accountId", "characNo", "characName", "accountname", "guildName"}) {
            Object value = row.get(key);
            if (value != null && String.valueOf(value).toLowerCase().contains(keyword)) return true;
        }
        return false;
    }

    private int intValue(Object value) { return value instanceof Number ? ((Number) value).intValue() : 0; }
    private String stringValue(Object value) { return value == null ? "" : String.valueOf(value); }
    private String compact(String value) { if (value == null || value.trim().isEmpty()) return "实时在线数据源不可用"; return value.length() <= 240 ? value : value.substring(0, 240); }

    private static final class CollectedPlayers {
        private final List<GameRuntimeClient.PlayerSnapshot> players;
        private final int total;
        private final boolean truncated;

        private CollectedPlayers(List<GameRuntimeClient.PlayerSnapshot> players, int total, boolean truncated) {
            this.players = players == null ? Collections.<GameRuntimeClient.PlayerSnapshot>emptyList() : players;
            this.total = total;
            this.truncated = truncated;
        }
    }
}
