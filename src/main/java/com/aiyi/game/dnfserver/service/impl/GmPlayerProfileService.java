package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.exception.ValidationException;
import com.aiyi.game.dnfserver.utils.ChinaseUtil;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GmPlayerProfileService {

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private GameRuntimeClient gameRuntimeClient;

    public Map<String, Object> basic(int characNo) {
        Map<String, Object> row = requireCharacter(characNo);
        Map<String, Object> result = new LinkedHashMap<>();
        long accountId = longValue(row.get("accountId"));
        result.put("accountId", accountId);
        result.put("accountname", stringValue(row.get("accountname")));
        result.put("characNo", intValue(row.get("characNo")));
        result.put("characName", ChinaseUtil.toSimple(stringValue(row.get("characName"))));
        result.put("level", intValue(row.get("level")));
        result.put("job", intValue(row.get("job")));
        result.put("growType", intValue(row.get("growType")));
        result.put("village", intValue(row.get("village")));
        result.put("experience", longValue(row.get("experience")));
        result.put("fatigue", intValue(row.get("fatigue")));
        result.put("maxFatigue", intValue(row.get("maxFatigue")));
        result.put("premiumFatigue", intValue(row.get("premiumFatigue")));
        result.put("maxPremiumFatigue", intValue(row.get("maxPremiumFatigue")));
        result.put("gold", longValue(row.get("gold")));
        result.put("createTime", row.get("createTime"));
        result.put("lastPlayTime", row.get("lastPlayTime"));
        result.put("totalPlayTime", longValue(row.get("totalPlayTime")));
        result.put("guildId", longValue(row.get("guildId")));
        result.put("guildName", ChinaseUtil.toSimple(stringValue(row.get("guildName"))));
        result.put("guildGrade", intValue(row.get("guildGrade")));
        result.put("runtime", runtime(accountId, characNo));
        return result;
    }

    public Map<String, Object> mailAndRewards(int characNo) {
        requireCharacter(characNo);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("characNo", characNo);
        try {
            Number mailCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM taiwan_cain_2nd.postal WHERE receive_charac_no=?",
                    new Object[]{characNo}, Number.class);
            Number unclaimedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM taiwan_cain_2nd.postal p LEFT JOIN taiwan_cain_2nd.letter l " +
                            "ON l.letter_id=p.letter_id WHERE p.receive_charac_no=? " +
                            "AND p.receive_time='0000-00-00 00:00:00' AND (l.stat IS NULL OR l.stat=0)",
                    new Object[]{characNo}, Number.class);
            result.put("mailCount", mailCount == null ? 0 : mailCount.intValue());
            result.put("unclaimedCount", unclaimedCount == null ? 0 : unclaimedCount.intValue());
            result.put("mailSource", source("AVAILABLE", null));
        } catch (DataAccessException exception) {
            result.put("mailCount", null);
            result.put("unclaimedCount", null);
            result.put("mailSource", source("UNAVAILABLE", "邮箱数据源查询失败"));
        }
        try {
            result.put("recentRewards", jdbcTemplate.queryForList(
                    "SELECT id,interval_minutes AS intervalMinutes,cera_point AS ceraPoint,gold," +
                            "awarded_at AS awardedAt,status,message FROM dnf_service.gm_online_reward_log " +
                            "WHERE charac_no=? ORDER BY awarded_at DESC LIMIT 50",
                    characNo));
            result.put("rewardSource", source("AVAILABLE", null));
        } catch (DataAccessException exception) {
            result.put("recentRewards", Collections.emptyList());
            result.put("rewardSource", source("UNAVAILABLE", "奖励历史数据源查询失败"));
        }
        return result;
    }

    public Map<String, Object> guildAndActivity(int characNo) {
        Map<String, Object> character = requireCharacter(characNo);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("characNo", characNo);
        result.put("guildId", longValue(character.get("guildId")));
        try {
            List<Map<String, Object>> membership = jdbcTemplate.queryForList(
                    "SELECT gm.guild_id AS guildId,gi.guild_name AS guildName,gm.grade," +
                            "gm.member_point AS memberPoint,gm.member_time AS memberTime," +
                            "gm.last_play_time AS lastPlayTime FROM d_guild.guild_member gm " +
                            "LEFT JOIN d_guild.guild_info gi ON gi.guild_id=gm.guild_id " +
                            "WHERE gm.charac_no=? AND gm.member_flag=1 AND gm.secede_type=0",
                    characNo);
            if (membership.isEmpty()) {
                result.put("membership", null);
            } else {
                Map<String, Object> row = new LinkedHashMap<>(membership.get(0));
                row.put("guildName", ChinaseUtil.toSimple(stringValue(row.get("guildName"))));
                result.put("membership", row);
            }
            result.put("guildSource", source("AVAILABLE", null));
        } catch (DataAccessException exception) {
            result.put("membership", null);
            result.put("guildSource", source("UNAVAILABLE", "公会数据源查询失败"));
        }
        try {
            result.put("activity", jdbcTemplate.queryForList(
                    "SELECT mpi.occ_date AS date,mpi.play_time AS playTime,mpi.play_count AS playCount," +
                            "mpi.trade_cnt AS tradeCount,mpi.exp,mpi.used_fatigue AS usedFatigue " +
                            "FROM taiwan_login.member_play_info mpi WHERE mpi.m_id=? " +
                            "ORDER BY mpi.occ_date DESC LIMIT 30",
                    longValue(character.get("accountId"))));
            result.put("activitySource", source("AVAILABLE", null));
        } catch (DataAccessException exception) {
            result.put("activity", Collections.emptyList());
            result.put("activitySource", source("UNAVAILABLE", "活动数据源查询失败"));
        }
        return result;
    }

    public Map<String, Object> securityAndAudit(int characNo) {
        Map<String, Object> character = requireCharacter(characNo);
        Map<String, Object> result = new LinkedHashMap<>();
        long accountId = longValue(character.get("accountId"));
        result.put("characNo", characNo);
        result.put("accountId", accountId);
        try {
            List<Map<String, Object>> security = jdbcTemplate.queryForList(
                    "SELECT UID AS accountId,accountname,qq,ip,login_IP AS loginIp," +
                            "login_Mac AS loginMac,seal_IP AS sealIp,seal_MAC AS sealMac," +
                            "seal_accountname AS sealAccount FROM d_taiwan.accounts WHERE UID=?",
                    accountId);
            result.put("security", security.isEmpty() ? null : security.get(0));
            result.put("securitySource", source("AVAILABLE", null));
        } catch (DataAccessException exception) {
            result.put("security", null);
            result.put("securitySource", source("UNAVAILABLE", "安全信息数据源查询失败"));
        }
        try {
            result.put("audit", jdbcTemplate.queryForList(
                    "SELECT id,request_id AS requestId,operator_name AS operatorName,module,action," +
                            "status,message,created_at AS createdAt,completed_at AS completedAt " +
                            "FROM dnf_service.gm_operation_audit WHERE target_charac_no=? " +
                            "ORDER BY created_at DESC LIMIT 100",
                    characNo));
            result.put("auditSource", source("AVAILABLE", null));
        } catch (DataAccessException exception) {
            result.put("audit", Collections.emptyList());
            result.put("auditSource", source("UNAVAILABLE", "操作审计数据源查询失败"));
        }
        return result;
    }

    public long accountIdFor(int characNo) {
        return longValue(requireCharacter(characNo).get("accountId"));
    }

    private Map<String, Object> requireCharacter(int characNo) {
        if (characNo <= 0) {
            throw new ValidationException("角色编号无效");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT c.m_id AS accountId,a.accountname,c.charac_no AS characNo," +
                        "c.charac_name AS characName,c.lev AS level,c.job,c.grow_type AS growType," +
                        "c.village,c.exp AS experience,c.fatigue,c.max_fatigue AS maxFatigue," +
                        "c.premium_fatigue AS premiumFatigue,c.max_premium_fatigue AS maxPremiumFatigue," +
                        "NULLIF(c.create_time,'0000-00-00 00:00:00') AS createTime," +
                        "NULLIF(c.last_play_time,'0000-00-00 00:00:00') AS lastPlayTime,c.guild_id AS guildId," +
                        "i.money AS gold,s.total_play_time AS totalPlayTime,gi.guild_name AS guildName," +
                        "gm.grade AS guildGrade FROM taiwan_cain.charac_info c " +
                        "LEFT JOIN d_taiwan.accounts a ON a.UID=c.m_id " +
                        "LEFT JOIN taiwan_cain_2nd.inventory i ON i.charac_no=c.charac_no " +
                        "LEFT JOIN taiwan_cain.charac_stat s ON s.charac_no=c.charac_no " +
                        "LEFT JOIN d_guild.guild_info gi ON gi.guild_id=c.guild_id " +
                        "LEFT JOIN d_guild.guild_member gm ON gm.guild_id=c.guild_id AND gm.charac_no=c.charac_no " +
                        "AND gm.member_flag=1 AND gm.secede_type=0 " +
                        "WHERE c.charac_no=? AND c.delete_flag=0",
                characNo);
        if (rows.isEmpty()) {
            throw new ValidationException("角色不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> runtime(long accountId, int characNo) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (accountId <= 0 || accountId > Integer.MAX_VALUE) {
            result.put("status", "UNAVAILABLE");
            result.put("online", false);
            result.put("reason", "账号编号超出运行时协议范围");
            return result;
        }
        try {
            GameRuntimeClient.OnlineCharacter runtime = gameRuntimeClient.findOnlineCharacter((int) accountId);
            boolean targetOnline = runtime.getCharacNo() == characNo;
            result.put("status", targetOnline ? "AVAILABLE" : "ONLINE_OTHER_CHARACTER");
            result.put("online", targetOnline);
            result.put("activeCharacNo", runtime.getCharacNo());
            result.put("gold", runtime.getGold());
            result.put("reason", targetOnline ? null : "同账号的其他角色在线");
        } catch (RuntimeException exception) {
            result.put("status", "UNAVAILABLE");
            result.put("online", false);
            result.put("reason", compact(exception.getMessage()));
        }
        return result;
    }

    private Map<String, Object> source(String status, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("reason", reason);
        return result;
    }

    private String compact(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "运行时数据源不可用";
        }
        return value.length() <= 240 ? value : value.substring(0, 240);
    }

    private int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
