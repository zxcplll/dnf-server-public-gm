package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.exception.ValidationException;
import com.aiyi.game.dnfserver.entity.gm.GmOperationContext;
import com.alibaba.fastjson.JSON;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.lang.reflect.Array;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@Service
public class GmOperationAuditService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private static final String SELECT_BY_REQUEST_ID =
            "SELECT id,request_id AS requestId,operator_uid AS operatorUid," +
                    "operator_name AS operatorName,client_ip AS clientIp,module,action," +
                    "target_account_id AS targetAccountId,target_charac_no AS targetCharacNo," +
                    "request_json AS requestJson,before_json AS beforeJson,after_json AS afterJson," +
                    "response_json AS responseJson,status,sync_status AS syncStatus,message," +
                    "created_at AS createdAt,completed_at AS completedAt " +
                    "FROM dnf_service.gm_operation_audit WHERE request_id=?";

    private static final String INSERT_AUDIT =
            "INSERT INTO dnf_service.gm_operation_audit " +
                    "(request_id,operator_uid,operator_name,client_ip,module,action," +
                    "target_account_id,target_charac_no,request_json,status,created_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,'PENDING',NOW())";

    @Resource
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initializeSchema() {
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource == null) {
            throw new IllegalStateException("GM operation audit datasource is not configured");
        }
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/gm_operation_audit.sql"));
        populator.setSqlScriptEncoding("UTF-8");
        populator.setContinueOnError(false);
        populator.execute(dataSource);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public AuditRecord begin(GmOperationContext context, Object requestPayload) {
        validateContext(context);
        String requestJson = toJson(requestPayload);
        AuditRecord existing = findByRequestId(context.getRequestId());
        if (existing != null) {
            requireMatchingRequest(existing, context, requestJson);
            return existing.withReplay(true);
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            int inserted = jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        INSERT_AUDIT, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, context.getRequestId());
                statement.setLong(2, context.getOperatorUid());
                statement.setString(3, truncate(context.getOperatorName(), 120));
                statement.setString(4, truncate(context.getClientIp(), 45));
                statement.setString(5, truncate(context.getModule(), 64));
                statement.setString(6, truncate(context.getAction(), 64));
                statement.setObject(7, context.getTargetAccountId());
                statement.setObject(8, context.getTargetCharacNo());
                statement.setString(9, requestJson);
                return statement;
            }, keyHolder);
            if (inserted != 1) {
                throw new ValidationException("审计记录创建失败");
            }
        } catch (DuplicateKeyException duplicate) {
            AuditRecord raced = findByRequestId(context.getRequestId());
            if (raced != null) {
                requireMatchingRequest(raced, context, requestJson);
                return raced.withReplay(true);
            }
            throw duplicate;
        }

        Number generatedKey = keyHolder.getKey();
        long id = generatedKey == null ? 0L : generatedKey.longValue();
        if (id == 0L) {
            AuditRecord inserted = findByRequestId(context.getRequestId());
            if (inserted != null) {
                return inserted;
            }
        }
        return AuditRecord.pending(id, context, requestJson);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public AuditRecord succeed(String requestId,
                               Object beforeState,
                               Object afterState,
                               Object response,
                               String syncStatus,
                               String message) {
        return complete(
                requestId, STATUS_SUCCESS, beforeState, afterState, response, syncStatus, message);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public AuditRecord fail(String requestId,
                            Object beforeState,
                            Object afterState,
                            Object response,
                            String syncStatus,
                            String message) {
        return complete(
                requestId, STATUS_FAILED, beforeState, afterState, response, syncStatus, message);
    }

    public AuditRecord findByRequestId(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return null;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                SELECT_BY_REQUEST_ID, requestId.trim());
        return rows.isEmpty() ? null : mapRecord(rows.get(0), false);
    }

    private AuditRecord complete(String requestId,
                                 String status,
                                 Object beforeState,
                                 Object afterState,
                                 Object response,
                                 String syncStatus,
                                 String message) {
        String normalizedRequestId = requireRequestId(requestId);
        String sql = "UPDATE dnf_service.gm_operation_audit SET before_json=?,after_json=?," +
                "response_json=?,status='" + status + "',sync_status=?,message=?,completed_at=NOW() " +
                "WHERE request_id=? AND status='PENDING'";
        int updated = jdbcTemplate.update(
                sql,
                toJson(beforeState),
                toJson(afterState),
                toJson(response),
                truncate(syncStatus, 32),
                truncate(message, 1000),
                normalizedRequestId);
        AuditRecord record = findByRequestId(normalizedRequestId);
        if (record == null) {
            throw new ValidationException("审计记录不存在");
        }
        if (updated == 0 && !status.equals(record.getStatus())) {
            throw new ValidationException("审计记录状态已变化");
        }
        return updated == 0 ? record.withReplay(true) : record;
    }

    private void validateContext(GmOperationContext context) {
        if (context == null) {
            throw new ValidationException("操作上下文不能为空");
        }
        requireRequestId(context.getRequestId());
        if (context.getOperatorUid() <= 0 || isBlank(context.getModule()) || isBlank(context.getAction())) {
            throw new ValidationException("操作上下文无效");
        }
    }

    private String requireRequestId(String requestId) {
        String normalized = requestId == null ? "" : requestId.trim();
        if (normalized.isEmpty() || normalized.length() > 128 ||
                !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new ValidationException("请求号格式无效");
        }
        return normalized;
    }

    private String toJson(Object value) {
        return value == null ? null : JSON.toJSONString(canonicalize(value));
    }

    private Object canonicalize(Object value) {
        if (value instanceof Map) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), canonicalize(entry.getValue()));
            }
            return sorted;
        }
        if (value instanceof Iterable) {
            List<Object> ordered = new ArrayList<>();
            for (Object element : (Iterable<?>) value) {
                ordered.add(canonicalize(element));
            }
            return ordered;
        }
        if (value != null && value.getClass().isArray()) {
            List<Object> ordered = new ArrayList<>();
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                ordered.add(canonicalize(Array.get(value, index)));
            }
            return ordered;
        }
        return value;
    }

    private void requireMatchingRequest(AuditRecord existing,
                                        GmOperationContext context,
                                        String requestJson) {
        boolean matches = existing.getOperatorUid() == context.getOperatorUid()
                && Objects.equals(existing.getModule(), context.getModule())
                && Objects.equals(existing.getAction(), context.getAction())
                && Objects.equals(existing.getTargetAccountId(), context.getTargetAccountId())
                && Objects.equals(existing.getTargetCharacNo(), context.getTargetCharacNo())
                && Objects.equals(existing.getRequestJson(), requestJson);
        if (!matches) {
            throw new ValidationException("请求号冲突：已用于其他操作或请求内容");
        }
    }

    private AuditRecord mapRecord(Map<String, Object> row, boolean replay) {
        return new AuditRecord(
                longValue(row.get("id")),
                stringValue(row.get("requestId")),
                longValue(row.get("operatorUid")),
                stringValue(row.get("operatorName")),
                stringValue(row.get("clientIp")),
                stringValue(row.get("module")),
                stringValue(row.get("action")),
                nullableLong(row.get("targetAccountId")),
                nullableInteger(row.get("targetCharacNo")),
                stringValue(row.get("requestJson")),
                stringValue(row.get("beforeJson")),
                stringValue(row.get("afterJson")),
                stringValue(row.get("responseJson")),
                stringValue(row.get("status")),
                stringValue(row.get("syncStatus")),
                stringValue(row.get("message")),
                row.get("createdAt"),
                row.get("completedAt"),
                replay);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private long longValue(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private Integer nullableInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public static final class AuditRecord {
        private final long id;
        private final String requestId;
        private final long operatorUid;
        private final String operatorName;
        private final String clientIp;
        private final String module;
        private final String action;
        private final Long targetAccountId;
        private final Integer targetCharacNo;
        private final String requestJson;
        private final String beforeJson;
        private final String afterJson;
        private final String responseJson;
        private final String status;
        private final String syncStatus;
        private final String message;
        private final Object createdAt;
        private final Object completedAt;
        private final boolean replay;

        private AuditRecord(long id,
                            String requestId,
                            long operatorUid,
                            String operatorName,
                            String clientIp,
                            String module,
                            String action,
                            Long targetAccountId,
                            Integer targetCharacNo,
                            String requestJson,
                            String beforeJson,
                            String afterJson,
                            String responseJson,
                            String status,
                            String syncStatus,
                            String message,
                            Object createdAt,
                            Object completedAt,
                            boolean replay) {
            this.id = id;
            this.requestId = requestId;
            this.operatorUid = operatorUid;
            this.operatorName = operatorName;
            this.clientIp = clientIp;
            this.module = module;
            this.action = action;
            this.targetAccountId = targetAccountId;
            this.targetCharacNo = targetCharacNo;
            this.requestJson = requestJson;
            this.beforeJson = beforeJson;
            this.afterJson = afterJson;
            this.responseJson = responseJson;
            this.status = status;
            this.syncStatus = syncStatus;
            this.message = message;
            this.createdAt = createdAt;
            this.completedAt = completedAt;
            this.replay = replay;
        }

        private static AuditRecord pending(long id,
                                           GmOperationContext context,
                                           String requestJson) {
            return new AuditRecord(
                    id,
                    context.getRequestId(),
                    context.getOperatorUid(),
                    context.getOperatorName(),
                    context.getClientIp(),
                    context.getModule(),
                    context.getAction(),
                    context.getTargetAccountId(),
                    context.getTargetCharacNo(),
                    requestJson,
                    null,
                    null,
                    null,
                    STATUS_PENDING,
                    null,
                    null,
                    null,
                    null,
                    false);
        }

        private AuditRecord withReplay(boolean replay) {
            return new AuditRecord(
                    id, requestId, operatorUid, operatorName, clientIp, module, action,
                    targetAccountId, targetCharacNo, requestJson, beforeJson, afterJson,
                    responseJson, status, syncStatus, message, createdAt, completedAt, replay);
        }

        public long getId() {
            return id;
        }

        public String getRequestId() {
            return requestId;
        }

        public long getOperatorUid() {
            return operatorUid;
        }

        public String getOperatorName() {
            return operatorName;
        }

        public String getClientIp() {
            return clientIp;
        }

        public String getModule() {
            return module;
        }

        public String getAction() {
            return action;
        }

        public Long getTargetAccountId() {
            return targetAccountId;
        }

        public Integer getTargetCharacNo() {
            return targetCharacNo;
        }

        public String getRequestJson() {
            return requestJson;
        }

        public String getBeforeJson() {
            return beforeJson;
        }

        public String getAfterJson() {
            return afterJson;
        }

        public String getResponseJson() {
            return responseJson;
        }

        public String getStatus() {
            return status;
        }

        public String getSyncStatus() {
            return syncStatus;
        }

        public String getMessage() {
            return message;
        }

        public Object getCreatedAt() {
            return createdAt;
        }

        public Object getCompletedAt() {
            return completedAt;
        }

        public boolean isReplay() {
            return replay;
        }
    }
}
