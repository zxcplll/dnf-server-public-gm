package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.exception.ValidationException;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class GmTargetTokenServiceTest {

    @Test
    public void tokenIsBoundToOperatorAccountCharacterAndSingleUse() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-31T04:00:00Z"));
        GmTargetTokenService service = new GmTargetTokenService(clock, 300000L);

        GmTargetTokenService.TargetToken issued = service.issue(100L, 18000013L, 23);

        assertNotNull(issued.getToken());
        assertEquals(23, issued.getCharacNo());
        service.validateAndConsume(issued.getToken(), 100L, 18000013L, 23);
        expectRejected(service, issued.getToken(), 100L, 18000013L, 23, "已使用");
    }

    @Test
    public void rejectsMismatchedAndExpiredTargets() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-31T04:00:00Z"));
        GmTargetTokenService service = new GmTargetTokenService(clock, 1000L);
        GmTargetTokenService.TargetToken issued = service.issue(100L, 18000013L, 23);

        expectRejected(service, issued.getToken(), 100L, 18000013L, 24, "目标");
        clock.advanceMillis(1001L);
        expectRejected(service, issued.getToken(), 100L, 18000013L, 23, "过期");
    }

    private void expectRejected(GmTargetTokenService service, String token, long operatorId,
                                long accountId, int characNo, String message) {
        try {
            service.validateAndConsume(token, operatorId, accountId, characNo);
            fail("Expected target token to be rejected");
        } catch (ValidationException expected) {
            if (!expected.getMessage().contains(message)) {
                fail("Unexpected validation message: " + expected.getMessage());
            }
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceMillis(long millis) {
            instant = instant.plusMillis(millis);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
