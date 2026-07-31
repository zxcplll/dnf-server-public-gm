package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.exception.ValidationException;
import com.aiyi.game.dnfserver.entity.gm.GmOperationContext;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GmContractService {

    private static final List<Integer> SUPPORTED_TYPES = Arrays.asList(9, 10, 11, 22);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private GmPlayerProfileService profileService;
    @Resource
    private GameRuntimeClient gameRuntimeClient;
    @Resource
    private GmAuthorizationService authorizationService;
    @Resource
    private GmOperationAuditService auditService;
    @Resource
    private GmTargetTokenService targetTokenService;

    public boolean isSupportedType(int preType) {
        return SUPPORTED_TYPES.contains(preType);
    }

    void validateContractWindow(int preType, LocalDateTime start, LocalDateTime end) {
        if (!isSupportedType(preType)) {
            throw new ValidationException("该契约类型不支持后台管理");
        }
        if (start == null || end == null || !end.isAfter(start)) {
            throw new ValidationException("契约结束时间必须晚于开始时间");
        }
    }

    public Map<String, Object> list(int characNo) {
        Map<String, Object> profile = profileService.basic(characNo);
        long accountId = longValue(profile.get("accountId"));
        List<Map<String, Object>> contracts = new ArrayList<>();
        Map<String, Object> source = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT event_id AS eventId,pre_type AS preType,server_id AS serverId,m_id AS accountId," +
                            "service_start AS serviceStart,service_end AS serviceEnd " +
                            "FROM taiwan_login.member_premium WHERE m_id=? ORDER BY service_start DESC,event_id DESC",
                    accountId);
            LocalDateTime now = LocalDateTime.now();
            for (Map<String, Object> row : rows) {
                Map<String, Object> contract = new LinkedHashMap<>(row);
                int preType = intValue(row.get("preType"));
                LocalDateTime start = toDateTime(row.get("serviceStart"));
                LocalDateTime end = toDateTime(row.get("serviceEnd"));
                contract.put("preType", preType);
                contract.put("supported", isSupportedType(preType));
                contract.put("state", end != null && end.isBefore(now) ? "EXPIRED" :
                        (start != null && start.isAfter(now) ? "SCHEDULED" : "ACTIVE"));
                contracts.add(contract);
            }
            source.put("status", "AVAILABLE");
            source.put("reason", null);
        } catch (DataAccessException exception) {
            source.put("status", "UNAVAILABLE");
            source.put("reason", "契约数据源查询失败");
        }
        Map<String, Object> runtime = new LinkedHashMap<>();
        try {
            if (accountId > 0 && accountId <= Integer.MAX_VALUE) {
                GameRuntimeClient.ContractSnapshot snapshot = gameRuntimeClient.inspectContracts(
                        (int) accountId, characNo);
                runtime.put("status", snapshot.getStatus());
                runtime.put("configuredLevel", snapshot.getConfiguredLevel());
                runtime.put("characLevel", snapshot.getCharacLevel());
                runtime.put("activePremiumTypes", snapshot.getActivePremiumTypes());
                runtime.put("levels", snapshot.getLevels());
                runtime.put("hookInstalled", snapshot.isHookInstalled());
            } else {
                runtime.put("status", "UNAVAILABLE");
                runtime.put("reason", "账号编号超出运行时协议范围");
            }
        } catch (RuntimeException exception) {
            runtime.put("status", "UNAVAILABLE");
            runtime.put("reason", compact(exception.getMessage()));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("characNo", characNo);
        result.put("accountId", accountId);
        result.put("supportedTypes", SUPPORTED_TYPES);
        result.put("contracts", contracts);
        result.put("source", source);
        result.put("runtime", runtime);
        result.put("onlineChangeNote", "在线角色修改契约后需要重新登录生效");
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> change(HttpServletRequest request, int characNo,
                                      Map<String, Object> payload) {
        if (payload == null) throw new ValidationException("契约操作参数不能为空");
        Map<String, Object> profile = profileService.basic(characNo);
        long accountId = longValue(profile.get("accountId"));
        GmOperationContext context = authorizationService.authorize(
                request, "CONTRACT", stringValue(payload.get("operation")), accountId, characNo);
        String operation = stringValue(payload.get("operation")).trim().toLowerCase();
        int preType = intValue(payload.get("preType"));
        Map<String, Object> auditPayload = auditPayload(payload);
        GmOperationAuditService.AuditRecord audit = auditService.begin(context, auditPayload);
        if (audit.isReplay()) return replay(audit);
        try {
            targetTokenService.validateAndConsume(stringValue(payload.get("targetToken")),
                    context.getOperatorUid(), accountId, characNo);
            if (!Arrays.asList("issue", "extend", "shorten", "revoke").contains(operation)) {
                throw new ValidationException("契约操作无效");
            }
            Map<String, Object> before = findTarget(payload, accountId, preType);
            Map<String, Object> after;
            if ("issue".equals(operation)) {
                after = issue(accountId, preType, payload);
            } else if ("revoke".equals(operation)) {
                after = revoke(payload, accountId, preType);
            } else {
                after = adjust(payload, accountId, preType, "extend".equals(operation));
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("requestId", context.getRequestId());
            response.put("characNo", characNo);
            response.put("accountId", accountId);
            response.put("operation", operation);
            response.put("contract", after);
            response.put("requiresRelogin", true);
            auditService.succeed(context.getRequestId(), before, after, response,
                    "DB_COMMITTED", "契约操作完成");
            return response;
        } catch (RuntimeException exception) {
            auditService.fail(context.getRequestId(), null, null, null, "FAILED", compact(exception.getMessage()));
            throw exception;
        }
    }

    Map<String, Object> auditPayload(Map<String, Object> payload) {
        Map<String, Object> result = new LinkedHashMap<>(payload);
        result.remove("targetToken");
        return result;
    }

    private Map<String, Object> issue(long accountId, int preType, Map<String, Object> payload) {
        if (!isSupportedType(preType)) throw new ValidationException("该契约类型不支持后台管理");
        int serverId = Math.max(0, intValue(payload.get("serverId")));
        int durationDays = intValue(payload.get("durationDays"));
        LocalDateTime start = parseDate(payload.get("serviceStart"), LocalDateTime.now());
        LocalDateTime end = parseDate(payload.get("serviceEnd"), start.plusDays(durationDays));
        validateContractWindow(preType, start, end);
        List<Map<String, Object>> active = jdbcTemplate.queryForList(
                "SELECT event_id AS eventId,service_start AS serviceStart,service_end AS serviceEnd " +
                        "FROM taiwan_login.member_premium WHERE m_id=? AND pre_type=? AND server_id=? " +
                        "AND service_end>=NOW() ORDER BY service_end DESC FOR UPDATE",
                accountId, preType, serverId);
        if (!active.isEmpty()) {
            Map<String, Object> row = active.get(0);
            LocalDateTime oldEnd = toDateTime(row.get("serviceEnd"));
            LocalDateTime newEnd = oldEnd != null && oldEnd.isAfter(end) ? oldEnd : end;
            jdbcTemplate.update("UPDATE taiwan_login.member_premium SET service_end=? WHERE event_id=? AND pre_type=? AND server_id=? AND m_id=? AND service_start=?",
                    Timestamp.valueOf(newEnd), longValue(row.get("eventId")), preType, serverId, accountId,
                    row.get("serviceStart"));
            return contractRow(row.get("eventId"), preType, serverId, accountId, row.get("serviceStart"), Timestamp.valueOf(newEnd));
        }
        Number nextId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(event_id),0)+1 FROM taiwan_login.member_premium FOR UPDATE", Number.class);
        long eventId = nextId == null ? 1L : nextId.longValue();
        jdbcTemplate.update("INSERT INTO taiwan_login.member_premium(event_id,pre_type,server_id,m_id,service_start,service_end) VALUES(?,?,?,?,?,?)",
                eventId, preType, serverId, accountId, Timestamp.valueOf(start), Timestamp.valueOf(end));
        return contractRow(eventId, preType, serverId, accountId, Timestamp.valueOf(start), Timestamp.valueOf(end));
    }

    private Map<String, Object> adjust(Map<String, Object> payload, long accountId, int preType, boolean extend) {
        if (!isSupportedType(preType)) throw new ValidationException("该契约类型不支持后台管理");
        long eventId = longValue(payload.get("eventId"));
        if (eventId <= 0) throw new ValidationException("契约事件编号无效");
        int days = intValue(payload.get("days"));
        if (days <= 0) throw new ValidationException("契约调整天数必须为正数");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT event_id AS eventId,pre_type AS preType,server_id AS serverId,m_id AS accountId," +
                        "service_start AS serviceStart,service_end AS serviceEnd FROM taiwan_login.member_premium " +
                        "WHERE event_id=? AND pre_type=? AND m_id=? FOR UPDATE",
                eventId, preType, accountId);
        if (rows.isEmpty()) throw new ValidationException("契约不存在");
        Map<String, Object> row = rows.get(0);
        LocalDateTime start = toDateTime(row.get("serviceStart"));
        LocalDateTime oldEnd = toDateTime(row.get("serviceEnd"));
        LocalDateTime end = extend ? oldEnd.plusDays(days) : oldEnd.minusDays(days);
        if (!end.isAfter(start)) throw new ValidationException("缩短后契约结束时间必须晚于开始时间");
        jdbcTemplate.update("UPDATE taiwan_login.member_premium SET service_end=? WHERE event_id=? AND pre_type=? AND m_id=?",
                Timestamp.valueOf(end), eventId, preType, accountId);
        return contractRow(eventId, preType, intValue(row.get("serverId")), accountId,
                row.get("serviceStart"), Timestamp.valueOf(end));
    }

    private Map<String, Object> revoke(Map<String, Object> payload, long accountId, int preType) {
        if (!isSupportedType(preType)) throw new ValidationException("该契约类型不支持后台管理");
        long eventId = longValue(payload.get("eventId"));
        if (eventId <= 0) throw new ValidationException("契约事件编号无效");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT event_id AS eventId,server_id AS serverId,service_start AS serviceStart " +
                        "FROM taiwan_login.member_premium WHERE event_id=? AND pre_type=? AND m_id=? FOR UPDATE",
                eventId, preType, accountId);
        if (rows.isEmpty()) throw new ValidationException("契约不存在");
        Timestamp end = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update("UPDATE taiwan_login.member_premium SET service_end=? WHERE event_id=? AND pre_type=? AND m_id=?",
                end, eventId, preType, accountId);
        Map<String, Object> row = rows.get(0);
        return contractRow(eventId, preType, intValue(row.get("serverId")), accountId,
                row.get("serviceStart"), end);
    }

    private Map<String, Object> findTarget(Map<String, Object> payload, long accountId, int preType) {
        long eventId = longValue(payload.get("eventId"));
        if (eventId <= 0) return Collections.emptyMap();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT event_id AS eventId,pre_type AS preType,server_id AS serverId,m_id AS accountId," +
                        "service_start AS serviceStart,service_end AS serviceEnd FROM taiwan_login.member_premium " +
                        "WHERE event_id=? AND pre_type=? AND m_id=?",
                eventId, preType, accountId);
        return rows.isEmpty() ? Collections.<String, Object>emptyMap() : rows.get(0);
    }

    private Map<String, Object> contractRow(Object eventId, int preType, int serverId, long accountId,
                                             Object start, Object end) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventId", eventId);
        result.put("preType", preType);
        result.put("serverId", serverId);
        result.put("accountId", accountId);
        result.put("serviceStart", start);
        result.put("serviceEnd", end);
        return result;
    }

    private LocalDateTime parseDate(Object value, LocalDateTime fallback) {
        if (value == null || String.valueOf(value).trim().isEmpty()) return fallback;
        String text = String.valueOf(value).trim().replace('T', ' ');
        try {
            return LocalDateTime.parse(text, DATE_TIME);
        } catch (DateTimeParseException exception) {
            throw new ValidationException("契约时间格式无效");
        }
    }

    private LocalDateTime toDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Timestamp) return ((Timestamp) value).toLocalDateTime();
        if (value instanceof java.util.Date) return new Timestamp(((java.util.Date) value).getTime()).toLocalDateTime();
        return parseDate(value, null);
    }

    private Map<String, Object> replay(GmOperationAuditService.AuditRecord audit) {
        if (audit.getResponseJson() == null) throw new ValidationException("相同请求正在处理中，请稍后查询审计状态");
        JSONObject response = JSON.parseObject(audit.getResponseJson());
        return response == null ? new LinkedHashMap<String, Object>() : response;
    }

    private long longValue(Object value) { return value instanceof Number ? ((Number) value).longValue() : 0L; }
    private int intValue(Object value) { return value instanceof Number ? ((Number) value).intValue() : parseInt(value); }
    private int parseInt(Object value) { try { return value == null ? 0 : Integer.parseInt(String.valueOf(value)); } catch (Exception e) { return 0; } }
    private String stringValue(Object value) { return value == null ? "" : String.valueOf(value); }
    private String compact(String value) { if (value == null || value.trim().isEmpty()) return "契约数据源不可用"; return value.length() <= 240 ? value : value.substring(0, 240); }
}
