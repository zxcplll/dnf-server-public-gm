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
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FridaGameRuntimeClientTest {

    @Test
    public void pingsTheVersionedBridgeAndReadsCapabilities() throws Exception {
        ServerSocket server = new ServerSocket(0);
        FutureTask<String> received = respondOnce(server,
                "{\"ok\":true,\"op\":\"pong\",\"protocolVersion\":2," +
                        "\"capabilities\":[\"online_snapshot\",\"change_gold\"]}");
        try {
            FridaGameRuntimeClient client = new FridaGameRuntimeClient(
                    "127.0.0.1", server.getLocalPort(), 1000, 2000);

            GameRuntimeClient.RuntimePing ping = client.ping();

            Map<String, Object> request = JSON.parseObject(received.get(2, TimeUnit.SECONDS));
            assertEquals("ping", request.get("op"));
            assertEquals(2, ping.getProtocolVersion());
            assertEquals(Arrays.asList("online_snapshot", "change_gold"), ping.getCapabilities());
        } finally {
            server.close();
        }
    }

    @Test
    public void readsABoundedOnlineSnapshot() throws Exception {
        ServerSocket server = new ServerSocket(0);
        FutureTask<String> received = respondOnce(server,
                "{\"ok\":true,\"protocolVersion\":2,\"offset\":0,\"limit\":50," +
                        "\"total\":1,\"truncated\":false,\"players\":[{" +
                        "\"accountId\":42,\"characNo\":23,\"state\":3,\"level\":86," +
                        "\"job\":1,\"growType\":2,\"gold\":3000000000," +
                        "\"fatigue\":12,\"maxFatigue\":156,\"village\":1,\"area\":2," +
                        "\"posX\":3,\"posY\":4,\"inParty\":true,\"guildId\":9," +
                        "\"inTrade\":false,\"unavailable\":[]}]}");
        try {
            FridaGameRuntimeClient client = new FridaGameRuntimeClient(
                    "127.0.0.1", server.getLocalPort(), 1000, 2000);

            GameRuntimeClient.OnlineSnapshot snapshot = client.onlineSnapshot(0, 50);

            Map<String, Object> request = JSON.parseObject(received.get(2, TimeUnit.SECONDS));
            assertEquals("online_snapshot", request.get("op"));
            assertEquals(0, ((Number) request.get("offset")).intValue());
            assertEquals(50, ((Number) request.get("limit")).intValue());
            assertEquals(1, snapshot.getTotal());
            assertFalse(snapshot.isTruncated());
            assertEquals(1, snapshot.getPlayers().size());
            assertEquals(23, snapshot.getPlayers().get(0).getCharacNo());
            assertEquals(Long.valueOf(3000000000L), snapshot.getPlayers().get(0).getGold());
            assertEquals(Integer.valueOf(156), snapshot.getPlayers().get(0).getMaxFatigue());
            assertTrue(snapshot.getPlayers().get(0).isInParty());
        } finally {
            server.close();
        }
    }

    @Test
    public void inspectsOneOnlinePlayerAndValidatesItsIdentity() throws Exception {
        ServerSocket server = new ServerSocket(0);
        FutureTask<String> received = respondOnce(server,
                "{\"ok\":true,\"protocolVersion\":2,\"accountId\":42,\"characNo\":23," +
                        "\"state\":3,\"level\":86,\"gold\":1200,\"inParty\":false," +
                        "\"inTrade\":true,\"unavailable\":[\"fatigue\"]}");
        try {
            FridaGameRuntimeClient client = new FridaGameRuntimeClient(
                    "127.0.0.1", server.getLocalPort(), 1000, 2000);

            GameRuntimeClient.PlayerSnapshot player = client.inspectPlayer(42, 23);

            Map<String, Object> request = JSON.parseObject(received.get(2, TimeUnit.SECONDS));
            assertEquals("inspect_player", request.get("op"));
            assertEquals(42, ((Number) request.get("accountId")).intValue());
            assertEquals(23, ((Number) request.get("characNo")).intValue());
            assertEquals(Integer.valueOf(86), player.getLevel());
            assertTrue(player.isInTrade());
            assertEquals(Arrays.asList("fatigue"), player.getUnavailable());
        } finally {
            server.close();
        }
    }

    @Test
    public void readsPartialRuntimeCurrenciesWithoutInventingUnavailableValues() throws Exception {
        ServerSocket server = new ServerSocket(0);
        respondOnce(server,
                "{\"ok\":true,\"protocolVersion\":2,\"accountId\":42,\"characNo\":23," +
                        "\"gold\":1200,\"cera\":99,\"ceraPoint\":null,\"winPoint\":7," +
                        "\"unavailable\":[\"ceraPoint\"]}");
        try {
            FridaGameRuntimeClient client = new FridaGameRuntimeClient(
                    "127.0.0.1", server.getLocalPort(), 1000, 2000);

            GameRuntimeClient.CurrencySnapshot snapshot = client.currencySnapshot(42, 23);

            assertEquals(Long.valueOf(1200), snapshot.getGold());
            assertEquals(Long.valueOf(99), snapshot.getCera());
            assertNull(snapshot.getCeraPoint());
            assertEquals(Long.valueOf(7), snapshot.getWinPoint());
            assertEquals(Arrays.asList("ceraPoint"), snapshot.getUnavailable());
        } finally {
            server.close();
        }
    }

    @Test
    public void changesGoldByAnExactSignedDelta() throws Exception {
        ServerSocket server = new ServerSocket(0);
        FutureTask<String> received = respondOnce(server,
                "{\"ok\":true,\"protocolVersion\":2,\"requestId\":\"gold-minus-1\"," +
                        "\"accountId\":42,\"characNo\":23,\"before\":1200," +
                        "\"delta\":-500,\"after\":700}");
        try {
            FridaGameRuntimeClient client = new FridaGameRuntimeClient(
                    "127.0.0.1", server.getLocalPort(), 1000, 2000);

            GameRuntimeClient.GoldChange change = client.changeGold("gold-minus-1", 42, 23, -500);

            Map<String, Object> request = JSON.parseObject(received.get(2, TimeUnit.SECONDS));
            assertEquals("change_gold", request.get("op"));
            assertEquals(-500, ((Number) request.get("delta")).intValue());
            assertEquals(-500L, change.getDelta());
            assertEquals(700L, change.getAfter());
        } finally {
            server.close();
        }
    }

    @Test
    public void readsInventorySlotsAndKeepsPerSlotErrorsIsolated() throws Exception {
        ServerSocket server = new ServerSocket(0);
        respondOnce(server,
                "{\"ok\":true,\"protocolVersion\":2,\"accountId\":42,\"characNo\":23," +
                        "\"scannedSlots\":685,\"totalOccupied\":1,\"truncated\":false," +
                        "\"accountCargoAvailable\":false,\"slots\":[{" +
                        "\"space\":1,\"slot\":4,\"itemId\":1001,\"addInfo\":3}]," +
                        "\"errors\":[{\"space\":1,\"slot\":7,\"code\":\"READ_FAILED\"}]}");
        try {
            FridaGameRuntimeClient client = new FridaGameRuntimeClient(
                    "127.0.0.1", server.getLocalPort(), 1000, 2000);

            GameRuntimeClient.InventorySnapshot snapshot = client.inventorySnapshot(42, 23);

            assertEquals(685, snapshot.getScannedSlots());
            assertEquals(1, snapshot.getSlots().size());
            assertEquals(1001, snapshot.getSlots().get(0).getItemId());
            assertEquals(1, snapshot.getErrors().size());
            assertEquals("READ_FAILED", snapshot.getErrors().get(0).getCode());
            assertFalse(snapshot.isAccountCargoAvailable());
        } finally {
            server.close();
        }
    }

    @Test
    public void readsEffectiveContractState() throws Exception {
        ServerSocket server = new ServerSocket(0);
        respondOnce(server,
                "{\"ok\":true,\"protocolVersion\":2,\"status\":\"AVAILABLE\"," +
                        "\"accountId\":42,\"characNo\":23,\"characLevel\":21," +
                        "\"configuredLevel\":10,\"hookInstalled\":true," +
                        "\"activePremiumTypes\":[22],\"levels\":[{" +
                        "\"equipmentType\":10,\"rawLevel\":5,\"effectiveLevel\":10}]}");
        try {
            FridaGameRuntimeClient client = new FridaGameRuntimeClient(
                    "127.0.0.1", server.getLocalPort(), 1000, 2000);

            GameRuntimeClient.ContractSnapshot snapshot = client.inspectContracts(42, 23);

            assertEquals("AVAILABLE", snapshot.getStatus());
            assertEquals(Integer.valueOf(10), snapshot.getConfiguredLevel());
            assertTrue(snapshot.isHookInstalled());
            assertEquals(Arrays.asList(22), snapshot.getActivePremiumTypes());
            assertEquals(10, snapshot.getLevels().get(0).getEffectiveLevel());
        } finally {
            server.close();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidRequestIdsBeforeOpeningASocket() {
        FridaGameRuntimeClient client = new FridaGameRuntimeClient("127.0.0.1", 1, 10, 10);
        client.changeGold("bad request id", 42, 23, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsGoldDeltasOutsideOneNativeSignedCall() {
        FridaGameRuntimeClient client = new FridaGameRuntimeClient("127.0.0.1", 1, 10, 10);
        client.changeGold("too-large", 42, 23, ((long) Integer.MAX_VALUE) + 1L);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsResponsesOverTheProtocolByteLimit() throws Exception {
        ServerSocket server = new ServerSocket(0);
        StringBuilder oversized = new StringBuilder(66000);
        for (int i = 0; i < 65537; i++) oversized.append('x');
        respondOnce(server, oversized.toString());
        try {
            FridaGameRuntimeClient client = new FridaGameRuntimeClient(
                    "127.0.0.1", server.getLocalPort(), 1000, 2000);
            client.ping();
        } finally {
            server.close();
        }
    }

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
