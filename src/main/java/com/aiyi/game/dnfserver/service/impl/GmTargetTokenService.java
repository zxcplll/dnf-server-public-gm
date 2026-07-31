package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.exception.ValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GmTargetTokenService {

    private final Map<String, TokenEntry> tokens = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;
    private final long ttlMillis;

    @Autowired
    public GmTargetTokenService(@Value("${dnf.gm.target-token-ttl-ms:300000}") long ttlMillis) {
        this(Clock.systemUTC(), ttlMillis);
    }

    GmTargetTokenService(Clock clock, long ttlMillis) {
        if (ttlMillis < 1000L || ttlMillis > 1800000L) {
            throw new IllegalArgumentException("Target token TTL must be between 1 second and 30 minutes");
        }
        this.clock = clock;
        this.ttlMillis = ttlMillis;
    }

    public TargetToken issue(long operatorId, long accountId, int characNo) {
        if (operatorId <= 0 || accountId <= 0 || characNo <= 0) {
            throw new ValidationException("目标令牌参数无效");
        }
        cleanupExpired();
        byte[] random = new byte[24];
        secureRandom.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        long expiresAt = clock.millis() + ttlMillis;
        tokens.put(token, new TokenEntry(operatorId, accountId, characNo, expiresAt));
        return new TargetToken(token, accountId, characNo, expiresAt);
    }

    public void validateAndConsume(String token, long operatorId, long accountId, int characNo) {
        if (token == null || token.trim().isEmpty()) {
            throw new ValidationException("目标令牌不能为空");
        }
        TokenEntry entry = tokens.get(token);
        if (entry == null) {
            throw new ValidationException("目标令牌无效");
        }
        synchronized (entry) {
            if (entry.used) {
                throw new ValidationException("目标令牌已使用");
            }
            if (clock.millis() > entry.expiresAt) {
                tokens.remove(token, entry);
                throw new ValidationException("目标令牌已过期");
            }
            if (entry.operatorId != operatorId || entry.accountId != accountId || entry.characNo != characNo) {
                throw new ValidationException("目标令牌与当前操作目标不一致");
            }
            entry.used = true;
        }
    }

    private void cleanupExpired() {
        long now = clock.millis();
        for (Map.Entry<String, TokenEntry> entry : tokens.entrySet()) {
            if (entry.getValue().expiresAt < now) {
                tokens.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private static final class TokenEntry {
        private final long operatorId;
        private final long accountId;
        private final int characNo;
        private final long expiresAt;
        private boolean used;

        private TokenEntry(long operatorId, long accountId, int characNo, long expiresAt) {
            this.operatorId = operatorId;
            this.accountId = accountId;
            this.characNo = characNo;
            this.expiresAt = expiresAt;
        }
    }

    public static final class TargetToken {
        private final String token;
        private final long accountId;
        private final int characNo;
        private final long expiresAt;

        private TargetToken(String token, long accountId, int characNo, long expiresAt) {
            this.token = token;
            this.accountId = accountId;
            this.characNo = characNo;
            this.expiresAt = expiresAt;
        }

        public String getToken() {
            return token;
        }

        public long getAccountId() {
            return accountId;
        }

        public int getCharacNo() {
            return characNo;
        }

        public long getExpiresAt() {
            return expiresAt;
        }
    }
}
