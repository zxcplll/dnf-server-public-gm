package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.exception.ValidationException;
import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.entity.gm.GmOperationContext;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

@Service
public class GmAuthorizationService {

    private static final int MAX_REQUEST_ID_LENGTH = 128;
    private static final int MAX_OPERATION_NAME_LENGTH = 64;

    @Resource
    private AccountVODao accountVODao;

    public AccountVO requireHighestAdmin() {
        Long operatorUid = ThreadUtil.getUserId();
        AccountVO operator = operatorUid == null ? null : accountVODao.get(operatorUid);
        if (operator == null || !operator.isAdmin()) {
            throw new ValidationException("只有最高管理员才能执行此操作");
        }
        return operator;
    }

    public GmOperationContext authorize(HttpServletRequest request,
                                        String module,
                                        String action,
                                        Long targetAccountId,
                                        Integer targetCharacNo) {
        AccountVO operator = requireHighestAdmin();
        String requestId = normalizeRequestId(ThreadUtil.getRequestId());
        String normalizedModule = requireOperationName(module, "模块");
        String normalizedAction = requireOperationName(action, "操作");
        String clientIp = request == null || request.getRemoteAddr() == null
                ? ""
                : truncate(request.getRemoteAddr().trim(), 45);
        return new GmOperationContext(
                requestId,
                operator.getUid(),
                safe(operator.getAccountname()),
                clientIp,
                normalizedModule,
                normalizedAction,
                targetAccountId,
                targetCharacNo);
    }

    private String normalizeRequestId(String requestId) {
        String normalized = requestId == null ? "" : requestId.trim();
        if (normalized.isEmpty()) {
            normalized = UUID.randomUUID().toString();
            ThreadUtil.setRequestId(normalized);
        }
        if (normalized.length() > MAX_REQUEST_ID_LENGTH ||
                !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new ValidationException("请求号格式无效");
        }
        return normalized;
    }

    private String requireOperationName(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_OPERATION_NAME_LENGTH) {
            throw new ValidationException(label + "名称无效");
        }
        return normalized;
    }

    private String safe(String value) {
        return value == null ? "" : truncate(value.trim(), 120);
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
