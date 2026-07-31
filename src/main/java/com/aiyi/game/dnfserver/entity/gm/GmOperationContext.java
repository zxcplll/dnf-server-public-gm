package com.aiyi.game.dnfserver.entity.gm;

/** Immutable identity and target metadata for one privileged GM operation. */
public final class GmOperationContext {

    private final String requestId;
    private final long operatorUid;
    private final String operatorName;
    private final String clientIp;
    private final String module;
    private final String action;
    private final Long targetAccountId;
    private final Integer targetCharacNo;

    public GmOperationContext(String requestId,
                              long operatorUid,
                              String operatorName,
                              String clientIp,
                              String module,
                              String action,
                              Long targetAccountId,
                              Integer targetCharacNo) {
        this.requestId = requestId;
        this.operatorUid = operatorUid;
        this.operatorName = operatorName;
        this.clientIp = clientIp;
        this.module = module;
        this.action = action;
        this.targetAccountId = targetAccountId;
        this.targetCharacNo = targetCharacNo;
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
}
