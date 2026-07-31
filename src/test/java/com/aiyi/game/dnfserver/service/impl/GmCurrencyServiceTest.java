package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.exception.ValidationException;
import org.junit.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GmCurrencyServiceTest {

    @Test
    public void rejectsUnsignedOverflowAndUnknownCurrencies() {
        GmCurrencyService service = new GmCurrencyService();
        expectRejected(service, "gold", 0xffffffffL, 1L, "范围");
        expectRejected(service, "not-a-currency", 0L, 1L, "货币");
        expectRejected(service, "gold", 10L, 0L, "不能为 0");
    }

    @Test
    public void reportsDatabaseFailureWithoutTurningUnknownBalancesIntoZero() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GmPlayerProfileService profileService = mock(GmPlayerProfileService.class);
        GameRuntimeClient runtimeClient = mock(GameRuntimeClient.class);
        GmCurrencyService service = new GmCurrencyService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "profileService", profileService);
        ReflectionTestUtils.setField(service, "gameRuntimeClient", runtimeClient);

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("accountId", 42L);
        profile.put("accountname", "account-42");
        profile.put("gold", 1200L);
        when(profileService.basic(23)).thenReturn(profile);

        Map<String, Object> inventory = new LinkedHashMap<>();
        inventory.put("gold", 1200L);
        inventory.put("coin", 3L);
        inventory.put("pay_coin", 4L);
        inventory.put("event_coin", 5L);
        inventory.put("avatar_coin", 6L);
        when(jdbcTemplate.queryForList(contains("taiwan_cain_2nd.inventory"), eq(23)))
                .thenReturn(Collections.singletonList(inventory));
        when(jdbcTemplate.queryForObject(
                contains("taiwan_billing.cash_cera"),
                any(Object[].class),
                eq(Number.class)))
                .thenThrow(new DataAccessResourceFailureException("billing unavailable"));
        when(runtimeClient.currencySnapshot(42, 23))
                .thenThrow(new IllegalStateException("runtime unavailable"));

        Map<String, Object> result = service.snapshot(23);

        Map<?, ?> sources = (Map<?, ?>) result.get("sources");
        assertEquals("UNAVAILABLE", sources.get("database"));
        assertNull(result.get("gold"));
        assertNull(result.get("cera"));
        assertNull(result.get("coin"));
    }

    @Test
    public void rejectsMutationValidationWhenBalanceSourceIsUnavailable() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GmCurrencyService service = new GmCurrencyService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        when(jdbcTemplate.queryForList(contains("taiwan_cain_2nd.inventory"), eq(23)))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("accountname", "account-42");

        try {
            ReflectionTestUtils.invokeMethod(service, "currentValue", "coin", 23, profile);
            fail("Expected unavailable balance source to be rejected");
        } catch (ValidationException expected) {
            assertTrue(expected.getMessage().contains("数据源"));
        }
    }

    private void expectRejected(GmCurrencyService service, String type, long before, long delta,
                                String expected) {
        try {
            service.validateDelta(type, before, delta);
            fail("Expected currency validation to fail");
        } catch (ValidationException exception) {
            assertTrue(exception.getMessage().contains(expected));
        }
    }
}
