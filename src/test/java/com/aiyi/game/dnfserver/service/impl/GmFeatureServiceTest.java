package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.game.dnfserver.service.PostalService;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class GmFeatureServiceTest {

    @Test
    public void sendsOnlineRewardGoldDirectlyToInventoryWithoutMail() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PostalService postalService = mock(PostalService.class);
        GmFeatureService service = new GmFeatureService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "postalService", postalService);
        when(jdbcTemplate.update(anyString(), eq(500), eq(1))).thenReturn(1);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetType", "CHARACTERS");
        payload.put("characterIds", Arrays.asList(1));
        payload.put("items", Collections.emptyList());
        payload.put("gold", 500);
        payload.put("ceraPoint", 0);
        payload.put("directGold", true);

        Map<String, Object> result = service.dispatchReward(payload, "online-reward");

        verify(jdbcTemplate).update(
                "UPDATE taiwan_cain_2nd.inventory SET money=LEAST(4294967295, money+?) WHERE charac_no=?",
                500,
                1);
        verifyZeroInteractions(postalService);
        assertEquals(0, result.get("mailCount"));
    }

    @Test
    public void sendsOnlineRewardCeraDirectlyToCashCeraWithoutTokenVoucher() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PostalService postalService = mock(PostalService.class);
        GmFeatureService service = new GmFeatureService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "postalService", postalService);
        when(jdbcTemplate.update(anyString(), eq(120), eq(1))).thenReturn(1);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetType", "CHARACTERS");
        payload.put("characterIds", Arrays.asList(1));
        payload.put("items", Collections.emptyList());
        payload.put("gold", 0);
        payload.put("ceraPoint", 120);
        payload.put("directCera", true);

        Map<String, Object> result = service.dispatchReward(payload, "online-reward");

        verify(jdbcTemplate).update(
                "UPDATE taiwan_billing.cash_cera p JOIN taiwan_cain.charac_info c ON CAST(p.account AS UNSIGNED)=c.m_id SET p.cera=LEAST(4294967295, p.cera+?),p.mod_date=NOW() WHERE c.charac_no=?",
                120,
                1);
        verifyZeroInteractions(postalService);
        assertEquals(0, result.get("mailCount"));
    }
}
