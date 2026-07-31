package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.exception.ValidationException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GmAnalyticsService {

    private static final int MAX_RANGE_DAYS = 366;
    private static final Map<String, MetricDefinition> METRICS = metrics();

    @Resource
    private JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> metricOptions() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (MetricDefinition definition : METRICS.values()) {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("value", definition.key);
            option.put("label", definition.label);
            option.put("unit", definition.unit);
            option.put("supported", definition.trendSql != null);
            option.put("groups", new ArrayList<>(definition.breakdownSql.keySet()));
            result.add(option);
        }
        return result;
    }

    public Map<String, Object> report(String metric, String groupBy, LocalDate start, LocalDate end) {
        MetricDefinition definition = METRICS.get(metric);
        if (definition == null) {
            throw new ValidationException("运营统计指标无效");
        }
        if (start == null || end == null || end.isBefore(start)) {
            throw new ValidationException("运营统计日期范围无效");
        }
        long inclusiveDays = ChronoUnit.DAYS.between(start, end) + 1L;
        if (inclusiveDays > MAX_RANGE_DAYS) {
            throw new ValidationException("运营统计日期范围最多 366 天");
        }
        String safeGroup = groupBy == null || groupBy.trim().isEmpty() ? "none" : groupBy.trim();
        if (!"none".equals(safeGroup) && !definition.breakdownSql.containsKey(safeGroup)) {
            throw new ValidationException("当前指标不支持该分组维度");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metric", definition.key);
        result.put("label", definition.label);
        result.put("unit", definition.unit);
        result.put("groupBy", safeGroup);
        result.put("start", start.toString());
        result.put("end", end.toString());
        result.put("maxRangeDays", MAX_RANGE_DAYS);
        if (definition.trendSql == null) {
            result.put("trend", Collections.emptyList());
            result.put("breakdown", Collections.emptyList());
            result.put("summary", summary(Collections.<Map<String, Object>>emptyList(), inclusiveDays));
            result.put("sourceStatus", source("UNAVAILABLE", definition.unavailableReason));
            return result;
        }

        Date startDate = Date.valueOf(start);
        Date endExclusive = Date.valueOf(end.plusDays(1));
        try {
            List<Map<String, Object>> trend = normalizeRows(
                    jdbcTemplate.queryForList(definition.trendSql, startDate, endExclusive),
                    definition.label);
            List<Map<String, Object>> breakdown = Collections.emptyList();
            if (!"none".equals(safeGroup)) {
                breakdown = normalizeRows(jdbcTemplate.queryForList(
                        definition.breakdownSql.get(safeGroup), startDate, endExclusive), definition.label);
            }
            result.put("trend", trend);
            result.put("breakdown", breakdown);
            result.put("summary", summary(trend, inclusiveDays));
            result.put("sourceStatus", source("AVAILABLE", null));
        } catch (DataAccessException exception) {
            result.put("trend", Collections.emptyList());
            result.put("breakdown", Collections.emptyList());
            result.put("summary", summary(Collections.<Map<String, Object>>emptyList(), inclusiveDays));
            result.put("sourceStatus", source("UNAVAILABLE", "统计数据源查询失败"));
        }
        return result;
    }

    public String toCsv(Map<String, Object> report) {
        StringBuilder csv = new StringBuilder("date,label,value\r\n");
        Object trendValue = report == null ? null : report.get("trend");
        if (!(trendValue instanceof Iterable)) {
            return csv.toString();
        }
        for (Object value : (Iterable<?>) trendValue) {
            if (!(value instanceof Map)) {
                continue;
            }
            Map<?, ?> row = (Map<?, ?>) value;
            csv.append(csvCell(row.get("date"))).append(',')
                    .append(csvCell(row.get("label"))).append(',')
                    .append(csvCell(row.get("value"))).append("\r\n");
        }
        return csv.toString();
    }

    private List<Map<String, Object>> normalizeRows(List<Map<String, Object>> rows, String defaultLabel) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            Object date = find(row, "date", "day");
            Object label = find(row, "label", "groupLabel", "category");
            Object value = find(row, "value", "metricValue", "total");
            normalized.put("date", date == null ? "" : String.valueOf(date));
            normalized.put("label", label == null ? defaultLabel : String.valueOf(label));
            normalized.put("value", value instanceof Number ? ((Number) value).longValue() : 0L);
            result.add(normalized);
        }
        return result;
    }

    private Map<String, Object> summary(List<Map<String, Object>> trend, long days) {
        long total = 0L;
        long peak = 0L;
        for (Map<String, Object> row : trend) {
            Object value = row.get("value");
            long number = value instanceof Number ? ((Number) value).longValue() : 0L;
            total += number;
            peak = Math.max(peak, number);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("average", days <= 0 ? 0D : ((double) total) / days);
        result.put("peak", peak);
        result.put("samples", trend.size());
        return result;
    }

    private Map<String, Object> source(String status, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("reason", reason);
        result.put("observedAt", System.currentTimeMillis());
        return result;
    }

    private Object find(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private String csvCell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        boolean quote = text.indexOf(',') >= 0 || text.indexOf('"') >= 0 ||
                text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0;
        text = text.replace("\"", "\"\"");
        return quote ? "\"" + text + "\"" : text;
    }

    private static Map<String, MetricDefinition> metrics() {
        Map<String, MetricDefinition> metrics = new LinkedHashMap<>();
        metrics.put("registrations", unavailable("registrations", "注册账号", "个",
                "账号表没有可信注册时间字段"));
        metrics.put("new_characters", metric("new_characters", "新建角色", "个",
                "SELECT DATE(create_time) AS date,COUNT(*) AS value " +
                        "FROM taiwan_cain.charac_info WHERE create_time>=? AND create_time<? " +
                        "AND delete_flag=0 GROUP BY DATE(create_time) ORDER BY DATE(create_time)",
                group("job", "SELECT CAST(job AS CHAR) AS label,COUNT(*) AS value " +
                        "FROM taiwan_cain.charac_info WHERE create_time>=? AND create_time<? " +
                        "AND delete_flag=0 GROUP BY job ORDER BY value DESC")));
        metrics.put("active_accounts", metric("active_accounts", "日活账号", "个",
                "SELECT occ_date AS date,COUNT(DISTINCT m_id) AS value " +
                        "FROM taiwan_login.member_play_info WHERE occ_date>=? AND occ_date<? " +
                        "GROUP BY occ_date ORDER BY occ_date",
                group("server", "SELECT CAST(server_id AS CHAR) AS label,COUNT(DISTINCT m_id) AS value " +
                        "FROM taiwan_login.member_play_info WHERE occ_date>=? AND occ_date<? " +
                        "GROUP BY server_id ORDER BY value DESC")));
        metrics.put("play_time", metric("play_time", "在线时长", "秒",
                "SELECT occ_date AS date,SUM(play_time) AS value FROM taiwan_login.member_play_info " +
                        "WHERE occ_date>=? AND occ_date<? GROUP BY occ_date ORDER BY occ_date",
                group("server", "SELECT CAST(server_id AS CHAR) AS label,SUM(play_time) AS value " +
                        "FROM taiwan_login.member_play_info WHERE occ_date>=? AND occ_date<? " +
                        "GROUP BY server_id ORDER BY value DESC")));
        metrics.put("used_fatigue", metric("used_fatigue", "消耗疲劳", "点",
                "SELECT occ_date AS date,SUM(used_fatigue) AS value FROM taiwan_login.member_play_info " +
                        "WHERE occ_date>=? AND occ_date<? GROUP BY occ_date ORDER BY occ_date",
                Collections.<String, String>emptyMap()));
        metrics.put("dungeon_activity", unavailable("dungeon_activity", "副本活动", "次",
                "当前数据库没有可信的按日副本历史表"));
        metrics.put("reward_count", metric("reward_count", "奖励发放", "次",
                "SELECT DATE(awarded_at) AS date,COUNT(*) AS value FROM dnf_service.gm_online_reward_log " +
                        "WHERE awarded_at>=? AND awarded_at<? GROUP BY DATE(awarded_at) ORDER BY DATE(awarded_at)",
                group("status", "SELECT status AS label,COUNT(*) AS value " +
                        "FROM dnf_service.gm_online_reward_log WHERE awarded_at>=? AND awarded_at<? " +
                        "GROUP BY status ORDER BY value DESC")));
        metrics.put("currency_changes", metric("currency_changes", "货币变更", "次",
                "SELECT DATE(created_at) AS date,COUNT(*) AS value FROM dnf_service.gm_operation_audit " +
                        "WHERE created_at>=? AND created_at<? AND module='CURRENCY' " +
                        "GROUP BY DATE(created_at) ORDER BY DATE(created_at)",
                group("status", "SELECT status AS label,COUNT(*) AS value " +
                        "FROM dnf_service.gm_operation_audit WHERE created_at>=? AND created_at<? " +
                        "AND module='CURRENCY' GROUP BY status ORDER BY value DESC")));
        metrics.put("guild_activity", metric("guild_activity", "公会新增成员", "人",
                "SELECT DATE(member_time) AS date,COUNT(*) AS value FROM d_guild.guild_member " +
                        "WHERE member_time>=? AND member_time<? AND member_flag=1 AND secede_type=0 " +
                        "GROUP BY DATE(member_time) ORDER BY DATE(member_time)",
                group("guild", "SELECT CAST(guild_id AS CHAR) AS label,COUNT(*) AS value " +
                        "FROM d_guild.guild_member WHERE member_time>=? AND member_time<? " +
                        "AND member_flag=1 AND secede_type=0 GROUP BY guild_id ORDER BY value DESC LIMIT 50")));
        return Collections.unmodifiableMap(metrics);
    }

    private static MetricDefinition metric(String key, String label, String unit, String trendSql,
                                           Map<String, String> breakdownSql) {
        return new MetricDefinition(key, label, unit, trendSql, breakdownSql, null);
    }

    private static MetricDefinition unavailable(String key, String label, String unit, String reason) {
        return new MetricDefinition(key, label, unit, null, Collections.<String, String>emptyMap(), reason);
    }

    private static Map<String, String> group(String name, String sql) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(name, sql);
        return result;
    }

    private static final class MetricDefinition {
        private final String key;
        private final String label;
        private final String unit;
        private final String trendSql;
        private final Map<String, String> breakdownSql;
        private final String unavailableReason;

        private MetricDefinition(String key, String label, String unit, String trendSql,
                                 Map<String, String> breakdownSql, String unavailableReason) {
            this.key = key;
            this.label = label;
            this.unit = unit;
            this.trendSql = trendSql;
            this.breakdownSql = breakdownSql;
            this.unavailableReason = unavailableReason;
        }
    }
}
