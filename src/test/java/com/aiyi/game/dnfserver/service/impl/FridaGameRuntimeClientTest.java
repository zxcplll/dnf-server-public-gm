package com.aiyi.game.dnfserver.service.impl;

import com.alibaba.fastjson.JSON;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FridaGameRuntimeClientTest {

    @Test
    public void sendsOneJsonRequestAndAcceptsVerifiedGoldChange() throws Exception {
        ServerSocket server = new ServerSocket(0);
        FutureTask<String> received = respondOnce(server,
                "{\"ok\":true,\"requestId\":\"reward-23-1\",\"accountId\":18000023,\"characNo\":23," +
                        "\"before\":22720,\"added\":100000,\"after\":122720}");

        FridaGameRuntimeClient client = new FridaGameRuntimeClient(
                "127.0.0.1", server.getLocalPort(), 1000, 2000);
        GameRuntimeClient.GoldChange change = client.addGold("reward-23-1", 18000023, 23, 100000);

        Map<String, Object> request = JSON.parseObject(received.get(2, TimeUnit.SECONDS));
        assertEquals("add_gold", request.get("op"));
        assertEquals("reward-23-1", request.get("requestId"));
        assertEquals(18000023, ((Number) request.get("accountId")).intValue());
        assertEquals(23, ((Number) request.get("characNo")).intValue());
        assertEquals(100000, ((Number) request.get("amount")).intValue());
        assertEquals(22720, change.getBefore());
        assertEquals(100000, change.getAdded());
        assertEquals(122720, change.getAfter());
        server.close();
    }

    @Test
    public void resolvesTheCurrentOnlineCharacterForAnAccount() throws Exception {
        ServerSocket server = new ServerSocket(0);
        FutureTask<String> received = respondOnce(server,
                "{\"ok\":true,\"accountId\":18000023,\"characNo\":23,\"gold\":5031059}");
        FridaGameRuntimeClient client = new FridaGameRuntimeClient(
                "127.0.0.1", server.getLocalPort(), 1000, 2000);

        GameRuntimeClient.OnlineCharacter character = client.findOnlineCharacter(18000023);

        Map<String, Object> request = JSON.parseObject(received.get(2, TimeUnit.SECONDS));
        assertEquals("inspect_account", request.get("op"));
        assertEquals(18000023, ((Number) request.get("accountId")).intValue());
        assertEquals(23, character.getCharacNo());
        assertEquals(5031059L, character.getGold());
        server.close();
    }

    @Test
    public void acceptsUnsignedGoldBalancesAboveSignedIntegerRange() throws Exception {
        ServerSocket server = new ServerSocket(0);
        respondOnce(server,
                "{\"ok\":true,\"requestId\":\"reward-high\",\"accountId\":42,\"characNo\":23," +
                        "\"before\":3000000000,\"added\":100000,\"after\":3000100000}");
        try {
            FridaGameRuntimeClient client = new FridaGameRuntimeClient(
                    "127.0.0.1", server.getLocalPort(), 1000, 2000);
            GameRuntimeClient.GoldChange change = client.addGold("reward-high", 42, 23, 100000);
            assertEquals(3000000000L, change.getBefore());
            assertEquals(3000100000L, change.getAfter());
        } finally {
            server.close();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsSuccessResponseForDifferentAccount() throws Exception {
        ServerSocket server = new ServerSocket(0);
        respondOnce(server,
                "{\"ok\":true,\"requestId\":\"reward-account\",\"accountId\":43,\"characNo\":23," +
                        "\"before\":1000,\"added\":500,\"after\":1500}");
        try {
            FridaGameRuntimeClient client = new FridaGameRuntimeClient(
                    "127.0.0.1", server.getLocalPort(), 1000, 2000);
            client.addGold("reward-account", 42, 23, 500);
        } finally {
            server.close();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsSuccessResponseWithWrongGoldDelta() throws Exception {
        ServerSocket server = new ServerSocket(0);
        respondOnce(server,
                "{\"ok\":true,\"requestId\":\"reward-23-2\",\"characNo\":23," +
                        "\"before\":122720,\"added\":99999,\"after\":222719}");
        try {
            FridaGameRuntimeClient client = new FridaGameRuntimeClient(
                    "127.0.0.1", server.getLocalPort(), 1000, 2000);
            client.addGold("reward-23-2", 18000023, 23, 100000);
        } finally {
            server.close();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void surfacesRuntimeRejection() throws Exception {
        ServerSocket server = new ServerSocket(0);
        respondOnce(server,
                "{\"ok\":false,\"requestId\":\"reward-23-3\"," +
                        "\"error\":\"CHARACTER_NOT_ONLINE\"}");
        try {
            FridaGameRuntimeClient client = new FridaGameRuntimeClient(
                    "127.0.0.1", server.getLocalPort(), 1000, 2000);
            client.addGold("reward-23-3", 18000023, 23, 100000);
        } finally {
            server.close();
        }
    }

    private FutureTask<String> respondOnce(ServerSocket server, String response) {
        FutureTask<String> task = new FutureTask<>(() -> {
            try (Socket socket = server.accept();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(
                         socket.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                         socket.getOutputStream(), StandardCharsets.UTF_8))) {
                String request = reader.readLine();
                assertTrue(request != null && !request.isEmpty());
                writer.write(response);
                writer.newLine();
                writer.flush();
                return request;
            }
        });
        Thread thread = new Thread(task, "frida-runtime-test-server");
        thread.setDaemon(true);
        thread.start();
        return task;
    }
}
