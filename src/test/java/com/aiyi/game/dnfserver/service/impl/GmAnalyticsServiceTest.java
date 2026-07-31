package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.exception.ValidationException;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GmAnalyticsServiceTest {

    @Test
    public void rejectsUnknownMetricsAndRangesOver366Days() {
        GmAnalyticsService service = new GmAnalyticsService();
        expectRejected(service, "raw_sql", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), "指标");
        expectRejected(service, "active_accounts", LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 2), "366");
    }

    @Test
    public void reportUsesWhitelistedParameterizedQuery() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GmAnalyticsService service = new GmAnalyticsService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("date", "2026-07-31");
        point.put("value", 3L);
        when(jdbcTemplate.queryForList(
                contains("FROM taiwan_login.member_play_info"),
                any(java.sql.Date.class),
                any(java.sql.Date.class)))
                .thenReturn(Collections.singletonList(point));

        Map<String, Object> report = service.report("active_accounts", "none",
                LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 31));

        assertTrue(String.valueOf(report.get("sourceStatus")).contains("AVAILABLE"));
        assertTrue(String.valueOf(report.get("summary")).contains("3"));
    }

    @Test
    public void csvEscapesQuotesNewlinesAndSpreadsheetFormulas() {
        GmAnalyticsService service = new GmAnalyticsService();
        Map<String, Object> report = new LinkedHashMap<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("date", "2026-07-31");
        row.put("label", "=HYPERLINK(\"bad\")\nnext");
        row.put("value", 3);
        report.put("trend", Collections.singletonList(row));

        String csv = service.toCsv(report);

        assertTrue(csv.startsWith("date,label,value"));
        assertTrue(csv.contains("'="));
        assertTrue(csv.contains("\"\"bad\"\""));
    }

    private void expectRejected(GmAnalyticsService service, String metric, LocalDate start,
                                LocalDate end, String expected) {
        try {
            service.report(metric, "none", start, end);
            fail("Expected analytics request to be rejected");
        } catch (ValidationException exception) {
            assertTrue(exception.getMessage().contains(expected));
        }
    }
}
