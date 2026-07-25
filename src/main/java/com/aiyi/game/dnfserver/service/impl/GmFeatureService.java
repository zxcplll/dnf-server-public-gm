package com.aiyi.game.dnfserver.service.impl;

import com.alibaba.fastjson.JSON;
import com.aiyi.game.dnfserver.entity.Postal;
import com.aiyi.game.dnfserver.entity.common.Item;
import com.aiyi.game.dnfserver.pvf.PvfCache;
import com.aiyi.game.dnfserver.pvf.PvfManager;
import com.aiyi.game.dnfserver.service.PostalService;
import com.aiyi.game.dnfserver.utils.ChinaseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Shared backend for operational GM functions. */
@Service
public class GmFeatureService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GmFeatureService.class);
    private static final String[] BACKUP_SCHEMAS = {
            "taiwan_cain", "taiwan_cain_2nd", "d_taiwan", "taiwan_login", "taiwan_billing", "dnf_service"
    };
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private PostalService postalService;
    @Resource
    private PvfManager pvfManager;

    private final Deque<Map<String, Object>> metricHistory = new ConcurrentLinkedDeque<>();
    private long lastRx;
    private long lastTx;
    private long lastNetworkAt;

    @PostConstruct
    public void init() {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS dnf_service.gm_reward_task (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(120) NOT NULL, " +
                    "interval_minutes INT NOT NULL DEFAULT 60, enabled TINYINT NOT NULL DEFAULT 0, " +
                    "target_type VARCHAR(20) NOT NULL DEFAULT 'ALL', payload_json LONGTEXT NOT NULL, " +
                    "next_run_at DATETIME NULL, last_run_at DATETIME NULL, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL)");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS dnf_service.gm_backup_entry (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT, type VARCHAR(12) NOT NULL, path VARCHAR(500) NOT NULL, " +
                    "size_bytes BIGINT NOT NULL DEFAULT 0, status VARCHAR(20) NOT NULL, created_at DATETIME NOT NULL, " +
                    "message VARCHAR(500) NULL)");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS dnf_service.gm_backup_schedule (" +
                    "type VARCHAR(12) PRIMARY KEY, interval_minutes INT NOT NULL DEFAULT 1440, enabled TINYINT NOT NULL DEFAULT 0, " +
                    "retain_count INT NOT NULL DEFAULT 10, next_run_at DATETIME NULL, updated_at DATETIME NOT NULL)");
        } catch (Exception e) {
            LOGGER.warn("GM feature tables were not initialized: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 15000L)
    public void tick() {
        recordMetrics();
        dispatchDueTasks();
        processBackupSchedules();
    }

    public Map<String, Object> monitor() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> current = metricHistory.peekLast();
        if (current == null) current = recordMetrics();
        result.put("current", current);
        result.put("history", new ArrayList<>(metricHistory));
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalAccounts", scalar("SELECT COUNT(*) FROM d_taiwan.accounts"));
        overview.put("todayRegistrations", scalar("SELECT COUNT(*) FROM taiwan_cain.charac_info WHERE create_time >= CURDATE()"));
        overview.put("online", querySafe("SELECT c.charac_no AS id, c.charac_name AS name, c.m_id AS uid " +
                "FROM taiwan_cain.charac_info c WHERE c.delete_flag=0 AND (" + onlineExists("c.m_id", "taiwan_login.login_account_1") +
                " OR " + onlineExists("c.m_id", "taiwan_login.login_account_2") +
                " OR " + onlineExists("c.m_id", "taiwan_login.login_account_3") + ") ORDER BY c.charac_no"));
        overview.put("todayActive", querySafe("SELECT c.charac_no AS id, c.charac_name AS name, c.m_id AS uid " +
                "FROM taiwan_cain.charac_info c WHERE c.last_play_time >= CURDATE() AND c.delete_flag=0 ORDER BY c.last_play_time DESC"));
        result.put("overview", overview);
        return result;
    }

    private Map<String, Object> recordMetrics() {
        Map<String, Object> item = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        item.put("timestamp", now);
        java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        double cpu = -1D;
        if (os instanceof com.sun.management.OperatingSystemMXBean) {
            cpu = ((com.sun.management.OperatingSystemMXBean) os).getSystemCpuLoad();
            if (cpu >= 0) cpu *= 100D;
        }
        item.put("cpu", round(cpu));
        Runtime runtime = Runtime.getRuntime();
        long memoryTotal = runtime.maxMemory();
        long memoryUsed = runtime.totalMemory() - runtime.freeMemory();
        item.put("memoryUsed", memoryUsed);
        item.put("memoryTotal", memoryTotal);
        item.put("memoryPercent", memoryTotal == 0 ? 0D : round(memoryUsed * 100D / memoryTotal));
        File root = new File(".");
        long total = root.getTotalSpace();
        long free = root.getUsableSpace();
        item.put("storageUsed", total - free);
        item.put("storageTotal", total);
        item.put("storagePercent", total == 0 ? 0D : round((total - free) * 100D / total));
        long[] network = readNetworkBytes();
        long seconds = Math.max(1L, (now - lastNetworkAt) / 1000L);
        long rxRate = lastNetworkAt == 0 ? 0L : Math.max(0L, network[0] - lastRx) / seconds;
        long txRate = lastNetworkAt == 0 ? 0L : Math.max(0L, network[1] - lastTx) / seconds;
        long capacityBytes = Math.max(1L, readNetworkSpeedMbps()) * 1024L * 1024L / 8L;
        double networkPercent = Math.min(100D, (rxRate + txRate) * 100D / capacityBytes);
        item.put("networkRx", rxRate);
        item.put("networkTx", txRate);
        item.put("networkPercent", round(networkPercent));
        lastRx = network[0];
        lastTx = network[1];
        lastNetworkAt = now;
        metricHistory.addLast(item);
        while (metricHistory.size() > 5760) metricHistory.pollFirst();
        return item;
    }

    private long readNetworkSpeedMbps() {
        Path root = Paths.get("/sys/class/net");
        if (!Files.isDirectory(root)) return 1000L;
        long total = 0L;
        try (DirectoryStream<Path> interfaces = Files.newDirectoryStream(root)) {
            for (Path iface : interfaces) {
                if ("lo".equals(iface.getFileName().toString())) continue;
                Path speed = iface.resolve("speed");
                if (!Files.exists(speed)) continue;
                try {
                    long value = Long.parseLong(new String(Files.readAllBytes(speed)).trim());
                    if (value > 0) total += value;
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return total > 0 ? total : 1000L;
    }

    private long[] readNetworkBytes() {
        long rx = 0L, tx = 0L;
        Path path = Paths.get("/proc/net/dev");
        if (!Files.exists(path)) return new long[]{0L, 0L};
        try {
            for (String line : Files.readAllLines(path)) {
                int colon = line.indexOf(':');
                if (colon < 0 || line.trim().startsWith("lo:")) continue;
                String[] values = line.substring(colon + 1).trim().split("\\s+");
                if (values.length >= 9) {
                    rx += Long.parseLong(values[0]);
                    tx += Long.parseLong(values[8]);
                }
            }
        } catch (Exception ignored) {
        }
        return new long[]{rx, tx};
    }

    public List<Map<String, Object>> listRewardTasks() {
        List<Map<String, Object>> rows = querySafe("SELECT id,name,interval_minutes,enabled,target_type,payload_json,next_run_at,last_run_at,created_at,updated_at " +
                "FROM dnf_service.gm_reward_task ORDER BY id DESC");
        for (Map<String, Object> row : rows) {
            Object payload = row.remove("payload_json");
            row.put("payload", parseJsonMap(payload));
        }
        return rows;
    }

    public Map<String, Object> saveRewardTask(Long id, Map<String, Object> payload) {
        String name = stringValue(payload.get("name"), "定时福利");
        int interval = Math.max(1, intValue(payload.get("intervalMinutes"), 60));
        boolean enabled = boolValue(payload.get("enabled"), false);
        String targetType = stringValue(payload.get("targetType"), "ALL");
        String json = JSON.toJSONString(payload);
        Date now = new Date();
        Date next = enabled ? new Date(now.getTime() + interval * 60000L) : new Date(0L);
        if (id == null || id <= 0) {
            jdbcTemplate.update("INSERT INTO dnf_service.gm_reward_task(name,interval_minutes,enabled,target_type,payload_json,next_run_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)",
                    name, interval, enabled ? 1 : 0, targetType, json, next, now, now);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
            result.put("name", name);
            result.put("enabled", enabled);
            return result;
        }
        jdbcTemplate.update("UPDATE dnf_service.gm_reward_task SET name=?,interval_minutes=?,enabled=?,target_type=?,payload_json=?,next_run_at=?,updated_at=? WHERE id=?",
                name, interval, enabled ? 1 : 0, targetType, json, next, now, id);
        return Collections.<String, Object>singletonMap("id", id);
    }

    public void toggleRewardTask(long id, boolean enabled) {
        Map<String, Object> row = first(querySafe("SELECT interval_minutes FROM dnf_service.gm_reward_task WHERE id=" + id));
        int interval = intValue(row.get("interval_minutes"), 60);
        Date next = enabled ? new Date(System.currentTimeMillis() + interval * 60000L) : new Date(0L);
        jdbcTemplate.update("UPDATE dnf_service.gm_reward_task SET enabled=?,next_run_at=?,updated_at=? WHERE id=?",
                enabled ? 1 : 0, next, new Date(), id);
    }

    public void deleteRewardTask(long id) {
        jdbcTemplate.update("DELETE FROM dnf_service.gm_reward_task WHERE id=?", id);
    }

    public Map<String, Object> sendGlobalReward(Map<String, Object> payload) {
        return dispatchReward(payload, "GM后台");
    }

    private void dispatchDueTasks() {
        List<Map<String, Object>> rows = querySafe("SELECT id,interval_minutes,payload_json FROM dnf_service.gm_reward_task WHERE enabled=1 AND next_run_at IS NOT NULL AND next_run_at <= NOW() ORDER BY id");
        for (Map<String, Object> row : rows) {
            long id = longValue(row.get("id"), 0L);
            int interval = Math.max(1, intValue(row.get("interval_minutes"), 60));
            try {
                Map<String, Object> payload = parseJsonMap(row.get("payload_json"));
                dispatchReward(payload, "定时福利");
            } catch (Exception e) {
                LOGGER.warn("Reward task {} failed: {}", id, e.getMessage());
            } finally {
                jdbcTemplate.update("UPDATE dnf_service.gm_reward_task SET last_run_at=?,next_run_at=?,updated_at=? WHERE id=?",
                        new Date(), new Date(System.currentTimeMillis() + interval * 60000L), new Date(), id);
            }
        }
    }

    public Map<String, Object> dispatchReward(Map<String, Object> payload, String sender) {
        List<Integer> recipients = resolveRecipients(payload);
        List<Map<String, Object>> items = listValue(payload.get("items"));
        int gold = Math.max(0, intValue(payload.get("gold"), 0));
        int ceraPoint = Math.max(0, intValue(payload.get("ceraPoint"), 0));
        String message = stringValue(payload.get("message"), "GM奖励");
        int sent = 0;
        for (Integer characNo : recipients) {
            if (items.isEmpty() && gold == 0) {
                continue;
            }
            boolean first = true;
            if (items.isEmpty()) items = Collections.singletonList(Collections.<String, Object>emptyMap());
            for (Map<String, Object> item : items) {
                Postal postal = new Postal();
                postal.setSendCharacName(sender);
                postal.setReceiveCharacNo(String.valueOf(characNo));
                postal.setItemId(longValue(item.get("itemId"), 0L));
                postal.setAddInfo(Math.max(1, intValue(item.get("quantity"), 1)));
                postal.setUpgrade(intValue(item.get("upgrade"), 0));
                postal.setSeperateUpgrade(intValue(item.get("separateUpgrade"), intValue(item.get("seperateUpgrade"), 0)));
                postal.setSealFlag(boolValue(item.get("sealFlag"), false));
                postal.setAmplifyOption(intValue(item.get("amplifyOption"), 0));
                postal.setAmplifyValue(intValue(item.get("amplifyValue"), 0));
                postal.setGold(first ? gold : 0);
                postalService.sendMail(postal);
                sent++;
                first = false;
            }
            if (ceraPoint > 0) addCeraPoint(characNo, ceraPoint);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recipientCount", recipients.size());
        result.put("mailCount", sent);
        result.put("ceraPoint", ceraPoint);
        result.put("message", message);
        return result;
    }

    private List<Integer> resolveRecipients(Map<String, Object> payload) {
        String type = stringValue(payload.get("targetType"), "ALL").toUpperCase(Locale.ROOT);
        if ("CHARACTERS".equals(type) || "CHARACTER".equals(type)) {
            List<Integer> result = new ArrayList<>();
            for (Object value : listValues(payload.get("characterIds"))) result.add(intValue(value, 0));
            return result;
        }
        String sql = "SELECT DISTINCT c.charac_no FROM taiwan_cain.charac_info c WHERE c.delete_flag=0";
        if ("ONLINE".equals(type)) {
            sql += " AND (" + onlineExists("c.m_id", "taiwan_login.login_account_1") + " OR " +
                    onlineExists("c.m_id", "taiwan_login.login_account_2") + " OR " +
                    onlineExists("c.m_id", "taiwan_login.login_account_3") + ")";
        }
        List<Map<String, Object>> rows = querySafe(sql);
        List<Integer> result = new ArrayList<>();
        for (Map<String, Object> row : rows) result.add(intValue(row.get("charac_no"), intValue(row.get("CHARAC_NO"), 0)));
        return result;
    }

    private void addCeraPoint(int characNo, int amount) {
        try {
            jdbcTemplate.update("UPDATE taiwan_billing.cash_cera_point p JOIN taiwan_cain.charac_info c ON CAST(p.account AS UNSIGNED)=c.m_id SET p.cera_point=p.cera_point+?,p.mod_date=NOW() WHERE c.charac_no=?", amount, characNo);
        } catch (Exception e) {
            LOGGER.warn("Could not update cera point for {}: {}", characNo, e.getMessage());
        }
    }

    public Map<String, Object> queryMail(String sender, String receiver, Integer itemId, String keyword, int page, int pageSize) {
        page = Math.max(1, page);
        pageSize = Math.min(100, Math.max(1, pageSize));
        StringBuilder where = new StringBuilder(" FROM taiwan_cain_2nd.postal p LEFT JOIN taiwan_cain.charac_info c ON c.charac_no=p.receive_charac_no LEFT JOIN taiwan_cain_2nd.letter l ON l.letter_id=p.letter_id WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (sender != null && !sender.trim().isEmpty()) { where.append(" AND p.send_charac_name LIKE ?"); args.add("%" + sender.trim() + "%"); }
        if (receiver != null && !receiver.trim().isEmpty()) { where.append(" AND (c.charac_name LIKE ? OR CAST(p.receive_charac_no AS CHAR)=?)"); args.add("%" + receiver.trim() + "%"); args.add(receiver.trim()); }
        if (itemId != null && itemId > 0) { where.append(" AND p.item_id=?"); args.add(itemId); }
        if (keyword != null && !keyword.trim().isEmpty()) { where.append(" AND (p.send_charac_name LIKE ? OR l.letter_text LIKE ? OR CAST(p.postal_id AS CHAR)=?)"); args.add("%" + keyword.trim() + "%"); args.add("%" + keyword.trim() + "%"); args.add(keyword.trim()); }
        Number total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + where, args.toArray(), Number.class);
        String sql = "SELECT p.postal_id,p.occ_time,p.send_charac_no,p.send_charac_name,p.receive_charac_no,p.item_id,p.add_info,p.upgrade,p.amplify_option,p.amplify_value,p.gold,p.delete_flag,p.seal_flag,p.letter_id,p.seperate_upgrade,DATE_FORMAT(p.receive_time,'%Y-%m-%d %H:%i:%s') AS receive_time_text,c.charac_name AS receiver_name,c.m_id AS receiver_uid,l.letter_text,l.stat AS letter_stat" + where + " ORDER BY p.postal_id DESC LIMIT ? OFFSET ?";
        args.add(pageSize);
        args.add((page - 1) * pageSize);
        List<Postal> list = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql, args.toArray())) list.add(toPostal(row));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalSize", total == null ? 0 : total.intValue());
        result.put("page", page);
        result.put("totalPageSize", pageSize);
        result.put("list", list);
        return result;
    }

    private Postal toPostal(Map<String, Object> row) {
        Postal p = new Postal();
        p.setPostalId(longValue(row.get("postal_id"), 0));
        p.setOccTime(parseDate(row.get("occ_time")));
        p.setSendCharacNo(intValue(row.get("send_charac_no"), 0));
        p.setSendCharacName(ChinaseUtil.toSimple(stringValue(row.get("send_charac_name"), "")));
        p.setReceiveCharacNo(stringValue(row.get("receive_charac_no"), ""));
        p.setItemId(longValue(row.get("item_id"), 0));
        p.setAddInfo(intValue(row.get("add_info"), 0));
        p.setUpgrade(intValue(row.get("upgrade"), 0));
        p.setAmplifyOption(intValue(row.get("amplify_option"), 0));
        p.setAmplifyValue(intValue(row.get("amplify_value"), 0));
        p.setGold(intValue(row.get("gold"), 0));
        p.setDeleteFlag(intValue(row.get("delete_flag"), 0) != 0);
        p.setSealFlag(intValue(row.get("seal_flag"), 0) != 0);
        p.setLetterId(intValue(row.get("letter_id"), 0));
        p.setSeperateUpgrade(intValue(row.get("seperate_upgrade"), 0));
        p.setReceiveTime(parseDate(row.get("receive_time_text")));
        p.setReceiverName(ChinaseUtil.toSimple(stringValue(row.get("receiver_name"), "")));
        p.setReceiverUid(longValue(row.get("receiver_uid"), 0));
        p.setLetterText(ChinaseUtil.toSimple(stringValue(row.get("letter_text"), "")));
        p.setClaimed(intValue(row.get("letter_stat"), 0) > 0 || p.getReceiveTime() != null);
        p.setSenderType(p.getSendCharacName().contains("定时") ? "定时福利" : (p.getSendCharacName().contains("GM") ? "GM后台" : "游戏邮件"));
        Item item = pvfManager.findItem((int) p.getItemId());
        if (item != null) {
            p.setItemName(item.getName());
            p.setItemIcon(item.getIcon());
        }
        return p;
    }

    public List<Map<String, Object>> listBackups() {
        return querySafe("SELECT id,type,path,size_bytes,status,created_at,message FROM dnf_service.gm_backup_entry ORDER BY id DESC");
    }

    public List<Map<String, Object>> listBackupSchedules() {
        return querySafe("SELECT type,interval_minutes,enabled,retain_count,next_run_at,updated_at FROM dnf_service.gm_backup_schedule ORDER BY type");
    }

    public void saveBackupSchedule(String type, int intervalMinutes, boolean enabled, int retainCount) {
        Date now = new Date();
        Date next = enabled ? new Date(now.getTime() + Math.max(1, intervalMinutes) * 60000L) : new Date(0L);
        jdbcTemplate.update("INSERT INTO dnf_service.gm_backup_schedule(type,interval_minutes,enabled,retain_count,next_run_at,updated_at) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE interval_minutes=VALUES(interval_minutes),enabled=VALUES(enabled),retain_count=VALUES(retain_count),next_run_at=VALUES(next_run_at),updated_at=VALUES(updated_at)", type, Math.max(1, intervalMinutes), enabled ? 1 : 0, Math.max(1, retainCount), next, now);
    }

    private void processBackupSchedules() {
        for (Map<String, Object> schedule : listBackupSchedules()) {
            if (intValue(schedule.get("enabled"), 0) == 0 || schedule.get("next_run_at") == null) continue;
            Date next = parseDate(schedule.get("next_run_at"));
            if (next == null || next.after(new Date())) continue;
            String type = stringValue(schedule.get("type"), "PVF");
            try { createBackup(type); } catch (Exception e) { LOGGER.warn("Scheduled {} backup failed: {}", type, e.getMessage()); }
            int interval = Math.max(1, intValue(schedule.get("interval_minutes"), 1440));
            jdbcTemplate.update("UPDATE dnf_service.gm_backup_schedule SET next_run_at=?,updated_at=? WHERE type=?", new Date(System.currentTimeMillis() + interval * 60000L), new Date(), type);
            pruneBackups(type, Math.max(1, intValue(schedule.get("retain_count"), 10)));
        }
    }

    public Map<String, Object> createBackup(String type) throws IOException, InterruptedException {
        type = "DB".equalsIgnoreCase(type) ? "DB" : "PVF";
        File root = new File(System.getProperty("user.dir"), "backups");
        if (!root.exists() && !root.mkdirs()) throw new IOException("无法创建备份目录");
        String stamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        File target = new File(root, type.toLowerCase(Locale.ROOT) + "-" + stamp + ("DB".equals(type) ? ".sql" : ".pvf"));
        String status = "SUCCESS", message = "";
        try {
            if ("PVF".equals(type)) {
                Files.copy(new File("data/Script.pvf").toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                runDump(target);
            }
        } catch (Exception e) {
            status = "FAILED";
            message = e.getMessage() == null ? "" : e.getMessage();
            if (target.exists()) target.delete();
        }
        long size = target.exists() ? target.length() : 0L;
        jdbcTemplate.update("INSERT INTO dnf_service.gm_backup_entry(type,path,size_bytes,status,created_at,message) VALUES(?,?,?,?,?,?)", type, target.getAbsolutePath(), size, status, new Date(), message);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type); result.put("path", target.getAbsolutePath()); result.put("sizeBytes", size); result.put("status", status); result.put("message", message);
        if ("FAILED".equals(status)) throw new IOException(message);
        return result;
    }

    private void runDump(File target) throws IOException, InterruptedException {
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        if (password == null) password = System.getenv("MYSQL_PASSWORD");
        List<String> command = new ArrayList<>();
        command.add("mysqldump"); command.add("--host=127.0.0.1"); command.add("--single-transaction"); command.add("--routines"); command.add("--triggers"); command.add("-u" + stringValue(System.getenv("SPRING_DATASOURCE_USERNAME"), "game"));
        for (String schema : BACKUP_SCHEMAS) { command.add("--databases"); command.add(schema); }
        ProcessBuilder builder = new ProcessBuilder(command);
        if (password != null) builder.environment().put("MYSQL_PWD", password);
        builder.redirectOutput(target);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        if (process.waitFor() != 0) throw new IOException("mysqldump 退出码 " + process.exitValue());
    }

    public void restoreBackup(long id) throws IOException, InterruptedException {
        Map<String, Object> row = first(querySafe("SELECT id,type,path FROM dnf_service.gm_backup_entry WHERE id=" + id));
        if (row.isEmpty()) throw new FileNotFoundException("备份不存在");
        File root = new File(System.getProperty("user.dir"), "backups").getCanonicalFile();
        File target = new File(stringValue(row.get("path"), "")).getCanonicalFile();
        if (!target.toPath().startsWith(root.toPath()) || !target.exists()) throw new IOException("备份路径无效");
        if ("PVF".equalsIgnoreCase(stringValue(row.get("type"), ""))) {
            Files.copy(target.toPath(), new File("data/Script.pvf").toPath(), StandardCopyOption.REPLACE_EXISTING);
            pvfManager.init(); pvfManager.clearItemCache(); PvfCache.setPvfSize(0); PvfCache.setEquipmentList(null); PvfCache.setStackableList(null);
        } else {
            runRestore(target);
        }
    }

    private void runRestore(File target) throws IOException, InterruptedException {
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        if (password == null) password = System.getenv("MYSQL_PASSWORD");
        ProcessBuilder builder = new ProcessBuilder("mysql", "--host=127.0.0.1", "-u" + stringValue(System.getenv("SPRING_DATASOURCE_USERNAME"), "game"));
        if (password != null) builder.environment().put("MYSQL_PWD", password);
        builder.redirectInput(target); builder.redirectErrorStream(true);
        Process process = builder.start();
        if (process.waitFor() != 0) throw new IOException("mysql 恢复退出码 " + process.exitValue());
    }

    private void pruneBackups(String type, int retain) {
        List<Map<String, Object>> rows = querySafe("SELECT id,path FROM dnf_service.gm_backup_entry WHERE type='" + ("DB".equalsIgnoreCase(type) ? "DB" : "PVF") + "' AND status='SUCCESS' ORDER BY created_at DESC");
        for (int i = retain; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            try { Files.deleteIfExists(Paths.get(stringValue(row.get("path"), ""))); } catch (Exception ignored) { }
            jdbcTemplate.update("DELETE FROM dnf_service.gm_backup_entry WHERE id=?", longValue(row.get("id"), 0));
        }
    }

    private Number scalar(String sql) {
        try { return jdbcTemplate.queryForObject(sql, Number.class); } catch (Exception e) { return 0; }
    }

    private List<Map<String, Object>> querySafe(String sql) {
        try { return jdbcTemplate.queryForList(sql); } catch (Exception e) { LOGGER.warn("GM query failed: {}", e.getMessage()); return new ArrayList<>(); }
    }

    private Map<String, Object> first(List<Map<String, Object>> list) { return list.isEmpty() ? new LinkedHashMap<String, Object>() : list.get(0); }
    private String onlineExists(String idExpression, String table) {
        return "EXISTS (SELECT 1 FROM " + table + " l WHERE l.m_id=" + idExpression + " AND l.login_status=1)";
    }
    private Map<String, Object> parseJsonMap(Object value) { try { Map<String, Object> map = JSON.parseObject(stringValue(value, "{}"), Map.class); return map == null ? new LinkedHashMap<String, Object>() : map; } catch (Exception e) { return new LinkedHashMap<String, Object>(); } }
    @SuppressWarnings("unchecked") private List<Map<String, Object>> listValue(Object value) { return value instanceof List ? (List<Map<String, Object>>) value : new ArrayList<Map<String, Object>>(); }
    private List<?> listValues(Object value) { return value instanceof List ? (List<?>) value : new ArrayList<Object>(); }
    private String stringValue(Object value, String fallback) { return value == null ? fallback : String.valueOf(value); }
    private int intValue(Object value, int fallback) { try { return value == null ? fallback : ((Number) value).intValue(); } catch (Exception e) { try { return Integer.parseInt(String.valueOf(value)); } catch (Exception ignored) { return fallback; } } }
    private long longValue(Object value, long fallback) { try { return value == null ? fallback : ((Number) value).longValue(); } catch (Exception e) { try { return Long.parseLong(String.valueOf(value)); } catch (Exception ignored) { return fallback; } } }
    private boolean boolValue(Object value, boolean fallback) { if (value == null) return fallback; if (value instanceof Boolean) return (Boolean) value; return "1".equals(String.valueOf(value)) || "true".equalsIgnoreCase(String.valueOf(value)); }
    private Date parseDate(Object value) { if (value == null) return null; if (value instanceof Date) return (Date) value; String text = String.valueOf(value); if (text.startsWith("0000")) return null; try { return DATE_FORMAT.parse(text); } catch (ParseException e) { return null; } }
    private double round(double value) { return value < 0 ? value : Math.round(value * 100.0D) / 100.0D; }
}
