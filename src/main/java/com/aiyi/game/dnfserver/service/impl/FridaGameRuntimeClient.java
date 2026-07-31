package com.aiyi.game.dnfserver.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class FridaGameRuntimeClient implements GameRuntimeClient {

    private static final int PROTOCOL_VERSION = 2;
    private static final int MAX_REQUEST_BYTES = 4096;
    private static final int MAX_RESPONSE_BYTES = 65536;
    private static final int MAX_ONLINE_OFFSET = 512;
    private static final int MAX_ONLINE_LIMIT = 200;
    private static final int MAX_INVENTORY_RECORDS = 1024;
    private static final long MAX_UNSIGNED_INT = 0xffffffffL;
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,160}$");

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
    public RuntimePing ping() {
        JSONObject request = request("ping");
        JSONObject response = exchange(request);
        requireSuccess(response, "ping");
        requireProtocolV2(response);
        if (!"pong".equals(response.getString("op"))) {
            throw invalidResponse("ping response did not contain pong");
        }
        return new RuntimePing(PROTOCOL_VERSION, requiredStringList(response, "capabilities"));
    }

    @Override
    public OnlineSnapshot onlineSnapshot(int offset, int limit) {
        if (offset < 0 || offset > MAX_ONLINE_OFFSET) {
            throw new IllegalArgumentException("Runtime offset must be between 0 and " + MAX_ONLINE_OFFSET);
        }
        if (limit < 1 || limit > MAX_ONLINE_LIMIT) {
            throw new IllegalArgumentException("Runtime limit must be between 1 and " + MAX_ONLINE_LIMIT);
        }
        JSONObject request = request("online_snapshot");
        request.put("offset", offset);
        request.put("limit", limit);
        JSONObject response = exchange(request);
        requireSuccess(response, "online snapshot");
        requireProtocolV2(response);
        int responseOffset = requiredNonNegativeInt(response, "offset");
        int responseLimit = requiredPositiveInt(response, "limit");
        int total = requiredNonNegativeInt(response, "total");
        boolean truncated = requiredBoolean(response, "truncated");
        if (responseOffset != offset || responseLimit != limit) {
            throw invalidResponse("online snapshot pagination did not match the request");
        }
        JSONArray values = requiredArray(response, "players", MAX_ONLINE_LIMIT);
        if (values.size() > limit || total < values.size()) {
            throw invalidResponse("online snapshot counts are inconsistent");
        }
        List<PlayerSnapshot> players = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof JSONObject)) throw invalidResponse("online player was not an object");
            players.add(parsePlayer((JSONObject) value, null, null));
        }
        return new OnlineSnapshot(responseOffset, responseLimit, total, truncated, players);
    }

    @Override
    public PlayerSnapshot inspectPlayer(int accountId, int characNo) {
        validateIdentity(accountId, characNo);
        JSONObject request = targetedRequest("inspect_player", accountId, characNo);
        JSONObject response = exchange(request);
        requireSuccess(response, "player inspection");
        requireProtocolV2(response);
        return parsePlayer(response, accountId, characNo);
    }

    @Override
    public CurrencySnapshot currencySnapshot(int accountId, int characNo) {
        validateIdentity(accountId, characNo);
        JSONObject response = exchange(targetedRequest("currency_snapshot", accountId, characNo));
        requireSuccess(response, "currency snapshot");
        requireProtocolV2(response);
        requireIdentity(response, accountId, characNo);
        return new CurrencySnapshot(accountId, characNo,
                nullableUnsignedInt(response, "gold"),
                nullableUnsignedInt(response, "cera"),
                nullableUnsignedInt(response, "ceraPoint"),
                nullableUnsignedInt(response, "winPoint"),
                optionalStringList(response, "unavailable"));
    }

    @Override
    public GoldChange changeGold(String requestId, int accountId, int characNo, long delta) {
        validateRequestId(requestId);
        validateIdentity(accountId, characNo);
        if (delta == 0 || delta < -Integer.MAX_VALUE || delta > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Runtime gold delta must be a non-zero signed native integer");
        }
        JSONObject request = targetedRequest("change_gold", accountId, characNo);
        request.put("requestId", requestId);
        request.put("delta", delta);
        return executeGoldChange(request, requestId, accountId, characNo, delta, "delta", "gold change");
    }

    @Override
    public InventorySnapshot inventorySnapshot(int accountId, int characNo) {
        validateIdentity(accountId, characNo);
        JSONObject response = exchange(targetedRequest("inventory_snapshot", accountId, characNo));
        requireSuccess(response, "inventory snapshot");
        requireProtocolV2(response);
        requireIdentity(response, accountId, characNo);
        int scannedSlots = requiredNonNegativeInt(response, "scannedSlots");
        int totalOccupied = requiredNonNegativeInt(response, "totalOccupied");
        boolean truncated = requiredBoolean(response, "truncated");
        boolean accountCargoAvailable = requiredBoolean(response, "accountCargoAvailable");

        JSONArray rawSlots = requiredArray(response, "slots", MAX_INVENTORY_RECORDS);
        List<InventorySlot> slots = new ArrayList<>();
        for (Object value : rawSlots) {
            if (!(value instanceof JSONObject)) throw invalidResponse("inventory slot was not an object");
            JSONObject slot = (JSONObject) value;
            slots.add(new InventorySlot(requiredNonNegativeInt(slot, "space"),
                    requiredNonNegativeInt(slot, "slot"), requiredPositiveInt(slot, "itemId"),
                    requiredUnsignedInt(slot, "addInfo")));
        }
        if (totalOccupied < slots.size()) {
            throw invalidResponse("inventory occupied count is inconsistent");
        }

        JSONArray rawErrors = requiredArray(response, "errors", MAX_INVENTORY_RECORDS);
        List<InventoryReadError> errors = new ArrayList<>();
        for (Object value : rawErrors) {
            if (!(value instanceof JSONObject)) throw invalidResponse("inventory error was not an object");
            JSONObject error = (JSONObject) value;
            errors.add(new InventoryReadError(requiredNonNegativeInt(error, "space"),
                    requiredNonNegativeInt(error, "slot"), requiredAsciiString(error, "code", 64)));
        }
        return new InventorySnapshot(accountId, characNo, scannedSlots, totalOccupied, truncated,
                accountCargoAvailable, slots, errors);
    }

    @Override
    public ContractSnapshot inspectContracts(int accountId, int characNo) {
        validateIdentity(accountId, characNo);
        JSONObject response = exchange(targetedRequest("inspect_contracts", accountId, characNo));
        requireSuccess(response, "contract inspection");
        requireProtocolV2(response);
        requireIdentity(response, accountId, characNo);
        String status = requiredAsciiString(response, "status", 32);
        if (!"AVAILABLE".equals(status) && !"DEGRADED".equals(status)) {
            throw invalidResponse("contract status was invalid");
        }
        List<Integer> activePremiumTypes = requiredIntegerList(response, "activePremiumTypes", 64);
        JSONArray rawLevels = requiredArray(response, "levels", 64);
        List<ContractLevel> levels = new ArrayList<>();
        for (Object value : rawLevels) {
            if (!(value instanceof JSONObject)) throw invalidResponse("contract level was not an object");
            JSONObject level = (JSONObject) value;
            levels.add(new ContractLevel(requiredNonNegativeInt(level, "equipmentType"),
                    requiredNonNegativeInt(level, "rawLevel"),
                    requiredNonNegativeInt(level, "effectiveLevel")));
        }
        return new ContractSnapshot(status, accountId, characNo,
                nullableInt(response, "characLevel"), nullableInt(response, "configuredLevel"),
                requiredBoolean(response, "hookInstalled"), activePremiumTypes, levels);
    }

    @Override
    public OnlineCharacter findOnlineCharacter(int accountId) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("Runtime accountId must be positive");
        }
        JSONObject request = request("inspect_account");
        request.put("accountId", accountId);
        JSONObject response = exchange(request);
        requireSuccess(response, "account inspection");
        int responseAccountId = requiredInt(response, "accountId");
        int characNo = requiredPositiveInt(response, "characNo");
        long gold = requiredUnsignedInt(response, "gold");
        if (responseAccountId != accountId) {
            throw invalidResponse("account inspection returned a different account");
        }
        return new OnlineCharacter(responseAccountId, characNo, gold);
    }

    @Override
    public GoldChange addGold(String requestId, int accountId, int characNo, int amount) {
        validateRequestId(requestId);
        validateIdentity(accountId, characNo);
        if (amount <= 0) {
            throw new IllegalArgumentException("Runtime amount must be positive");
        }
        JSONObject request = targetedRequest("add_gold", accountId, characNo);
        request.put("requestId", requestId);
        request.put("amount", amount);
        return executeGoldChange(request, requestId, accountId, characNo, amount, "added", "gold reward");
    }

    private GoldChange executeGoldChange(JSONObject request, String requestId, int accountId,
                                         int characNo, long requestedDelta, String deltaField,
                                         String operation) {
        JSONObject response = exchange(request);
        requireSuccess(response, operation);
        if (!requestId.equals(response.getString("requestId"))) {
            throw invalidResponse(operation + " returned a mismatched requestId");
        }
        requireIdentity(response, accountId, characNo);
        long before = requiredUnsignedInt(response, "before");
        long delta = requiredSignedNativeInt(response, deltaField);
        long after = requiredUnsignedInt(response, "after");
        long expected = before + delta;
        if (delta != requestedDelta || expected < 0 || expected > MAX_UNSIGNED_INT || expected != after) {
            throw invalidResponse(operation + " delta was not exact: requested=" + requestedDelta +
                    ", before=" + before + ", delta=" + delta + ", after=" + after);
        }
        return new GoldChange(characNo, before, delta, after);
    }

    private PlayerSnapshot parsePlayer(JSONObject value, Integer expectedAccountId, Integer expectedCharacNo) {
        int accountId = requiredPositiveInt(value, "accountId");
        int characNo = requiredPositiveInt(value, "characNo");
        int state = requiredNonNegativeInt(value, "state");
        if (state < 3) throw invalidResponse("runtime returned a character below online state 3");
        if ((expectedAccountId != null && accountId != expectedAccountId) ||
                (expectedCharacNo != null && characNo != expectedCharacNo)) {
            throw invalidResponse("runtime returned a different account or character");
        }
        return new PlayerSnapshot(accountId, characNo, state,
                nullableInt(value, "level"), nullableInt(value, "job"),
                nullableInt(value, "growType"), nullableUnsignedInt(value, "gold"),
                nullableInt(value, "fatigue"), nullableInt(value, "maxFatigue"),
                nullableInt(value, "village"), nullableInt(value, "area"),
                nullableInt(value, "posX"), nullableInt(value, "posY"),
                optionalBoolean(value, "inParty"), nullableInt(value, "guildId"),
                optionalBoolean(value, "inTrade"), optionalStringList(value, "unavailable"));
    }

    private JSONObject request(String operation) {
        JSONObject request = new JSONObject(true);
        request.put("op", operation);
        return request;
    }

    private JSONObject targetedRequest(String operation, int accountId, int characNo) {
        JSONObject request = request(operation);
        request.put("accountId", accountId);
        request.put("characNo", characNo);
        return request;
    }

    private void validateIdentity(int accountId, int characNo) {
        if (accountId <= 0 || characNo <= 0) {
            throw new IllegalArgumentException("Runtime accountId and characNo must be positive");
        }
    }

    private void validateRequestId(String requestId) {
        if (requestId == null || !REQUEST_ID.matcher(requestId).matches()) {
            throw new IllegalArgumentException("Runtime requestId has an invalid format");
        }
    }

    private void requireSuccess(JSONObject response, String operation) {
        if (!response.containsKey("ok") || !(response.get("ok") instanceof Boolean)) {
            throw invalidResponse(operation + " response did not contain a boolean ok field");
        }
        if (!response.getBooleanValue("ok")) {
            String code = response.getString("code");
            String error = response.getString("error");
            throw new IllegalStateException("Frida runtime rejected " + operation +
                    (code == null ? "" : " [" + code + "]") + ": " + error);
        }
    }

    private void requireProtocolV2(JSONObject response) {
        if (requiredInt(response, "protocolVersion") != PROTOCOL_VERSION) {
            throw invalidResponse("unsupported protocol version");
        }
    }

    private void requireIdentity(JSONObject response, int accountId, int characNo) {
        if (requiredInt(response, "accountId") != accountId ||
                requiredInt(response, "characNo") != characNo) {
            throw invalidResponse("runtime returned a different account or character");
        }
    }

    private int requiredInt(JSONObject response, String field) {
        Object value = response.get(field);
        if (!(value instanceof Number)) throw invalidResponse("missing numeric field " + field);
        long number = ((Number) value).longValue();
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw invalidResponse("field outside signed integer range: " + field);
        }
        return (int) number;
    }

    private int requiredPositiveInt(JSONObject response, String field) {
        int value = requiredInt(response, field);
        if (value <= 0) throw invalidResponse("field must be positive: " + field);
        return value;
    }

    private int requiredNonNegativeInt(JSONObject response, String field) {
        int value = requiredInt(response, field);
        if (value < 0) throw invalidResponse("field must be non-negative: " + field);
        return value;
    }

    private Integer nullableInt(JSONObject response, String field) {
        Object value = response.get(field);
        if (value == null) return null;
        return requiredInt(response, field);
    }

    private long requiredUnsignedInt(JSONObject response, String field) {
        Object value = response.get(field);
        if (!(value instanceof Number)) throw invalidResponse("missing numeric field " + field);
        long number = ((Number) value).longValue();
        if (number < 0 || number > MAX_UNSIGNED_INT) {
            throw invalidResponse("field outside unsigned integer range: " + field);
        }
        return number;
    }

    private Long nullableUnsignedInt(JSONObject response, String field) {
        if (response.get(field) == null) return null;
        return requiredUnsignedInt(response, field);
    }

    private long requiredSignedNativeInt(JSONObject response, String field) {
        Object value = response.get(field);
        if (!(value instanceof Number)) throw invalidResponse("missing numeric field " + field);
        long number = ((Number) value).longValue();
        if (number < -Integer.MAX_VALUE || number > Integer.MAX_VALUE || number == 0) {
            throw invalidResponse("field outside supported signed native range: " + field);
        }
        return number;
    }

    private boolean requiredBoolean(JSONObject response, String field) {
        Object value = response.get(field);
        if (!(value instanceof Boolean)) throw invalidResponse("missing boolean field " + field);
        return (Boolean) value;
    }

    private boolean optionalBoolean(JSONObject response, String field) {
        Object value = response.get(field);
        if (value == null) return false;
        if (!(value instanceof Boolean)) throw invalidResponse("field was not boolean: " + field);
        return (Boolean) value;
    }

    private String requiredAsciiString(JSONObject response, String field, int maximumLength) {
        Object value = response.get(field);
        if (!(value instanceof String)) throw invalidResponse("missing string field " + field);
        String text = (String) value;
        if (text.isEmpty() || text.length() > maximumLength || !isAscii(text)) {
            throw invalidResponse("invalid ASCII string field " + field);
        }
        return text;
    }

    private JSONArray requiredArray(JSONObject response, String field, int maximumSize) {
        Object value = response.get(field);
        if (!(value instanceof JSONArray)) throw invalidResponse("missing array field " + field);
        JSONArray array = (JSONArray) value;
        if (array.size() > maximumSize) throw invalidResponse("array too large: " + field);
        return array;
    }

    private List<String> requiredStringList(JSONObject response, String field) {
        return parseStringList(requiredArray(response, field, 64), field);
    }

    private List<String> optionalStringList(JSONObject response, String field) {
        Object value = response.get(field);
        if (value == null) return Collections.emptyList();
        if (!(value instanceof JSONArray)) throw invalidResponse("field was not an array: " + field);
        JSONArray array = (JSONArray) value;
        if (array.size() > 64) throw invalidResponse("array too large: " + field);
        return parseStringList(array, field);
    }

    private List<String> parseStringList(JSONArray array, String field) {
        List<String> values = new ArrayList<>();
        for (Object value : array) {
            if (!(value instanceof String) || ((String) value).length() > 64 || !isAscii((String) value)) {
                throw invalidResponse("invalid string in array " + field);
            }
            values.add((String) value);
        }
        return values;
    }

    private List<Integer> requiredIntegerList(JSONObject response, String field, int maximumSize) {
        JSONArray array = requiredArray(response, field, maximumSize);
        List<Integer> values = new ArrayList<>();
        for (Object value : array) {
            if (!(value instanceof Number)) throw invalidResponse("invalid integer in array " + field);
            long number = ((Number) value).longValue();
            if (number < 0 || number > Integer.MAX_VALUE) {
                throw invalidResponse("integer outside range in array " + field);
            }
            values.add((int) number);
        }
        return values;
    }

    private JSONObject exchange(JSONObject request) {
        String requestText = request.toJSONString();
        if (!isAscii(requestText)) {
            throw new IllegalArgumentException("Frida runtime request must be ASCII");
        }
        byte[] requestBytes = requestText.getBytes(StandardCharsets.US_ASCII);
        if (requestBytes.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Frida runtime request exceeds " + MAX_REQUEST_BYTES + " bytes");
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
            socket.setSoTimeout(readTimeoutMillis);
            OutputStream output = socket.getOutputStream();
            output.write(requestBytes);
            output.write('\n');
            output.flush();
            String line = readAsciiLine(socket.getInputStream());
            if (line.trim().isEmpty()) throw invalidResponse("empty response");
            JSONObject response = JSON.parseObject(line);
            if (response == null) throw invalidResponse("invalid JSON");
            return response;
        } catch (IOException e) {
            throw new IllegalStateException("Frida runtime is unavailable at " + host + ":" + port, e);
        } catch (RuntimeException e) {
            if (e instanceof IllegalStateException) throw e;
            throw new IllegalStateException("Frida runtime returned invalid JSON", e);
        }
    }

    private String readAsciiLine(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while (true) {
            int value = input.read();
            if (value < 0) throw invalidResponse("response ended before LF");
            if (value == '\n') break;
            if (value > 0x7f) throw invalidResponse("response was not ASCII");
            if (output.size() >= MAX_RESPONSE_BYTES) {
                throw invalidResponse("response exceeds " + MAX_RESPONSE_BYTES + " bytes");
            }
            output.write(value);
        }
        byte[] bytes = output.toByteArray();
        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\r') length -= 1;
        return new String(bytes, 0, length, StandardCharsets.US_ASCII);
    }

    private boolean isAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 0x7f) return false;
        }
        return true;
    }

    private IllegalStateException invalidResponse(String message) {
        return new IllegalStateException("Frida runtime returned an invalid response: " + message);
    }
}
