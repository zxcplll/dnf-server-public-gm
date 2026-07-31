package com.aiyi.game.dnfserver.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GmRuntimeHealthService {

    @Value("${dnf.runtime.host:127.0.0.1}")
    private String runtimeHost = "127.0.0.1";
    @Value("${dnf.runtime.port:27043}")
    private int runtimePort = 27043;
    @Value("${dnf.health.ports:80,9001,27043}")
    private String configuredPorts = "80,9001,27043";
    @Value("${dnf.health.process-markers:df_game_r,java,nginx}")
    private String configuredProcesses = "df_game_r,java,nginx";
    @Value("${dnf.health.log-paths:/dp2/frida/frida.log,/dp2/dp2.log,/opt/dnf-server-public/logs/dnf.log}")
    private String configuredLogs = "/dp2/frida/frida.log,/dp2/dp2.log,/opt/dnf-server-public/logs/dnf.log";

    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("observedAt", System.currentTimeMillis());
        Map<String, Object> game = safeProbe("game", new Probe() {
            @Override
            public Map<String, Object> run() {
                return probeGameProcess();
            }
        });
        Map<String, Object> bridge = safeProbe("frida", new Probe() {
            @Override
            public Map<String, Object> run() {
                return probeBridge();
            }
        });
        List<Map<String, Object>> ports = safeListProbe("ports", new ListProbe() {
            @Override
            public List<Map<String, Object>> run() {
                return probePorts();
            }
        });
        List<Map<String, Object>> services = safeListProbe("services", new ListProbe() {
            @Override
            public List<Map<String, Object>> run() {
                return probeServices();
            }
        });
        List<Map<String, Object>> logs = safeListProbe("logs", new ListProbe() {
            @Override
            public List<Map<String, Object>> run() {
                return probeLogs();
            }
        });
        result.put("gameProcess", game);
        result.put("bridge", bridge);
        result.put("ports", ports);
        result.put("services", services);
        result.put("logs", logs);
        boolean healthy = isHealthy(game) && isHealthy(bridge) && allHealthy(ports) &&
                allHealthy(services) && allHealthy(logs);
        result.put("status", healthy ? "HEALTHY" : "DEGRADED");
        return result;
    }

    Map<String, Object> probeGameProcess() {
        for (ProcessSnapshot process : processes()) {
            if (process.command.contains("df_game_r")) {
                Map<String, Object> result = base("game", "HEALTHY", null, 0L);
                result.put("pid", process.pid);
                result.put("executable", process.executable);
                result.put("command", process.command);
                result.put("rssBytes", process.rssBytes);
                result.put("threads", process.threads);
                result.put("uptimeSeconds", process.uptimeSeconds);
                return result;
            }
        }
        return base("game", "DEGRADED", "游戏进程未运行", 0L);
    }

    Map<String, Object> probeBridge() {
        long started = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(runtimeHost, runtimePort), 1000);
            socket.setSoTimeout(2000);
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.US_ASCII));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(
                         socket.getInputStream(), StandardCharsets.US_ASCII))) {
                writer.write("{\"op\":\"ping\"}\n");
                writer.flush();
                String line = reader.readLine();
                JSONObject response = line == null ? null : JSON.parseObject(line);
                if (response == null || !response.getBooleanValue("ok")) {
                    throw new IllegalStateException("Frida bridge returned an invalid ping response");
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Frida bridge connection failed: " + exception.getMessage(), exception);
        }
        long latency = (System.nanoTime() - started) / 1000000L;
        return base("frida", "HEALTHY", null, latency);
    }

    List<Map<String, Object>> probePorts() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String value : split(configuredPorts)) {
            int port;
            try {
                port = Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                result.add(base(value, "DEGRADED", "配置的端口无效", 0L));
                continue;
            }
            long started = System.nanoTime();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
                result.add(base(String.valueOf(port), "HEALTHY", null,
                        (System.nanoTime() - started) / 1000000L));
            } catch (IOException exception) {
                result.add(base(String.valueOf(port), "DEGRADED", "端口未监听", 0L));
            }
        }
        return result;
    }

    List<Map<String, Object>> probeServices() {
        List<ProcessSnapshot> processes = processes();
        List<Map<String, Object>> result = new ArrayList<>();
        for (String marker : split(configuredProcesses)) {
            ProcessSnapshot match = null;
            for (ProcessSnapshot process : processes) {
                if (process.command.contains(marker)) {
                    match = process;
                    break;
                }
            }
            Map<String, Object> item = base(marker, match == null ? "DEGRADED" : "HEALTHY",
                    match == null ? "进程未运行" : null, 0L);
            if (match != null) {
                item.put("pid", match.pid);
                item.put("rssBytes", match.rssBytes);
                item.put("threads", match.threads);
                item.put("uptimeSeconds", match.uptimeSeconds);
            }
            result.add(item);
        }
        return result;
    }

    List<Map<String, Object>> probeLogs() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String configuredPath : split(configuredLogs)) {
            Path path = Paths.get(configuredPath).normalize();
            Map<String, Object> item;
            try {
                if (!Files.exists(path)) {
                    item = base(path.getFileName().toString(), "DEGRADED", "日志文件不存在", 0L);
                } else {
                    FileTime modified = Files.getLastModifiedTime(path);
                    item = base(path.getFileName().toString(), "HEALTHY", null, 0L);
                    item.put("lastModified", modified.toMillis());
                    item.put("ageSeconds", Math.max(0L,
                            (System.currentTimeMillis() - modified.toMillis()) / 1000L));
                    item.put("sizeBytes", Files.isRegularFile(path) ? Files.size(path) : 0L);
                }
            } catch (IOException exception) {
                item = base(path.getFileName().toString(), "DEGRADED", "日志状态读取失败", 0L);
            }
            item.put("path", path.toString());
            result.add(item);
        }
        return result;
    }

    private List<ProcessSnapshot> processes() {
        List<ProcessSnapshot> result = new ArrayList<>();
        Path proc = Paths.get("/proc");
        if (!Files.isDirectory(proc)) {
            return result;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(proc)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (!name.matches("[0-9]+")) {
                    continue;
                }
                try {
                    int pid = Integer.parseInt(name);
                    byte[] commandBytes = Files.readAllBytes(entry.resolve("cmdline"));
                    String command = new String(commandBytes, StandardCharsets.UTF_8).replace('\0', ' ').trim();
                    if (command.isEmpty()) {
                        continue;
                    }
                    String executable = Files.isSymbolicLink(entry.resolve("exe"))
                            ? Files.readSymbolicLink(entry.resolve("exe")).toString() : "";
                    Map<String, Long> status = readStatus(entry.resolve("status"));
                    result.add(new ProcessSnapshot(pid, command, executable,
                            status.get("VmRSS") == null ? 0L : status.get("VmRSS") * 1024L,
                            status.get("Threads") == null ? 0 : status.get("Threads").intValue(),
                            processUptime(entry.resolve("stat"))));
                } catch (Exception ignored) {
                    // A process can exit while /proc is being read.
                }
            }
        } catch (IOException ignored) {
        }
        return result;
    }

    private Map<String, Long> readStatus(Path path) throws IOException {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (!line.startsWith("VmRSS:") && !line.startsWith("Threads:")) {
                continue;
            }
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2) {
                result.put(parts[0].replace(":", ""), Long.parseLong(parts[1]));
            }
        }
        return result;
    }

    private long processUptime(Path statPath) {
        try {
            String stat = new String(Files.readAllBytes(statPath), StandardCharsets.US_ASCII);
            int endCommand = stat.lastIndexOf(')');
            String[] fields = stat.substring(endCommand + 2).split("\\s+");
            long startTicks = Long.parseLong(fields[19]);
            String uptimeText = new String(Files.readAllBytes(Paths.get("/proc/uptime")),
                    StandardCharsets.US_ASCII).split("\\s+")[0];
            long uptimeSeconds = (long) Double.parseDouble(uptimeText);
            return Math.max(0L, uptimeSeconds - startTicks / 100L);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private Map<String, Object> safeProbe(String name, Probe probe) {
        try {
            return probe.run();
        } catch (RuntimeException exception) {
            return base(name, "DEGRADED", compactReason(exception), 0L);
        }
    }

    private List<Map<String, Object>> safeListProbe(String name, ListProbe probe) {
        try {
            return probe.run();
        } catch (RuntimeException exception) {
            List<Map<String, Object>> result = new ArrayList<>();
            result.add(base(name, "DEGRADED", compactReason(exception), 0L));
            return result;
        }
    }

    private String compactReason(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 240 ? message : message.substring(0, 240);
    }

    private boolean isHealthy(Map<String, Object> item) {
        return "HEALTHY".equals(item.get("status"));
    }

    private boolean allHealthy(List<Map<String, Object>> items) {
        for (Map<String, Object> item : items) {
            if (!isHealthy(item)) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> base(String name, String status, String reason, long latencyMs) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("status", status);
        result.put("reason", reason);
        result.put("latencyMs", latencyMs);
        result.put("observedAt", System.currentTimeMillis());
        return result;
    }

    private List<String> split(String value) {
        List<String> result = new ArrayList<>();
        for (String part : Arrays.asList(value == null ? new String[0] : value.split(","))) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private interface Probe {
        Map<String, Object> run();
    }

    private interface ListProbe {
        List<Map<String, Object>> run();
    }

    private static final class ProcessSnapshot {
        private final int pid;
        private final String command;
        private final String executable;
        private final long rssBytes;
        private final int threads;
        private final long uptimeSeconds;

        private ProcessSnapshot(int pid, String command, String executable, long rssBytes, int threads,
                                long uptimeSeconds) {
            this.pid = pid;
            this.command = command;
            this.executable = executable;
            this.rssBytes = rssBytes;
            this.threads = threads;
            this.uptimeSeconds = uptimeSeconds;
        }
    }
}
