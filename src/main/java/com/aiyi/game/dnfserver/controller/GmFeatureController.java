package com.aiyi.game.dnfserver.controller;

import com.aiyi.game.dnfserver.service.impl.GmFeatureService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** APIs used by the operational GM pages. */
@RestController
@RequestMapping("api/v1/gm")
public class GmFeatureController {

    @Resource
    private GmFeatureService gmFeatureService;

    @GetMapping("monitor")
    public Map<String, Object> monitor() {
        return gmFeatureService.monitor();
    }

    @GetMapping("reward/tasks")
    public List<Map<String, Object>> rewardTasks() {
        return gmFeatureService.listRewardTasks();
    }

    @PostMapping("reward/tasks")
    public Map<String, Object> saveRewardTask(@RequestBody Map<String, Object> payload) {
        return gmFeatureService.saveRewardTask(null, payload);
    }

    @PutMapping("reward/tasks/{id}")
    public Map<String, Object> updateRewardTask(@PathVariable long id, @RequestBody Map<String, Object> payload) {
        return gmFeatureService.saveRewardTask(id, payload);
    }

    @PutMapping("reward/tasks/{id}/toggle")
    public void toggleRewardTask(@PathVariable long id, @RequestParam boolean enabled) {
        gmFeatureService.toggleRewardTask(id, enabled);
    }

    @DeleteMapping("reward/tasks/{id}")
    public void deleteRewardTask(@PathVariable long id) {
        gmFeatureService.deleteRewardTask(id);
    }

    @PostMapping("reward/global")
    public Map<String, Object> sendGlobalReward(@RequestBody Map<String, Object> payload) {
        return gmFeatureService.sendGlobalReward(payload);
    }

    @GetMapping("online-reward")
    public Map<String, Object> onlineReward() {
        return gmFeatureService.getOnlineReward();
    }

    @PutMapping("online-reward")
    public Map<String, Object> saveOnlineReward(@RequestBody Map<String, Object> payload) {
        return gmFeatureService.saveOnlineReward(payload);
    }

    @PostMapping("online-reward/toggle")
    public Map<String, Object> toggleOnlineReward(@RequestParam boolean enabled) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("enabled", enabled);
        Map<String, Object> current = gmFeatureService.getOnlineReward();
        payload.put("intervalMinutes", current.get("intervalMinutes"));
        payload.put("ceraPoint", current.get("ceraPoint"));
        payload.put("gold", current.get("gold"));
        return gmFeatureService.saveOnlineReward(payload);
    }

    @GetMapping("online-reward/logs")
    public List<Map<String, Object>> onlineRewardLogs(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int pageSize) {
        return gmFeatureService.listOnlineRewardLogs(page, pageSize);
    }

    @GetMapping("mail")
    public Map<String, Object> queryMail(@RequestParam(required = false) String sender,
                                         @RequestParam(required = false) String receiver,
                                         @RequestParam(required = false) Integer itemId,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int pageSize) {
        return gmFeatureService.queryMail(sender, receiver, itemId, keyword, page, pageSize);
    }

    @GetMapping("backups")
    public Map<String, Object> backups() {
        return mapOf("entries", gmFeatureService.listBackups(), "schedules", gmFeatureService.listBackupSchedules());
    }

    @PostMapping("backups/{type}")
    public Map<String, Object> createBackup(@PathVariable String type) throws IOException, InterruptedException {
        return gmFeatureService.createBackup(type);
    }

    @PostMapping("backups/{id}/restore")
    public void restoreBackup(@PathVariable long id) throws IOException, InterruptedException {
        gmFeatureService.restoreBackup(id);
    }

    @PostMapping("backups/schedule")
    public void saveBackupSchedule(@RequestBody Map<String, Object> payload) {
        String type = String.valueOf(payload.getOrDefault("type", "PVF"));
        int interval = intValue(payload.get("intervalMinutes"), 1440);
        int retain = intValue(payload.get("retainCount"), 10);
        boolean enabled = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("enabled", "false")));
        gmFeatureService.saveBackupSchedule(type, interval, enabled, retain);
    }

    private Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        result.put(k1, v1); result.put(k2, v2); return result;
    }

    private int intValue(Object value, int fallback) {
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (Exception e) { return fallback; }
    }
}
