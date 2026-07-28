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

@Service
public class FridaGameRuntimeClient implements GameRuntimeClient {

    private static final long MAX_UNSIGNED_INT = 0xffffffffL;

    @Value("${dnf.runtime.host:127.0.0.1}")
    private String host = "127.0.0.1";
    @Value("${dnf.runtime.port:27043}")
    private int port = 27043;
    @Value("${dnf.runtime.connect-timeout-ms:1000}")
    private int connectTimeoutMillis = 1000;
    @Value("${dnf.runtime.read-timeout-ms:3000}")
    private int readTimeoutMillis = 3000;

    public FridaGameRuntimeClient() {
    }

    FridaGameRuntimeClient(String host, int port, int connectTimeoutMillis, int readTimeoutMillis) {
        this.host = host;
        this.port = port;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    @Override
    public OnlineCharacter findOnlineCharacter(int accountId) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("Runtime accountId must be positive");
        }
        JSONObject request = new JSONObject(true);
        request.put("op", "inspect_account");
        request.put("accountId", accountId);
        JSONObject response = exchange(request);
        requireSuccess(response, "account inspection");
        int responseAccountId = requiredInt(response, "accountId");
        int characNo = requiredInt(response, "characNo");
        long gold = requiredUnsignedInt(response, "gold");
        if (responseAccountId != accountId || characNo <= 0) {
            throw new IllegalStateException("Frida runtime returned a different account or invalid character");
        }
        return new OnlineCharacter(responseAccountId, characNo, gold);
    }

    @Override
    public GoldChange addGold(String requestId, int accountId, int characNo, int amount) {
        if (requestId == null || requestId.trim().isEmpty()) {
            throw new IllegalArgumentException("Runtime requestId is required");
        }
        if (accountId <= 0 || characNo <= 0 || amount <= 0) {
            throw new IllegalArgumentException("Runtime accountId, characNo and amount must be positive");
        }

        JSONObject request = new JSONObject(true);
        request.put("op", "add_gold");
        request.put("requestId", requestId);
        request.put("accountId", accountId);
        request.put("characNo", characNo);
        request.put("amount", amount);

        JSONObject response = exchange(request);
        requireSuccess(response, "gold reward");
        if (!requestId.equals(response.getString("requestId"))) {
            throw new IllegalStateException("Frida runtime returned a mismatched requestId");
        }

        int responseAccountId = requiredInt(response, "accountId");
        int responseCharacNo = requiredInt(response, "characNo");
        long before = requiredUnsignedInt(response, "before");
        long added = requiredUnsignedInt(response, "added");
        long after = requiredUnsignedInt(response, "after");
        if (responseAccountId != accountId || responseCharacNo != characNo) {
            throw new IllegalStateException("Frida runtime returned a different account or character");
        }
        if (added != amount || ((before + added) & MAX_UNSIGNED_INT) != after) {
            throw new IllegalStateException("Frida runtime gold delta was not exact: requested=" +
                    amount + ", before=" + before + ", added=" + added + ", after=" + after);
        }
        return new GoldChange(responseCharacNo, before, added, after);
    }

    private void requireSuccess(JSONObject response, String operation) {
        if (!response.containsKey("ok") || !(response.get("ok") instanceof Boolean)) {
            throw new IllegalStateException("Frida runtime returned an invalid " + operation + " response");
        }
        if (!response.getBooleanValue("ok")) {
            throw new IllegalStateException("Frida runtime rejected " + operation + ": " +
                    response.getString("error"));
        }
    }

    private int requiredInt(JSONObject response, String field) {
        Object value = response.get(field);
        if (!(value instanceof Number)) {
            throw new IllegalStateException("Frida runtime response is missing numeric field " + field);
        }
        long number = ((Number) value).longValue();
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalStateException("Frida runtime field is outside signed integer range: " + field);
        }
        return (int) number;
    }

    private long requiredUnsignedInt(JSONObject response, String field) {
        Object value = response.get(field);
        if (!(value instanceof Number)) {
            throw new IllegalStateException("Frida runtime response is missing numeric field " + field);
        }
        long number = ((Number) value).longValue();
        if (number < 0 || number > MAX_UNSIGNED_INT) {
            throw new IllegalStateException("Frida runtime field is outside unsigned integer range: " + field);
        }
        return number;
    }

    private JSONObject exchange(JSONObject request) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
            socket.setSoTimeout(readTimeoutMillis);
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(
                         socket.getInputStream(), StandardCharsets.UTF_8))) {
                writer.write(request.toJSONString());
                writer.newLine();
                writer.flush();
                String line = reader.readLine();
                if (line == null || line.trim().isEmpty()) {
                    throw new IllegalStateException("Frida runtime returned an empty response");
                }
                JSONObject response = JSON.parseObject(line);
                if (response == null) {
                    throw new IllegalStateException("Frida runtime returned invalid JSON");
                }
                return response;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Frida runtime is unavailable at " + host + ":" + port, e);
        } catch (RuntimeException e) {
            if (e instanceof IllegalStateException) throw e;
            throw new IllegalStateException("Frida runtime returned invalid JSON", e);
        }
    }
}
