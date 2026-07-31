package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.exception.ValidationException;
import com.aiyi.game.dnfserver.entity.gm.GmOperationContext;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GmCurrencyService {

    public static final long MAX_UNSIGNED_INT = 0xffffffffL;
    private static final long MAX_SIGNED_INT = 0x7fffffffL;
    private static final Map<String, String> CURRENCY_COLUMNS = currencyColumns();

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

    public List<Map<String, Object>> currencyOptions() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String key : Arrays.asList("gold", "cera", "cera_point", "coin", "pay_coin",
                "event_coin", "avatar_coin", "win_point")) {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("value", key);
            option.put("label", label(key));
            option.put("onlineMode", "gold".equals(key) ? "RUNTIME" : "OFFLINE_ONLY");
            option.put("deltaOnly", true);
            result.add(option);
        }
        return result;
    }

    public Map<String, Object> snapshot(int characNo) {
        Map<String, Object> profile = profileService.basic(characNo);
        long accountId = longValue(profile.get("accountId"));
        String accountname = stringValue(profile.get("accountname"));
        Map<String, Object> result = dbSnapshot(characNo, accountname);
        result.put("characNo", characNo);
        result.put("accountId", accountId);
        result.put("accountname", accountname);
        Map<String, Object> runtime = new LinkedHashMap<>();
        try {
            if (accountId > 0 && accountId <= Integer.MAX_VALUE) {
                GameRuntimeClient.CurrencySnapshot current = gameRuntimeClient.currencySnapshot(
                        (int) accountId, characNo);
                boolean complete = current.getUnavailable() == null || current.getUnavailable().isEmpty();
                runtime.put("status", complete ? "AVAILABLE" : "DEGRADED");
                runtime.put("gold", current.getGold());
                runtime.put("cera", current.getCera());
                runtime.put("ceraPoint", current.getCeraPoint());
                runtime.put("winPoint", current.getWinPoint());
                if (current.getGold() != null) result.put("gold", current.getGold());
                if (current.getCera() != null) result.put("cera", current.getCera());
                if (current.getCeraPoint() != null) result.put("cera_point", current.getCeraPoint());
                if (current.getWinPoint() != null) result.put("win_point", current.getWinPoint());
                if (!complete) runtime.put("reason", "部分运行时货币不可用");
            } else {
                runtime.put("status", "UNAVAILABLE");
                runtime.put("reason", "账号编号超出运行时协议范围");
            }
        } catch (RuntimeException exception) {
            runtime.put("status", "UNAVAILABLE");
            runtime.put("reason", compact(exception.getMessage()));
        }
        result.put("runtime", runtime);
        result.put("sources", sourceMap(runtime.get("status"), result.get("dbStatus")));
        result.remove("dbStatus");
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> change(HttpServletRequest request, int characNo,
                                      Map<String, Object> payload) {
        if (payload == null) {
            throw new ValidationException("货币变更参数不能为空");
        }
        Map<String, Object> profile = profileService.basic(characNo);
        long accountId = longValue(profile.get("accountId"));
        GmOperationContext context = authorizationService.authorize(
                request, "CURRENCY", "CHANGE", accountId, characNo);
        String currency = stringValue(payload.get("currency")).trim();
        long delta = longValue(payload.get("delta"));
        validateDelta(currency, 0L, delta);
        String targetToken = stringValue(payload.get("targetToken"));
        Map<String, Object> auditPayload = new LinkedHashMap<>(payload);
        auditPayload.remove("targetToken");
        auditPayload.put("currency", currency);
        auditPayload.put("delta", delta);
        GmOperationAuditService.AuditRecord audit = auditService.begin(context, auditPayload);
        if (audit.isReplay()) {
            return replay(audit);
        }
        try {
            targetTokenService.validateAndConsume(targetToken, context.getOperatorUid(), accountId, characNo);
            OnlineState online = onlineState(accountId, characNo);
            if (online.unavailable) {
                throw new ValidationException("运行时状态不可用，暂不执行货币修改");
            }
            validateDelta(currency, currentValue(currency, characNo, profile), delta);
            Map<String, Object> before = snapshotForAudit(currency, characNo, profile);
            Map<String, Object> after;
            if (online.online && "gold".equals(currency)) {
                if (accountId > Integer.MAX_VALUE) {
                    throw new ValidationException("账号编号超出运行时协议范围");
                }
                GameRuntimeClient.GoldChange runtimeChange = gameRuntimeClient.changeGold(
                        context.getRequestId(), (int) accountId, characNo, delta);
                after = new LinkedHashMap<>();
                after.put("currency", currency);
                after.put("before", runtimeChange.getBefore());
                after.put("delta", runtimeChange.getDelta());
                after.put("after", runtimeChange.getAfter());
                after.put("syncStatus", "RUNTIME_VERIFIED");
            } else {
                if (online.online) {
                    throw new ValidationException("角色在线时仅允许修改金币");
                }
                after = updateOffline(currency, characNo, stringValue(profile.get("accountname")), delta);
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("requestId", context.getRequestId());
            response.put("characNo", characNo);
            response.put("accountId", accountId);
            response.putAll(after);
            auditService.succeed(context.getRequestId(), before, after, response,
                    stringValue(after.get("syncStatus")), "货币变更完成");
            return response;
        } catch (RuntimeException exception) {
            auditService.fail(context.getRequestId(), null, null, null, "FAILED", compact(exception.getMessage()));
            throw exception;
        }
    }

    void validateDelta(String currency, long before, long delta) {
        if (!CURRENCY_COLUMNS.containsKey(currency)) {
            throw new ValidationException("货币类型无效");
        }
        if (delta == 0) {
            throw new ValidationException("货币变更数量不能为 0");
        }
        long max = "win_point".equals(currency) ? MAX_SIGNED_INT : MAX_UNSIGNED_INT;
        if (before < 0 || before > max || delta > max || delta < -max) {
            throw new ValidationException("货币变更超出允许范围");
        }
        long after;
        try {
            after = Math.addExact(before, delta);
        } catch (ArithmeticException exception) {
            throw new ValidationException("货币变更超出允许范围");
        }
        if (after < 0 || after > max) {
            throw new ValidationException("货币变更超出允许范围");
        }
    }

    private Map<String, Object> dbSnapshot(int characNo, String accountname) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> inventory = jdbcTemplate.queryForList(
                    "SELECT money AS gold,coin,pay_coin AS pay_coin,event_coin AS event_coin," +
                            "avatar_coin AS avatar_coin FROM taiwan_cain_2nd.inventory WHERE charac_no=?",
                    characNo);
            if (!inventory.isEmpty()) {
                Map<String, Object> row = inventory.get(0);
                result.put("gold", nullableLong(row.get("gold")));
                result.put("coin", nullableLong(row.get("coin")));
                result.put("pay_coin", nullableLong(row.get("pay_coin")));
                result.put("event_coin", nullableLong(row.get("event_coin")));
                result.put("avatar_coin", nullableLong(row.get("avatar_coin")));
            }
            result.put("cera", firstValue("SELECT cera FROM taiwan_billing.cash_cera WHERE account=?", accountname));
            result.put("cera_point", firstValue("SELECT cera_point FROM taiwan_billing.cash_cera_point WHERE account=?", accountname));
            result.put("win_point", firstValue("SELECT win_point FROM taiwan_cain.pvp_result WHERE charac_no=?", characNo));
            result.put("dbStatus", "AVAILABLE");
        } catch (DataAccessException exception) {
            result.clear();
            result.put("dbStatus", "UNAVAILABLE");
            result.put("reason", "货币数据源查询失败");
        }
        for (String key : CURRENCY_COLUMNS.keySet()) {
            if (!result.containsKey(key)) result.put(key, null);
        }
        return result;
    }

    private long currentValue(String currency, int characNo, Map<String, Object> profile) {
        Map<String, Object> current = dbSnapshot(characNo, stringValue(profile.get("accountname")));
        Object value = current.get(currency);
        if (!"AVAILABLE".equals(current.get("dbStatus")) || !(value instanceof Number)) {
            throw new ValidationException("货币余额数据源不可用，请稍后重试");
        }
        return ((Number) value).longValue();
    }

    private Map<String, Object> snapshotForAudit(String currency, int characNo, Map<String, Object> profile) {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("currency", currency);
        before.put("value", currentValue(currency, characNo, profile));
        return before;
    }

    private Map<String, Object> updateOffline(String currency, int characNo, String accountname, long delta) {
        Map<String, Object> result = new LinkedHashMap<>();
        if ("cera".equals(currency) || "cera_point".equals(currency)) {
            String table = "cera".equals(currency) ? "taiwan_billing.cash_cera" : "taiwan_billing.cash_cera_point";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT " + CURRENCY_COLUMNS.get(currency) + " AS value FROM " + table +
                            " WHERE account=? FOR UPDATE", accountname);
            long before = rows.isEmpty() ? 0L : longValue(rows.get(0).get("value"));
            validateDelta(currency, before, delta);
            long after = before + delta;
            if (rows.isEmpty()) {
                if ("cera".equals(currency)) {
                    jdbcTemplate.update("INSERT INTO taiwan_billing.cash_cera(account,cera,cera_cold,mod_tran,mod_date,reg_date) VALUES(?,?,0,0,NOW(),NOW())",
                            accountname, after);
                } else {
                    jdbcTemplate.update("INSERT INTO taiwan_billing.cash_cera_point(account,cera_point,reg_date,mod_date) VALUES(?,?,NOW(),NOW())",
                            accountname, after);
                }
            } else {
                jdbcTemplate.update("UPDATE " + table + " SET " + CURRENCY_COLUMNS.get(currency) + "=?,mod_date=NOW() WHERE account=?",
                        after, accountname);
            }
            result.put("before", before);
            result.put("delta", delta);
            result.put("after", after);
            result.put("syncStatus", "DB_COMMITTED");
            return result;
        }
        if ("win_point".equals(currency)) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT win_point AS value FROM taiwan_cain.pvp_result WHERE charac_no=? FOR UPDATE", characNo);
            long before = rows.isEmpty() ? 0L : longValue(rows.get(0).get("value"));
            validateDelta(currency, before, delta);
            long after = before + delta;
            if (rows.isEmpty()) {
                jdbcTemplate.update("INSERT INTO taiwan_cain.pvp_result(charac_no,win_point) VALUES(?,?)", characNo, after);
            } else {
                jdbcTemplate.update("UPDATE taiwan_cain.pvp_result SET win_point=? WHERE charac_no=?", after, characNo);
            }
            result.put("before", before);
            result.put("delta", delta);
            result.put("after", after);
            result.put("syncStatus", "DB_COMMITTED");
            return result;
        }
        String column = CURRENCY_COLUMNS.get(currency);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + column + " AS value FROM taiwan_cain_2nd.inventory WHERE charac_no=? FOR UPDATE", characNo);
        if (rows.isEmpty()) throw new ValidationException("角色背包不存在");
        long before = longValue(rows.get(0).get("value"));
        validateDelta(currency, before, delta);
        long after = before + delta;
        int updated = jdbcTemplate.update("UPDATE taiwan_cain_2nd.inventory SET " + column + "=? WHERE charac_no=? AND " + column + "=?",
                after, characNo, before);
        if (updated != 1) throw new ValidationException("货币余额已变化，请刷新后重试");
        result.put("before", before);
        result.put("delta", delta);
        result.put("after", after);
        result.put("syncStatus", "DB_COMMITTED");
        return result;
    }

    private OnlineState onlineState(long accountId, int characNo) {
        if (accountId <= 0 || accountId > Integer.MAX_VALUE) {
            return new OnlineState(false, true);
        }
        try {
            GameRuntimeClient.OnlineCharacter online = gameRuntimeClient.findOnlineCharacter((int) accountId);
            return new OnlineState(true, online.getCharacNo() != characNo);
        } catch (RuntimeException exception) {
            String message = compact(exception.getMessage()).toLowerCase();
            if (message.contains("not online") || message.contains("not found") || message.contains("no user") ||
                    message.contains("不存在") || message.contains("不在线")) {
                return new OnlineState(false, false);
            }
            return new OnlineState(false, true);
        }
    }

    private Map<String, Object> replay(GmOperationAuditService.AuditRecord audit) {
        if (audit.getResponseJson() == null) {
            throw new ValidationException("相同请求正在处理中，请稍后查询审计状态");
        }
        JSONObject response = JSON.parseObject(audit.getResponseJson());
        return response == null ? new LinkedHashMap<String, Object>() : response;
    }

    private Map<String, Object> sourceMap(Object runtime, Object db) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runtime", runtime);
        result.put("database", db);
        return result;
    }

    private Long firstValue(String sql, Object arg) {
        try {
            Number value = jdbcTemplate.queryForObject(sql, new Object[]{arg}, Number.class);
            return value == null ? null : value.longValue();
        } catch (EmptyResultDataAccessException exception) {
            return 0L;
        }
    }

    private static Map<String, String> currencyColumns() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("gold", "money");
        result.put("coin", "coin");
        result.put("pay_coin", "pay_coin");
        result.put("event_coin", "event_coin");
        result.put("avatar_coin", "avatar_coin");
        result.put("cera", "cera");
        result.put("cera_point", "cera_point");
        result.put("win_point", "win_point");
        return result;
    }

    private String label(String value) {
        if ("gold".equals(value)) return "金币";
        if ("cera".equals(value)) return "点券";
        if ("cera_point".equals(value)) return "代币券";
        if ("coin".equals(value)) return "复活币";
        if ("pay_coin".equals(value)) return "付费硬币";
        if ("event_coin".equals(value)) return "活动币";
        if ("avatar_coin".equals(value)) return "时装币";
        return "PVP 胜点";
    }

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private Long nullableLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String compact(String value) {
        if (value == null || value.trim().isEmpty()) return "运行时数据源不可用";
        return value.length() <= 240 ? value : value.substring(0, 240);
    }

    private static final class OnlineState {
        private final boolean online;
        private final boolean unavailable;

        private OnlineState(boolean online, boolean unavailable) {
            this.online = online;
            this.unavailable = unavailable;
        }
    }
}
