package com.aiyi.game.dnfserver.service.impl;

import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GmRealtimePlayerServiceTest {

    @Test
    public void keywordSearchFiltersFullBoundedSnapshotBeforePagination() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GameRuntimeClient runtime = mock(GameRuntimeClient.class);
        GmRealtimePlayerService service = new GmRealtimePlayerService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "gameRuntimeClient", runtime);

        List<GameRuntimeClient.PlayerSnapshot> players = Arrays.asList(
                player(101, 1), player(102, 2), player(103, 3));
        when(runtime.onlineSnapshot(0, 200)).thenReturn(
                new GameRuntimeClient.OnlineSnapshot(0, 200, 3, false, players));
        Map<String, Object> result = service.online("3", 0, 20);

        verify(runtime).onlineSnapshot(0, 200);
        assertEquals(1, result.get("total"));
        assertFalse((Boolean) result.get("truncated"));
        List<?> rows = (List<?>) result.get("list");
        assertEquals(1, rows.size());
        assertEquals(3, ((Map<?, ?>) rows.get(0)).get("characNo"));
    }

    private GameRuntimeClient.PlayerSnapshot player(int accountId, int characNo) {
        return new GameRuntimeClient.PlayerSnapshot(
                accountId, characNo, 3, 86, 1, 2, 100L,
                10, 156, 1, 2, 3, 4, false, null, false,
                Collections.<String>emptyList());
    }

}
