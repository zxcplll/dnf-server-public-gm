package com.aiyi.game.dnfserver.controller;

import com.aiyi.game.dnfserver.service.impl.GmAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/v1/gm/analytics")
public class GmAnalyticsController {

    @Resource
    private GmAnalyticsService analyticsService;

    @GetMapping("metrics")
    public List<Map<String, Object>> metrics() {
        return analyticsService.metricOptions();
    }

    @GetMapping("report")
    public Map<String, Object> report(@RequestParam String metric,
                                      @RequestParam(defaultValue = "none") String groupBy,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return analyticsService.report(metric, groupBy, start, end);
    }

    @GetMapping(value = "export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> export(@RequestParam String metric,
                                         @RequestParam(defaultValue = "none") String groupBy,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        String csv = "\ufeff" + analyticsService.toCsv(analyticsService.report(metric, groupBy, start, end));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=gm-analytics-" + start + "-" + end + ".csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
