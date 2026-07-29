package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.exception.ValidationException;
import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.utils.ChinaseUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GuildManagementService {

    public static final int MIN_GUILD_LEVEL = 1;
    public static final int MAX_GUILD_LEVEL = 30;
    public static final long MAX_GUILD_FUND = 4294967295L;

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private AccountVODao accountVODao;

    public Map<String, Object> listGuilds(String keyword, Integer guildId, int page, int pageSize) {
        requireAdmin();
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        int offset = (safePage - 1) * safePageSize;
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (guildId != null && guildId > 0) {
            where.append(" AND gi.guild_id=?");
            args.add(guildId);
        }
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (!normalizedKeyword.isEmpty()) {
            String storedKeyword = ChinaseUtil.toTraditional(normalizedKeyword);
            where.append(" AND (gi.guild_name LIKE ? OR gi.master_name LIKE ?)");
            args.add("%" + storedKeyword + "%");
            args.add("%" + storedKeyword + "%");
        }

        Number total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM d_guild.guild_info gi" + where,
                args.toArray(),
                Number.class);
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(safePageSize);
        queryArgs.add(offset);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT gi.guild_id AS guildId,gi.server_id AS serverId,gi.guild_name AS guildName," +
                        "gi.master_id AS masterId,gi.master_no AS masterNo,gi.master_name AS masterName," +
                        "gi.lev AS level,gi.guild_exp AS guildExp,gi.guild_fund AS fund," +
                        "gi.member_count AS memberCount,gi.create_time AS createTime," +
                        "gi.expire_flag AS expireFlag FROM d_guild.guild_info gi" + where +
                        " ORDER BY gi.guild_id DESC LIMIT ? OFFSET ?",
                queryArgs.toArray());
        List<Map<String, Object>> guilds = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            guilds.add(mapGuild(row));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("page", safePage);
        result.put("pageSize", safePageSize);
        result.put("totalSize", total == null ? 0 : total.intValue());
        result.put("list", guilds);
        result.put("minLevel", MIN_GUILD_LEVEL);
        result.put("maxLevel", MAX_GUILD_LEVEL);
        result.put("maxFund", MAX_GUILD_FUND);
        return result;
    }

    public Map<String, Object> listAvailableCharacters(String keyword, int page, int pageSize) {
        requireAdmin();
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        int offset = (safePage - 1) * safePageSize;
        StringBuilder where = new StringBuilder(
                " WHERE c.delete_flag=0 AND c.guild_id=0 " +
                        "AND EXISTS (SELECT 1 FROM taiwan_cain.charac_stat s WHERE s.charac_no=c.charac_no) " +
                        "AND NOT EXISTS (SELECT 1 FROM d_guild.guild_member gm WHERE gm.charac_no=c.charac_no " +
                        "AND gm.member_flag=1 AND gm.secede_type=0)");
        List<Object> args = new ArrayList<>();
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (!normalizedKeyword.isEmpty()) {
            String storedKeyword = ChinaseUtil.toTraditional(normalizedKeyword);
            where.append(" AND (c.charac_name LIKE ? OR a.accountname LIKE ? " +
                    "OR CAST(c.charac_no AS CHAR)=? OR CAST(c.m_id AS CHAR)=?)");
            args.add("%" + storedKeyword + "%");
            args.add("%" + normalizedKeyword + "%");
            args.add(normalizedKeyword);
            args.add(normalizedKeyword);
        }

        String from = " FROM taiwan_cain.charac_info c " +
                "LEFT JOIN d_taiwan.accounts a ON a.UID=c.m_id";
        Number total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + from + where,
                args.toArray(),
                Number.class);
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(safePageSize);
        queryArgs.add(offset);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT c.m_id AS memberId,c.charac_no AS characNo,c.charac_name AS characName," +
                        "a.accountname,c.job,c.grow_type AS growType,c.lev AS level,c.guild_id AS guildId" +
                        from + where + " ORDER BY c.lev DESC,c.charac_no DESC LIMIT ? OFFSET ?",
                queryArgs.toArray());
        List<Map<String, Object>> characters = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            characters.add(mapAvailableCharacter(row));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("page", safePage);
        result.put("pageSize", safePageSize);
        result.put("totalSize", total == null ? 0 : total.intValue());
        result.put("list", characters);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> addMember(int guildId, Map<String, Object> payload) {
        requireAdmin();
        if (payload == null || !payload.containsKey("characNo")) {
            throw new ValidationException("请选择要加入公会的角色");
        }
        int characNo = intValue(payload.get("characNo"), 0);
        if (characNo <= 0) {
            throw new ValidationException("角色参数无效");
        }
        if (guildId <= 0) {
            throw new ValidationException("公会 ID 无效");
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT c.m_id AS memberId,c.charac_no AS characNo,c.charac_name AS characName," +
                        "c.job,c.grow_type AS growType,c.lev AS level,c.guild_id AS guildId " +
                        "FROM taiwan_cain.charac_info c " +
                        "WHERE c.charac_no=? AND c.delete_flag=0 " +
                        "AND EXISTS (SELECT 1 FROM taiwan_cain.charac_stat s WHERE s.charac_no=c.charac_no) " +
                        "FOR UPDATE",
                characNo);
        if (rows.isEmpty()) {
            throw new ValidationException("角色不存在或尚未初始化完成");
        }
        Map<String, Object> character = rows.get(0);
        if (intValue(character.get("guildId"), 0) != 0) {
            throw new ValidationException("该角色已有公会，请刷新候选列表");
        }
        List<Map<String, Object>> memberships = jdbcTemplate.queryForList(
                "SELECT guild_id AS guildId,member_flag AS memberFlag,secede_type AS secedeType," +
                        "member_point AS memberPoint FROM d_guild.guild_member WHERE charac_no=? FOR UPDATE",
                characNo);
        Map<String, Object> historicalTargetMembership = null;
        for (Map<String, Object> membership : memberships) {
            boolean active = intValue(membership.get("memberFlag"), 0) == 1 &&
                    intValue(membership.get("secedeType"), 0) == 0;
            if (active) {
                throw new ValidationException("该角色已有公会，请刷新候选列表");
            }
            if (intValue(membership.get("guildId"), 0) == guildId) {
                historicalTargetMembership = membership;
            }
        }
        Map<String, Object> guild = requireGuild(guildId, true);

        int characterUpdated = jdbcTemplate.update(
                "UPDATE taiwan_cain.charac_info SET guild_id=?,guild_right=1,guild_secede=0 " +
                        "WHERE charac_no=? AND guild_id=0 AND delete_flag=0",
                guildId,
                characNo);
        if (characterUpdated != 1) {
            throw new ValidationException("角色公会状态已变化，请刷新后重试");
        }

        int serverId = intValue(guild.get("serverId"), 0);
        long memberId = longValue(character.get("memberId"), 0);
        String characName = stringValue(character.get("characName"), "");
        int job = intValue(character.get("job"), 0);
        int growType = intValue(character.get("growType"), 0);
        int level = intValue(character.get("level"), 0);
        long memberPoint = historicalTargetMembership == null
                ? 0
                : longValue(historicalTargetMembership.get("memberPoint"), 0);
        int memberWritten;
        if (historicalTargetMembership == null) {
            memberWritten = jdbcTemplate.update(
                    "INSERT INTO d_guild.guild_member " +
                            "(guild_id,m_id,server_id,charac_no,charac_name,memo,grade,job,grow_type,lev," +
                            "member_time,member_flag,last_play_time,age,born_year) " +
                            "VALUES (?,?,?,?,?,'Hello',3,?,?,?,NOW(),1,NOW(),0,'00')",
                    guildId,
                    memberId,
                    serverId,
                    characNo,
                    characName,
                    job,
                    growType,
                    level);
        } else {
            memberWritten = jdbcTemplate.update(
                    "UPDATE d_guild.guild_member SET m_id=?,server_id=?,charac_name=?,grade=3," +
                            "job=?,grow_type=?,lev=?,member_time=NOW(),member_flag=1,last_play_time=NOW()," +
                            "secede_type=0,secede_time='0000-00-00 00:00:00' " +
                            "WHERE guild_id=? AND charac_no=? AND NOT (member_flag=1 AND secede_type=0)",
                    memberId,
                    serverId,
                    characName,
                    job,
                    growType,
                    level,
                    guildId,
                    characNo);
        }
        if (memberWritten != 1) {
            throw new ValidationException("公会成员写入失败");
        }

        int guildUpdated = jdbcTemplate.update(
                "UPDATE d_guild.guild_info SET member_count=member_count+1 WHERE guild_id=?",
                guildId);
        if (guildUpdated != 1) {
            throw new ValidationException("公会成员数更新失败");
        }
        jdbcTemplate.update(
                "DELETE FROM d_guild.guild_join_list WHERE charac_no=?",
                characNo);
        jdbcTemplate.update(
                "UPDATE d_guild.guild_search SET member_count=" +
                        "(SELECT gi.member_count FROM d_guild.guild_info gi WHERE gi.guild_id=?) " +
                        "WHERE guild_id=?",
                guildId,
                guildId);

        int memberCount = intValue(guild.get("memberCount"), 0) + 1;
        guild.put("memberCount", memberCount);
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("guildId", guildId);
        member.put("memberId", memberId);
        member.put("serverId", serverId);
        member.put("characNo", characNo);
        member.put("characName", characName);
        member.put("grade", 3);
        member.put("level", level);
        member.put("memberPoint", memberPoint);
        member.put("memberTime", new java.sql.Timestamp(System.currentTimeMillis()));
        member.put("lastPlayTime", new java.sql.Timestamp(System.currentTimeMillis()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("guild", mapGuild(guild));
        result.put("member", mapMember(member, intValue(guild.get("masterNo"), 0)));
        return result;
    }

    public Map<String, Object> listMembers(int guildId, String keyword, int page, int pageSize) {
        requireAdmin();
        Map<String, Object> guild = mapGuild(requireGuild(guildId, false));
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        int offset = (safePage - 1) * safePageSize;
        StringBuilder where = new StringBuilder(
                " WHERE gm.guild_id=? AND gm.member_flag=1 AND gm.secede_type=0");
        List<Object> args = new ArrayList<>();
        args.add(guildId);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (!normalizedKeyword.isEmpty()) {
            String storedKeyword = ChinaseUtil.toTraditional(normalizedKeyword);
            where.append(" AND (gm.charac_name LIKE ? OR CAST(gm.charac_no AS CHAR)=? OR CAST(gm.m_id AS CHAR)=?)");
            args.add("%" + storedKeyword + "%");
            args.add(normalizedKeyword);
            args.add(normalizedKeyword);
        }
        Number total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM d_guild.guild_member gm" + where,
                args.toArray(),
                Number.class);
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(safePageSize);
        queryArgs.add(offset);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT gm.guild_id AS guildId,gm.m_id AS memberId,gm.server_id AS serverId," +
                        "gm.charac_no AS characNo,gm.charac_name AS characName,gm.grade," +
                        "gm.lev AS level,gm.member_point AS memberPoint,gm.member_time AS memberTime," +
                        "gm.last_play_time AS lastPlayTime FROM d_guild.guild_member gm" + where +
                        " ORDER BY gm.grade,gm.charac_no LIMIT ? OFFSET ?",
                queryArgs.toArray());
        List<Map<String, Object>> members = new ArrayList<>();
        int masterNo = intValue(guild.get("masterNo"), 0);
        for (Map<String, Object> row : rows) {
            members.add(mapMember(row, masterNo));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("guild", guild);
        result.put("page", safePage);
        result.put("pageSize", safePageSize);
        result.put("totalSize", total == null ? 0 : total.intValue());
        result.put("list", members);
        result.put("gradeOptions", gradeOptions());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateGuild(int guildId, Map<String, Object> payload) {
        requireAdmin();
        if (payload == null || !payload.containsKey("level") || !payload.containsKey("fund") ||
                !payload.containsKey("expectedLevel") || !payload.containsKey("expectedFund")) {
            throw new ValidationException("公会设置参数不完整");
        }
        int level = intValue(payload.get("level"), -1);
        long fund = longValue(payload.get("fund"), -1);
        int expectedLevel = intValue(payload.get("expectedLevel"), -1);
        long expectedFund = longValue(payload.get("expectedFund"), -1);
        if (level < MIN_GUILD_LEVEL || level > MAX_GUILD_LEVEL) {
            throw new ValidationException("公会等级只能设置为 1 到 30");
        }
        if (fund < 0 || fund > MAX_GUILD_FUND) {
            throw new ValidationException("公会资金必须在 0 到 4294967295 之间");
        }

        Map<String, Object> row = requireGuild(guildId, true);
        int currentLevel = intValue(row.get("level"), 0);
        long currentFund = longValue(row.get("fund"), 0);
        if (currentLevel != expectedLevel || currentFund != expectedFund) {
            throw new ValidationException("公会设置已被其他人修改，请刷新后重试");
        }
        if (currentLevel != level || currentFund != fund) {
            int updated = jdbcTemplate.update(
                    "UPDATE d_guild.guild_info SET " +
                            "lev_up_time=CASE WHEN lev<>? THEN NOW() ELSE lev_up_time END," +
                            "lev=?,guild_fund=? WHERE guild_id=?",
                    level,
                    level,
                    fund,
                    guildId);
            if (updated != 1) {
                throw new ValidationException("公会设置保存失败");
            }
            if (currentLevel != level) {
                jdbcTemplate.update(
                        "UPDATE d_guild.guild_search SET lev=? WHERE guild_id=?",
                        level,
                        guildId);
            }
        }
        row.put("level", level);
        row.put("fund", fund);
        return mapGuild(row);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateMemberGrade(int guildId, int characNo, Map<String, Object> payload) {
        AccountVO operator = requireAdmin();
        if (payload == null || !payload.containsKey("grade") || !payload.containsKey("expectedGrade")) {
            throw new ValidationException("成员职级参数不完整");
        }
        int grade = intValue(payload.get("grade"), -1);
        int expectedGrade = intValue(payload.get("expectedGrade"), -1);
        if (grade < 2 || grade > 4) {
            throw new ValidationException("非会长成员只能设置为 2、3 或 4");
        }

        Map<String, Object> guild = requireGuild(guildId, true);
        List<Map<String, Object>> members = jdbcTemplate.queryForList(
                "SELECT gm.guild_id AS guildId,gm.m_id AS memberId,gm.server_id AS serverId," +
                        "gm.charac_no AS characNo,gm.charac_name AS characName,gm.grade," +
                        "gm.lev AS level,gm.member_point AS memberPoint,gm.member_time AS memberTime," +
                        "gm.last_play_time AS lastPlayTime FROM d_guild.guild_member gm " +
                        "WHERE gm.guild_id=? AND gm.charac_no=? AND gm.member_flag=1 AND gm.secede_type=0 FOR UPDATE",
                guildId,
                characNo);
        if (members.isEmpty()) {
            throw new ValidationException("公会成员不存在");
        }
        Map<String, Object> member = members.get(0);
        int currentGrade = intValue(member.get("grade"), 0);
        if (characNo == intValue(guild.get("masterNo"), 0) || currentGrade == 1) {
            throw new ValidationException("会长职级不可修改");
        }
        if (currentGrade != expectedGrade) {
            throw new ValidationException("成员职级已被其他人修改，请刷新后重试");
        }
        if (currentGrade != grade) {
            int updated = jdbcTemplate.update(
                    "UPDATE d_guild.guild_member SET grade=? " +
                            "WHERE guild_id=? AND charac_no=? AND grade=? AND member_flag=1 AND secede_type=0",
                    grade,
                    guildId,
                    characNo,
                    currentGrade);
            if (updated != 1) {
                throw new ValidationException("成员职级保存失败，请刷新后重试");
            }
            jdbcTemplate.update(
                    "INSERT INTO d_guild.guild_grade_log " +
                            "(guild_id,m_id,server_id,charac_no,charac_name,occ_time,grade_prev,grade_next," +
                            "reason,admin_no,admin_name) VALUES (?,?,?,?,?,NOW(),?,?,?,?,?)",
                    guildId,
                    longValue(member.get("memberId"), 0),
                    intValue(member.get("serverId"), 0),
                    characNo,
                    stringValue(member.get("characName"), ""),
                    currentGrade,
                    grade,
                    "GM后台修改",
                    operator.getUid(),
                    truncate(stringValue(operator.getAccountname(), "GM"), 20));
        }
        member.put("grade", grade);
        return mapMember(member, intValue(guild.get("masterNo"), 0));
    }

    private Map<String, Object> requireGuild(int guildId, boolean forUpdate) {
        if (guildId <= 0) {
            throw new ValidationException("公会 ID 无效");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT gi.guild_id AS guildId,gi.server_id AS serverId,gi.guild_name AS guildName," +
                        "gi.master_id AS masterId,gi.master_no AS masterNo,gi.master_name AS masterName," +
                        "gi.lev AS level,gi.guild_exp AS guildExp,gi.guild_fund AS fund," +
                        "gi.member_count AS memberCount,gi.create_time AS createTime," +
                        "gi.expire_flag AS expireFlag FROM d_guild.guild_info gi WHERE gi.guild_id=?" +
                        (forUpdate ? " FOR UPDATE" : ""),
                guildId);
        if (rows.isEmpty()) {
            throw new ValidationException("公会不存在");
        }
        return new LinkedHashMap<>(rows.get(0));
    }

    private AccountVO requireAdmin() {
        Long operatorId = ThreadUtil.getUserId();
        AccountVO operator = operatorId == null ? null : accountVODao.get(operatorId);
        if (operator == null || !operator.isAdmin()) {
            throw new ValidationException("只有最高管理员才能管理公会");
        }
        return operator;
    }

    private Map<String, Object> mapGuild(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("guildId", intValue(row.get("guildId"), 0));
        result.put("serverId", intValue(row.get("serverId"), 0));
        result.put("guildName", ChinaseUtil.toSimple(stringValue(row.get("guildName"), "")));
        result.put("masterId", longValue(row.get("masterId"), 0));
        result.put("masterNo", intValue(row.get("masterNo"), 0));
        result.put("masterName", ChinaseUtil.toSimple(stringValue(row.get("masterName"), "")));
        result.put("level", intValue(row.get("level"), 0));
        result.put("guildExp", longValue(row.get("guildExp"), 0));
        result.put("fund", longValue(row.get("fund"), 0));
        result.put("memberCount", intValue(row.get("memberCount"), 0));
        result.put("createTime", row.get("createTime"));
        result.put("expireFlag", intValue(row.get("expireFlag"), 0));
        result.put("minLevel", MIN_GUILD_LEVEL);
        result.put("maxLevel", MAX_GUILD_LEVEL);
        result.put("maxFund", MAX_GUILD_FUND);
        return result;
    }

    private Map<String, Object> mapAvailableCharacter(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("memberId", longValue(row.get("memberId"), 0));
        result.put("characNo", intValue(row.get("characNo"), 0));
        result.put("characName", ChinaseUtil.toSimple(stringValue(row.get("characName"), "")));
        result.put("accountname", stringValue(row.get("accountname"), ""));
        result.put("job", intValue(row.get("job"), 0));
        result.put("growType", intValue(row.get("growType"), 0));
        result.put("level", intValue(row.get("level"), 0));
        return result;
    }

    private Map<String, Object> mapMember(Map<String, Object> row, int masterNo) {
        Map<String, Object> result = new LinkedHashMap<>();
        int characNo = intValue(row.get("characNo"), 0);
        int grade = intValue(row.get("grade"), 4);
        boolean leader = characNo == masterNo || grade == 1;
        result.put("guildId", intValue(row.get("guildId"), 0));
        result.put("memberId", longValue(row.get("memberId"), 0));
        result.put("serverId", intValue(row.get("serverId"), 0));
        result.put("characNo", characNo);
        result.put("characName", ChinaseUtil.toSimple(stringValue(row.get("characName"), "")));
        result.put("grade", leader ? 1 : grade);
        result.put("gradeName", gradeName(leader ? 1 : grade));
        result.put("level", intValue(row.get("level"), 0));
        result.put("memberPoint", longValue(row.get("memberPoint"), 0));
        result.put("memberTime", row.get("memberTime"));
        result.put("lastPlayTime", row.get("lastPlayTime"));
        result.put("leader", leader);
        result.put("canEditGrade", !leader);
        return result;
    }

    private List<Map<String, Object>> gradeOptions() {
        List<Map<String, Object>> options = new ArrayList<>();
        for (int grade : Arrays.asList(2, 3, 4)) {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("value", grade);
            option.put("label", gradeName(grade));
            options.add(option);
        }
        return options;
    }

    private String gradeName(int grade) {
        switch (grade) {
            case 1:
                return "会长";
            case 2:
                return "副会长";
            case 3:
                return "优秀";
            case 4:
                return "普通";
            default:
                return "未知";
        }
    }

    private int intValue(Object value, int fallback) {
        try {
            return value == null ? fallback : ((Number) value).intValue();
        } catch (Exception ignored) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (Exception ignoredAgain) {
                return fallback;
            }
        }
    }

    private long longValue(Object value, long fallback) {
        try {
            return value == null ? fallback : ((Number) value).longValue();
        } catch (Exception ignored) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (Exception ignoredAgain) {
                return fallback;
            }
        }
    }

    private String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
