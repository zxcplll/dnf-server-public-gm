package com.aiyi.game.dnfserver.service.impl;

import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GmPlayerProfileServiceTest {

    @Test
    public void combinesDatabaseProfileAndMarksRuntimeUnavailable() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GameRuntimeClient runtime = mock(GameRuntimeClient.class);
        GmPlayerProfileService service = new GmPlayerProfileService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "gameRuntimeClient", runtime);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("accountId", 18000013L);
        row.put("accountname", "account");
        row.put("characNo", 23);
        row.put("characName", "角色");
        row.put("level", 86);
        row.put("job", 1);
        row.put("growType", 2);
        row.put("gold", 1000L);
        row.put("guildId", 1);
        row.put("guildName", "公会");
        when(jdbcTemplate.queryForList(contains("FROM taiwan_cain.charac_info c"), eq(23)))
                .thenReturn(Collections.singletonList(row));
        when(runtime.findOnlineCharacter(18000013)).thenThrow(new IllegalStateException("offline"));

        Map<String, Object> profile = service.basic(23);

        assertEquals("角色", profile.get("characName"));
        assertEquals(1000L, profile.get("gold"));
        assertEquals("UNAVAILABLE", ((Map<?, ?>) profile.get("runtime")).get("status"));
        assertTrue(String.valueOf(((Map<?, ?>) profile.get("runtime")).get("reason")).contains("offline"));
    }
}
