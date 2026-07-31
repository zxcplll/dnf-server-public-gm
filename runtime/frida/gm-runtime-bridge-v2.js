/*
 * GM runtime bridge v2 fragment for the current 86 server df_game_r.js.
 *
 * The host script supplies the native wrappers and api_scheduleOnMainThread.
 * Every GameWorld, CUser and CInventory operation below is dispatched through
 * that scheduler. The installer must call gmRuntimeBridgeV2Start() after the
 * dispatcher hook is ready.
 *
 * Wire protocol: one ASCII JSON request and response, each terminated by LF.
 */

var GM_RUNTIME_BRIDGE_V2_PROTOCOL_VERSION = 2;
var GM_RUNTIME_BRIDGE_V2_HOST = '127.0.0.1';
var GM_RUNTIME_BRIDGE_V2_PORT = 27043;
var GM_RUNTIME_BRIDGE_V2_MAX_REQUEST_BYTES = 4096;
var GM_RUNTIME_BRIDGE_V2_MAX_RESPONSE_BYTES = 65536;
var GM_RUNTIME_BRIDGE_V2_MAX_ONLINE_OFFSET = 512;
var GM_RUNTIME_BRIDGE_V2_MAX_ONLINE_LIMIT = 200;
var GM_RUNTIME_BRIDGE_V2_MAX_ONLINE_SCAN = 512;
var GM_RUNTIME_BRIDGE_V2_MAX_INVENTORY_ITEMS = 512;
var GM_RUNTIME_BRIDGE_V2_MAX_SLOT_ERRORS = 32;
var GM_RUNTIME_BRIDGE_V2_MAX_CACHE_ENTRIES = 4096;
var GM_RUNTIME_BRIDGE_V2_UINT32_BASE = 4294967296;
var GM_RUNTIME_BRIDGE_V2_UINT32_MAX = 4294967295;
var GM_RUNTIME_BRIDGE_V2_NATIVE_INT_MAX = 2147483647;

var gmRuntimeBridgeV2Listener = null;
var gmRuntimeBridgeV2Started = false;
var gmRuntimeBridgeV2Completed = Object.create(null);
var gmRuntimeBridgeV2InFlight = Object.create(null);
var gmRuntimeBridgeV2RequestOrder = [];
var gmRuntimeBridgeV2NativeCache = Object.create(null);
var gmRuntimeBridgeV2Root = typeof globalThis !== 'undefined' ? globalThis : this;

var GM_RUNTIME_BRIDGE_V2_CAPABILITIES = [
    'online_snapshot',
    'inspect_player',
    'currency_snapshot',
    'change_gold',
    'inventory_snapshot',
    'inspect_contracts',
    'inspect_account',
    'add_gold'
];

function gmRuntimeBridgeV2AsciiSafe(value, maximumLength) {
    var text = String(value === undefined || value === null ? '' : value);
    var result = '';
    for (var i = 0; i < text.length && result.length < maximumLength; i++) {
        var code = text.charCodeAt(i);
        result += code <= 0x7f ? text.charAt(i) : '?';
    }
    return result;
}

function gmRuntimeBridgeV2Error(code, message, requestId) {
    var result = {
        ok: false,
        protocolVersion: GM_RUNTIME_BRIDGE_V2_PROTOCOL_VERSION,
        code: gmRuntimeBridgeV2AsciiSafe(code, 64),
        error: gmRuntimeBridgeV2AsciiSafe(message, 240)
    };
    if (requestId !== undefined && requestId !== null) {
        result.requestId = gmRuntimeBridgeV2AsciiSafe(requestId, 160);
    }
    return result;
}

function gmRuntimeBridgeV2AsciiBytes(text) {
    var bytes = [];
    for (var i = 0; i < text.length; i++) {
        var code = text.charCodeAt(i);
        if (code > 0x7f) throw new Error('bridge data must be ASCII');
        bytes.push(code);
    }
    return bytes;
}

function gmRuntimeBridgeV2AsciiFromBuffer(buffer) {
    var bytes = new Uint8Array(buffer);
    var text = '';
    for (var i = 0; i < bytes.length; i++) {
        if (bytes[i] > 0x7f) throw new Error('request must be ASCII JSON');
        text += String.fromCharCode(bytes[i]);
    }
    return text;
}

async function gmRuntimeBridgeV2ReadRequest(input) {
    var text = '';
    while (text.length < GM_RUNTIME_BRIDGE_V2_MAX_REQUEST_BYTES) {
        var remaining = GM_RUNTIME_BRIDGE_V2_MAX_REQUEST_BYTES - text.length;
        var chunk = await input.read(Math.min(1024, remaining));
        if (chunk.byteLength === 0) break;
        text += gmRuntimeBridgeV2AsciiFromBuffer(chunk);
        var newline = text.indexOf('\n');
        if (newline !== -1) return text.substring(0, newline).replace(/\r$/, '');
    }
    throw new Error('request is missing LF terminator or is too large');
}

function gmRuntimeBridgeV2SerializeResponse(response) {
    var text = JSON.stringify(response);
    gmRuntimeBridgeV2AsciiBytes(text);
    if (text.length > GM_RUNTIME_BRIDGE_V2_MAX_RESPONSE_BYTES) {
        text = JSON.stringify(gmRuntimeBridgeV2Error(
            'RESPONSE_TOO_LARGE', 'response exceeded bridge byte limit', response && response.requestId));
    }
    return text + '\n';
}

function gmRuntimeBridgeV2ValidateInteger(value, field, minimum, maximum) {
    if (typeof value !== 'number' || !isFinite(value) || Math.floor(value) !== value ||
            value < minimum || value > maximum) {
        throw new Error(field + ' must be an integer between ' + minimum + ' and ' + maximum);
    }
    return value;
}

function gmRuntimeBridgeV2ValidatePositiveInt(value, field) {
    return gmRuntimeBridgeV2ValidateInteger(value, field, 1, GM_RUNTIME_BRIDGE_V2_NATIVE_INT_MAX);
}

function gmRuntimeBridgeV2ValidateRequestId(value) {
    if (typeof value !== 'string' || !/^[A-Za-z0-9._:-]{1,160}$/.test(value)) {
        throw new Error('requestId has an invalid format');
    }
    return value;
}

function gmRuntimeBridgeV2ToUnsigned(value, field) {
    if (typeof value !== 'number' || !isFinite(value) || Math.floor(value) !== value) {
        throw new Error(field + ' was not an integer');
    }
    var result = value < 0 ? value + GM_RUNTIME_BRIDGE_V2_UINT32_BASE : value;
    if (result < 0 || result > GM_RUNTIME_BRIDGE_V2_UINT32_MAX) {
        throw new Error(field + ' was outside uint32 range');
    }
    return result;
}

function gmRuntimeBridgeV2IsNull(value) {
    return value === null || value === undefined || value === 0 ||
        (typeof value.isNull === 'function' && value.isNull());
}

function gmRuntimeBridgeV2GlobalFunction(name) {
    var value = gmRuntimeBridgeV2Root && gmRuntimeBridgeV2Root[name];
    return typeof value === 'function' ? value : null;
}

function gmRuntimeBridgeV2ResolveNative(cacheKey, globalNames, symbolName, returnType, argumentTypes) {
    if (Object.prototype.hasOwnProperty.call(gmRuntimeBridgeV2NativeCache, cacheKey)) {
        return gmRuntimeBridgeV2NativeCache[cacheKey];
    }
    for (var i = 0; i < globalNames.length; i++) {
        var globalFunction = gmRuntimeBridgeV2GlobalFunction(globalNames[i]);
        if (globalFunction !== null) {
            gmRuntimeBridgeV2NativeCache[cacheKey] = globalFunction;
            return globalFunction;
        }
    }
    var resolved = null;
    try {
        if (typeof DebugSymbol !== 'undefined' && typeof NativeFunction === 'function') {
            var symbol = DebugSymbol.fromName(symbolName);
            if (symbol && symbol.address && !gmRuntimeBridgeV2IsNull(symbol.address)) {
                resolved = new NativeFunction(symbol.address, returnType, argumentTypes, { abi: 'sysv' });
            }
        }
    } catch (ignored) {
        resolved = null;
    }
    gmRuntimeBridgeV2NativeCache[cacheKey] = resolved;
    return resolved;
}

function gmRuntimeBridgeV2RunOnMainThread(operation, requestId) {
    return new Promise(function (resolve) {
        try {
            if (typeof api_scheduleOnMainThread !== 'function') {
                resolve(gmRuntimeBridgeV2Error('MAIN_THREAD_UNAVAILABLE',
                    'main thread scheduler is unavailable', requestId));
                return;
            }
            api_scheduleOnMainThread(function () {
                try {
                    resolve(operation());
                } catch (error) {
                    resolve(gmRuntimeBridgeV2Error('NATIVE_FAILURE', error.message || error, requestId));
                }
            }, null);
        } catch (error) {
            resolve(gmRuntimeBridgeV2Error('MAIN_THREAD_UNAVAILABLE', error.message || error, requestId));
        }
    });
}

function gmRuntimeBridgeV2FindUser(accountId, characNo) {
    var world = G_GameWorld();
    if (gmRuntimeBridgeV2IsNull(world)) {
        return { error: gmRuntimeBridgeV2Error('WORLD_UNAVAILABLE', 'game world is unavailable') };
    }
    var user = GameWorld_find_user_from_world_byaccid(world, accountId);
    if (gmRuntimeBridgeV2IsNull(user)) {
        return { error: gmRuntimeBridgeV2Error('ACCOUNT_OFFLINE', 'account is not online') };
    }
    var state = CUser_get_state(user);
    if (state < 3) {
        return { error: gmRuntimeBridgeV2Error('CHARACTER_NOT_READY', 'character state is below 3') };
    }
    var actualAccountId = CUser_get_acc_id(user);
    var actualCharacNo = CUserCharacInfo_getCurCharacNo(user);
    if (actualAccountId !== accountId) {
        return { error: gmRuntimeBridgeV2Error('ACCOUNT_MISMATCH', 'runtime account did not match') };
    }
    if (characNo !== null && actualCharacNo !== characNo) {
        return { error: gmRuntimeBridgeV2Error('CHARACTER_MISMATCH', 'online character did not match') };
    }
    return {
        user: user,
        accountId: actualAccountId,
        characNo: actualCharacNo,
        state: state
    };
}

function gmRuntimeBridgeV2OptionalValue(target, unavailable, field, operation, transform) {
    try {
        var value = operation();
        if (value === null || value === undefined) throw new Error('unavailable');
        target[field] = transform ? transform(value) : value;
    } catch (ignored) {
        target[field] = null;
        unavailable.push(field);
    }
}

function gmRuntimeBridgeV2ReadGold(user) {
    var inventory = CUserCharacInfo_getCurCharacInvenR(user);
    if (gmRuntimeBridgeV2IsNull(inventory)) throw new Error('inventory is unavailable');
    return gmRuntimeBridgeV2ToUnsigned(CInventory_get_money(inventory), 'gold');
}

function gmRuntimeBridgeV2Fatigue(user) {
    var fn = gmRuntimeBridgeV2ResolveNative('fatigue',
        ['CUser_getCurCharacTotalFatigue', 'CUser_GetFatigue'],
        '_ZNK5CUser24getCurCharacTotalFatigueEv', 'int', ['pointer']);
    if (fn === null) throw new Error('fatigue is unavailable');
    return fn(user);
}

function gmRuntimeBridgeV2MaxFatigue(user) {
    var fn = gmRuntimeBridgeV2ResolveNative('maxFatigue',
        ['CUser_getCurCharacTotalMaxFatigue', 'CUser_GetMaxFatigue'],
        '_ZNK5CUser27getCurCharacTotalMaxFatigueEv', 'int', ['pointer']);
    if (fn === null) throw new Error('max fatigue is unavailable');
    return fn(user);
}

function gmRuntimeBridgeV2Village(user) {
    var fn = gmRuntimeBridgeV2ResolveNative('village', ['CUser_GetCurCharacVill'],
        '_ZNK15CUserCharacInfo16getCurCharacVillEv', 'int', ['pointer']);
    if (fn === null) throw new Error('village is unavailable');
    return fn(user);
}

function gmRuntimeBridgeV2Area(user) {
    var fn = gmRuntimeBridgeV2ResolveNative('area', ['CUser_GetArea'],
        '_ZN5CUser8get_areaEb', 'int', ['pointer', 'int']);
    if (fn === null) throw new Error('area is unavailable');
    return fn(user, 0);
}

function gmRuntimeBridgeV2Position(user, axis) {
    var isX = axis === 'x';
    var fn = gmRuntimeBridgeV2ResolveNative(isX ? 'posX' : 'posY',
        [isX ? 'CUser_GetPosX' : 'CUser_GetPosY'],
        isX ? '_ZN5CUser8get_posXEv' : '_ZN5CUser8get_posYEv', 'int', ['pointer']);
    if (fn === null) throw new Error('position is unavailable');
    return fn(user);
}

function gmRuntimeBridgeV2Party(user) {
    var fn = null;
    if (typeof CUser_GetParty === 'function') fn = CUser_GetParty;
    if (fn === null) {
        fn = gmRuntimeBridgeV2ResolveNative('party', ['CUser_GetParty'],
            '_ZN5CUser8GetPartyEv', 'pointer', ['pointer']);
    }
    if (fn === null) throw new Error('party is unavailable');
    return !gmRuntimeBridgeV2IsNull(fn(user));
}

function gmRuntimeBridgeV2GuildId(user) {
    if (typeof CUserCharacInfo_get_charac_guildkey !== 'function') {
        throw new Error('guild is unavailable');
    }
    return CUserCharacInfo_get_charac_guildkey(user);
}

function gmRuntimeBridgeV2Trade(user) {
    var fn = null;
    if (typeof CUser_CheckInTrade === 'function') fn = CUser_CheckInTrade;
    if (fn === null) {
        fn = gmRuntimeBridgeV2ResolveNative('trade', ['CUser_CheckInTrade'],
            '_ZN5CUser12CheckInTradeEv', 'int', ['pointer']);
    }
    if (fn === null) throw new Error('trade is unavailable');
    return (fn(user) & 1) !== 0;
}

function gmRuntimeBridgeV2BuildPlayer(user) {
    var result = {
        accountId: CUser_get_acc_id(user),
        characNo: CUserCharacInfo_getCurCharacNo(user),
        state: CUser_get_state(user),
        unavailable: []
    };
    gmRuntimeBridgeV2OptionalValue(result, result.unavailable, 'level', function () {
        return CUserCharacInfo_get_charac_level(user);
    });
    gmRuntimeBridgeV2OptionalValue(result, result.unavailable, 'job', function () {
        return CUserCharacInfo_get_charac_job(user);
    });
    gmRuntimeBridgeV2OptionalValue(result, result.unavailable, 'growType', function () {
        return CUserCharacInfo_getCurCharacGrowType(user);
    });
    gmRuntimeBridgeV2OptionalValue(result, result.unavailable, 'gold', function () {
        return gmRuntimeBridgeV2ReadGold(user);
    });
    gmRuntimeBridgeV2OptionalValue(result, result.unavailable, 'fatigue', function () {
        return gmRuntimeBridgeV2Fatigue(user);
    });
    gmRuntimeBridgeV2OptionalValue(result, result.unavailable, 'maxFatigue', function () {
        return gmRuntimeBridgeV2MaxFatigue(user);
    });
    gmRuntimeBridgeV2OptionalValue(result, result.unavailable, 'village', function () {
        return gmRuntimeBridgeV2Village(user);
    });
    gmRuntimeBridgeV2OptionalValue(result, result.unavailable, 'area', function () {
        return gmRuntimeBridgeV2Area(user);
    });
    gmRuntimeBridgeV2OptionalValue(result, result.unavailable, 'posX', function () {
        return gmRuntimeBridgeV2Position(user, 'x');
    });
    gmRuntimeBridgeV2OptionalValue(result, result.unavailable, 'posY', function () {
        return gmRuntimeBridgeV2Position(user, 'y');
    });
    gmRuntimeBridgeV2OptionalValue(result, result.unavailable, 'inParty', function () {
        return gmRuntimeBridgeV2Party(user);
    });
    gmRuntimeBridgeV2OptionalValue(result, result.unavailable, 'guildId', function () {
        return gmRuntimeBridgeV2GuildId(user);
    });
    gmRuntimeBridgeV2OptionalValue(result, result.unavailable, 'inTrade', function () {
        return gmRuntimeBridgeV2Trade(user);
    });
    return result;
}

function gmRuntimeBridgeV2ValidateTarget(request) {
    return {
        accountId: gmRuntimeBridgeV2ValidatePositiveInt(request.accountId, 'accountId'),
        characNo: gmRuntimeBridgeV2ValidatePositiveInt(request.characNo, 'characNo')
    };
}

function gmRuntimeBridgeV2EnumerateOnlinePlayers() {
    if (typeof api_gameworld_user_map_begin !== 'function' ||
            typeof api_gameworld_user_map_end !== 'function' ||
            typeof api_gameworld_user_map_get !== 'function' ||
            typeof api_gameworld_user_map_next !== 'function' ||
            typeof gameworld_user_map_not_equal !== 'function') {
        throw new Error('online enumeration is unavailable');
    }
    var players = [];
    var activeCount = 0;
    var errors = 0;
    var scanned = 0;
    var traversalTruncated = false;
    var iterator = api_gameworld_user_map_begin();
    var end = api_gameworld_user_map_end();
    while (gameworld_user_map_not_equal(iterator, end)) {
        if (scanned >= GM_RUNTIME_BRIDGE_V2_MAX_ONLINE_SCAN) {
            traversalTruncated = true;
            break;
        }
        scanned += 1;
        try {
            var user = api_gameworld_user_map_get(iterator);
            if (!gmRuntimeBridgeV2IsNull(user) && CUser_get_state(user) >= 3) {
                activeCount += 1;
                players.push(gmRuntimeBridgeV2BuildPlayer(user));
            }
        } catch (ignored) {
            errors += 1;
        }
        iterator = api_gameworld_user_map_next(iterator);
    }
    var total = activeCount;
    try {
        if (typeof GameWorld_get_UserCount_InWorld === 'function') {
            var reported = GameWorld_get_UserCount_InWorld(G_GameWorld());
            if (typeof reported === 'number' && isFinite(reported) && reported >= total) {
                total = Math.floor(reported);
            }
        }
    } catch (ignored) {
    }
    return {
        players: players,
        total: total,
        errors: errors,
        traversalTruncated: traversalTruncated
    };
}

function gmRuntimeBridgeV2OnlineSnapshot(request) {
    var offset;
    var limit;
    try {
        offset = request.offset === undefined ? 0 : gmRuntimeBridgeV2ValidateInteger(
            request.offset, 'offset', 0, GM_RUNTIME_BRIDGE_V2_MAX_ONLINE_OFFSET);
        limit = request.limit === undefined ? 100 : gmRuntimeBridgeV2ValidateInteger(
            request.limit, 'limit', 1, GM_RUNTIME_BRIDGE_V2_MAX_ONLINE_LIMIT);
    } catch (error) {
        return Promise.resolve(gmRuntimeBridgeV2Error('INVALID_ARGUMENT', error.message || error));
    }
    return gmRuntimeBridgeV2RunOnMainThread(function () {
        var snapshot = gmRuntimeBridgeV2EnumerateOnlinePlayers();
        var players = snapshot.players;
        players.sort(function (left, right) {
            return left.characNo - right.characNo;
        });
        var page = players.slice(offset, offset + limit);
        return {
            ok: true,
            protocolVersion: GM_RUNTIME_BRIDGE_V2_PROTOCOL_VERSION,
            offset: offset,
            limit: limit,
            total: snapshot.total,
            truncated: snapshot.traversalTruncated || snapshot.total > players.length ||
                offset + page.length < players.length,
            errors: snapshot.errors,
            players: page
        };
    });
}

function gmRuntimeBridgeV2InspectPlayer(request) {
    var target;
    try {
        target = gmRuntimeBridgeV2ValidateTarget(request);
    } catch (error) {
        return Promise.resolve(gmRuntimeBridgeV2Error('INVALID_ARGUMENT', error.message || error));
    }
    return gmRuntimeBridgeV2RunOnMainThread(function () {
        var found = gmRuntimeBridgeV2FindUser(target.accountId, target.characNo);
        if (found.error) return found.error;
        var result = gmRuntimeBridgeV2BuildPlayer(found.user);
        result.ok = true;
        result.protocolVersion = GM_RUNTIME_BRIDGE_V2_PROTOCOL_VERSION;
        return result;
    });
}

function gmRuntimeBridgeV2InspectAccount(request) {
    var accountId;
    try {
        accountId = gmRuntimeBridgeV2ValidatePositiveInt(request.accountId, 'accountId');
    } catch (error) {
        return Promise.resolve(gmRuntimeBridgeV2Error('INVALID_ARGUMENT', error.message || error));
    }
    return gmRuntimeBridgeV2RunOnMainThread(function () {
        var found = gmRuntimeBridgeV2FindUser(accountId, null);
        if (found.error) return found.error;
        return {
            ok: true,
            protocolVersion: GM_RUNTIME_BRIDGE_V2_PROTOCOL_VERSION,
            accountId: found.accountId,
            characNo: found.characNo,
            gold: gmRuntimeBridgeV2ReadGold(found.user)
        };
    });
}

function gmRuntimeBridgeV2CurrencyFunction(cacheKey, globalNames, symbolName) {
    return gmRuntimeBridgeV2ResolveNative(cacheKey, globalNames, symbolName, 'int', ['pointer']);
}

function gmRuntimeBridgeV2CurrencySnapshot(request) {
    var target;
    try {
        target = gmRuntimeBridgeV2ValidateTarget(request);
    } catch (error) {
        return Promise.resolve(gmRuntimeBridgeV2Error('INVALID_ARGUMENT', error.message || error));
    }
    return gmRuntimeBridgeV2RunOnMainThread(function () {
        var found = gmRuntimeBridgeV2FindUser(target.accountId, target.characNo);
        if (found.error) return found.error;
        var result = {
            ok: true,
            protocolVersion: GM_RUNTIME_BRIDGE_V2_PROTOCOL_VERSION,
            accountId: found.accountId,
            characNo: found.characNo,
            unavailable: []
        };
        gmRuntimeBridgeV2OptionalValue(result, result.unavailable, 'gold', function () {
            return gmRuntimeBridgeV2ReadGold(found.user);
        });
        gmRuntimeBridgeV2OptionalValue(result, result.unavailable, 'cera', function () {
            var fn = gmRuntimeBridgeV2CurrencyFunction('cera', ['CUser_GetCera', 'CUser_getCera'],
                '_ZN5CUser7GetCeraEv');
            if (fn === null) throw new Error('cera is unavailable');
            return gmRuntimeBridgeV2ToUnsigned(fn(found.user), 'cera');
        });
        gmRuntimeBridgeV2OptionalValue(result, result.unavailable, 'ceraPoint', function () {
            var fn = gmRuntimeBridgeV2CurrencyFunction('ceraPoint',
                ['CUser_GetCeraPoint', 'CUser_getCeraPoint'], '_ZN5CUser12GetCeraPointEv');
            if (fn === null) throw new Error('cera point is unavailable');
            return gmRuntimeBridgeV2ToUnsigned(fn(found.user), 'ceraPoint');
        });
        gmRuntimeBridgeV2OptionalValue(result, result.unavailable, 'winPoint', function () {
            var fn = gmRuntimeBridgeV2CurrencyFunction('winPoint',
                ['CUser_GetWinPoint'], '_ZN15CUserCharacInfo11GetWinPointEv');
            if (fn === null) throw new Error('win point is unavailable');
            return gmRuntimeBridgeV2ToUnsigned(fn(found.user), 'winPoint');
        });
        return result;
    });
}

function gmRuntimeBridgeV2Remember(requestId, fingerprint, response) {
    gmRuntimeBridgeV2Completed[requestId] = {
        fingerprint: fingerprint,
        response: response
    };
    gmRuntimeBridgeV2RequestOrder.push(requestId);
    while (gmRuntimeBridgeV2RequestOrder.length > GM_RUNTIME_BRIDGE_V2_MAX_CACHE_ENTRIES) {
        var expired = gmRuntimeBridgeV2RequestOrder.shift();
        delete gmRuntimeBridgeV2Completed[expired];
    }
}

function gmRuntimeBridgeV2RefreshInventory(user) {
    if (typeof CUser_send_itemspace === 'function') {
        CUser_send_itemspace(user, typeof ENUM_ITEMSPACE_INVENTORY === 'number' ?
            ENUM_ITEMSPACE_INVENTORY : 0);
        return;
    }
    if (typeof CUser_SendItemSpace === 'function') {
        CUser_SendItemSpace(user, 0);
        return;
    }
    throw new Error('inventory refresh is unavailable');
}

function gmRuntimeBridgeV2ChangeGoldOnMainThread(target, requestId, delta, legacy) {
    var found = gmRuntimeBridgeV2FindUser(target.accountId, target.characNo);
    if (found.error) {
        found.error.requestId = requestId;
        return found.error;
    }
    try {
        if (gmRuntimeBridgeV2Trade(found.user)) {
            return gmRuntimeBridgeV2Error('CHARACTER_BUSY', 'character is trading', requestId);
        }
    } catch (ignored) {
    }
    var inventory = CUserCharacInfo_getCurCharacInvenW(found.user);
    if (gmRuntimeBridgeV2IsNull(inventory)) {
        return gmRuntimeBridgeV2Error('INVENTORY_UNAVAILABLE', 'inventory is unavailable', requestId);
    }
    var before = gmRuntimeBridgeV2ToUnsigned(CInventory_get_money(inventory), 'gold');
    var expected = before + delta;
    if (expected < 0 || expected > GM_RUNTIME_BRIDGE_V2_UINT32_MAX) {
        return gmRuntimeBridgeV2Error('BALANCE_OUT_OF_RANGE',
            'gold change would leave uint32 range', requestId);
    }
    if (delta > 0) {
        CInventory_gain_money(inventory, delta, 0, 0, 0);
    } else {
        CInventory_use_money(inventory, -delta, 0, 0);
    }
    gmRuntimeBridgeV2RefreshInventory(found.user);
    var after = gmRuntimeBridgeV2ToUnsigned(CInventory_get_money(inventory), 'gold');
    var actualDelta = after - before;
    var response = {
        ok: actualDelta === delta,
        protocolVersion: GM_RUNTIME_BRIDGE_V2_PROTOCOL_VERSION,
        requestId: requestId,
        accountId: target.accountId,
        characNo: target.characNo,
        before: before,
        delta: actualDelta,
        after: after
    };
    if (legacy) response.added = actualDelta;
    if (!response.ok) {
        response.code = 'DELTA_MISMATCH';
        response.error = 'runtime gold delta did not match requested delta';
        response.mutated = actualDelta !== 0;
    }
    return response;
}

function gmRuntimeBridgeV2ChangeGold(request, legacy) {
    var requestId;
    var target;
    var delta;
    try {
        requestId = gmRuntimeBridgeV2ValidateRequestId(request.requestId);
        target = gmRuntimeBridgeV2ValidateTarget(request);
        if (legacy) {
            delta = gmRuntimeBridgeV2ValidateInteger(
                request.amount, 'amount', 1, GM_RUNTIME_BRIDGE_V2_NATIVE_INT_MAX);
        } else {
            delta = gmRuntimeBridgeV2ValidateInteger(request.delta, 'delta',
                -GM_RUNTIME_BRIDGE_V2_NATIVE_INT_MAX, GM_RUNTIME_BRIDGE_V2_NATIVE_INT_MAX);
            if (delta === 0) throw new Error('delta must not be zero');
        }
    } catch (error) {
        return Promise.resolve(gmRuntimeBridgeV2Error(
            'INVALID_ARGUMENT', error.message || error, request && request.requestId));
    }
    var fingerprint = (legacy ? 'add_gold:' : 'change_gold:') +
        target.accountId + ':' + target.characNo + ':' + delta;
    var completed = gmRuntimeBridgeV2Completed[requestId];
    if (completed) {
        if (completed.fingerprint !== fingerprint) {
            return Promise.resolve(gmRuntimeBridgeV2Error(
                'REQUEST_ID_CONFLICT', 'requestId was already used for another change', requestId));
        }
        return Promise.resolve(completed.response);
    }
    var inFlight = gmRuntimeBridgeV2InFlight[requestId];
    if (inFlight) {
        if (inFlight.fingerprint !== fingerprint) {
            return Promise.resolve(gmRuntimeBridgeV2Error(
                'REQUEST_ID_CONFLICT', 'requestId is in flight for another change', requestId));
        }
        return inFlight.promise;
    }
    var promise = gmRuntimeBridgeV2RunOnMainThread(function () {
        return gmRuntimeBridgeV2ChangeGoldOnMainThread(target, requestId, delta, legacy);
    }, requestId).then(function (response) {
        if (response.ok || response.mutated === true) {
            gmRuntimeBridgeV2Remember(requestId, fingerprint, response);
        }
        delete gmRuntimeBridgeV2InFlight[requestId];
        return response;
    }, function (error) {
        delete gmRuntimeBridgeV2InFlight[requestId];
        return gmRuntimeBridgeV2Error('NATIVE_FAILURE', error.message || error, requestId);
    });
    gmRuntimeBridgeV2InFlight[requestId] = {
        fingerprint: fingerprint,
        promise: promise
    };
    return promise;
}

function gmRuntimeBridgeV2InventorySnapshot(request) {
    var target;
    try {
        target = gmRuntimeBridgeV2ValidateTarget(request);
    } catch (error) {
        return Promise.resolve(gmRuntimeBridgeV2Error('INVALID_ARGUMENT', error.message || error));
    }
    return gmRuntimeBridgeV2RunOnMainThread(function () {
        var found = gmRuntimeBridgeV2FindUser(target.accountId, target.characNo);
        if (found.error) return found.error;
        if (typeof CInventory_GetInvenRef !== 'function' ||
                typeof Inven_Item_isEmpty !== 'function' ||
                typeof Inven_Item_getKey !== 'function' ||
                typeof Inven_Item_get_add_info !== 'function') {
            return gmRuntimeBridgeV2Error('CAPABILITY_UNAVAILABLE',
                'inventory readers are unavailable');
        }
        var inventory = CUserCharacInfo_getCurCharacInvenR(found.user);
        if (gmRuntimeBridgeV2IsNull(inventory)) {
            return gmRuntimeBridgeV2Error('INVENTORY_UNAVAILABLE', 'inventory is unavailable');
        }
        var spaces = [
            { space: 0, count: 27 },
            { space: 1, count: 312 },
            { space: 2, count: 105 },
            { space: 3, count: 242 }
        ];
        var slots = [];
        var errors = [];
        var errorCount = 0;
        var scannedSlots = 0;
        var totalOccupied = 0;
        for (var spaceIndex = 0; spaceIndex < spaces.length; spaceIndex++) {
            var descriptor = spaces[spaceIndex];
            for (var slot = 0; slot < descriptor.count; slot++) {
                scannedSlots += 1;
                try {
                    var item = CInventory_GetInvenRef(inventory, descriptor.space, slot);
                    if (gmRuntimeBridgeV2IsNull(item) || Inven_Item_isEmpty(item) !== 0) continue;
                    var itemId = Inven_Item_getKey(item);
                    if (itemId <= 0 || itemId > GM_RUNTIME_BRIDGE_V2_NATIVE_INT_MAX) {
                        throw new Error('item id outside range');
                    }
                    totalOccupied += 1;
                    if (slots.length < GM_RUNTIME_BRIDGE_V2_MAX_INVENTORY_ITEMS) {
                        slots.push({
                            space: descriptor.space,
                            slot: slot,
                            itemId: itemId,
                            addInfo: gmRuntimeBridgeV2ToUnsigned(
                                Inven_Item_get_add_info(item), 'addInfo')
                        });
                    }
                } catch (ignored) {
                    errorCount += 1;
                    if (errors.length < GM_RUNTIME_BRIDGE_V2_MAX_SLOT_ERRORS) {
                        errors.push({ space: descriptor.space, slot: slot, code: 'READ_FAILED' });
                    }
                }
            }
        }
        return {
            ok: true,
            protocolVersion: GM_RUNTIME_BRIDGE_V2_PROTOCOL_VERSION,
            accountId: found.accountId,
            characNo: found.characNo,
            scannedSlots: scannedSlots,
            totalOccupied: totalOccupied,
            truncated: totalOccupied > slots.length || errorCount > errors.length,
            accountCargoAvailable: false,
            errorCount: errorCount,
            slots: slots,
            errors: errors
        };
    });
}

function gmRuntimeBridgeV2InspectContracts(request) {
    var target;
    try {
        target = gmRuntimeBridgeV2ValidateTarget(request);
    } catch (error) {
        return Promise.resolve(gmRuntimeBridgeV2Error('INVALID_ARGUMENT', error.message || error));
    }
    return gmRuntimeBridgeV2RunOnMainThread(function () {
        var found = gmRuntimeBridgeV2FindUser(target.accountId, target.characNo);
        if (found.error) return found.error;
        var inspectionFunction = null;
        if (typeof gmOverEquipContractInspectUser === 'function') {
            inspectionFunction = gmOverEquipContractInspectUser;
        } else {
            inspectionFunction = gmRuntimeBridgeV2GlobalFunction('gmOverEquipContractInspectUser');
        }
        if (inspectionFunction !== null) {
            var inspected = inspectionFunction(found.user);
            if (!inspected || inspected.ok !== true) {
                return gmRuntimeBridgeV2Error('CONTRACT_INSPECTION_FAILED',
                    inspected && inspected.error ? inspected.error : 'contract inspection failed');
            }
            if (inspected.accountId !== found.accountId || inspected.characNo !== found.characNo) {
                return gmRuntimeBridgeV2Error('CHARACTER_MISMATCH',
                    'contract inspection returned another character');
            }
            return {
                ok: true,
                protocolVersion: GM_RUNTIME_BRIDGE_V2_PROTOCOL_VERSION,
                status: 'AVAILABLE',
                accountId: found.accountId,
                characNo: found.characNo,
                characLevel: inspected.characLevel,
                configuredLevel: inspected.configuredLevel,
                hookInstalled: inspected.hookInstalled === true,
                activePremiumTypes: inspected.activePremiumTypes || [],
                levels: inspected.levels || []
            };
        }

        var active = [];
        var affected = gmRuntimeBridgeV2ResolveNative('affectedPremium',
            ['CUser_IsAffectedPremium', 'GM_OVER_EQUIP_IS_AFFECTED_PREMIUM'],
            '_ZNK5CUser17isAffectedPremiumE17ENUM_PREMIUM_TYPE', 'int', ['pointer', 'int']);
        if (affected !== null) {
            [9, 10, 11, 22].forEach(function (premiumType) {
                if (affected(found.user, premiumType) === 1) active.push(premiumType);
            });
        }
        var level = null;
        try {
            level = CUserCharacInfo_get_charac_level(found.user);
        } catch (ignored) {
        }
        var configured = typeof gmOverEquipContractConfiguredLevel === 'number' ?
            gmOverEquipContractConfiguredLevel : null;
        var installed = typeof gmOverEquipContractReplaced === 'boolean' ?
            gmOverEquipContractReplaced : false;
        return {
            ok: true,
            protocolVersion: GM_RUNTIME_BRIDGE_V2_PROTOCOL_VERSION,
            status: 'DEGRADED',
            accountId: found.accountId,
            characNo: found.characNo,
            characLevel: level,
            configuredLevel: configured,
            hookInstalled: installed,
            activePremiumTypes: active,
            levels: []
        };
    });
}

function gmRuntimeBridgeV2Dispatch(request) {
    if (!request || typeof request !== 'object' || Array.isArray(request)) {
        return Promise.resolve(gmRuntimeBridgeV2Error('INVALID_REQUEST', 'request must be an object'));
    }
    switch (request.op) {
        case 'ping':
            return Promise.resolve({
                ok: true,
                op: 'pong',
                protocolVersion: GM_RUNTIME_BRIDGE_V2_PROTOCOL_VERSION,
                capabilities: GM_RUNTIME_BRIDGE_V2_CAPABILITIES.slice()
            });
        case 'online_snapshot':
            return gmRuntimeBridgeV2OnlineSnapshot(request);
        case 'inspect_player':
            return gmRuntimeBridgeV2InspectPlayer(request);
        case 'currency_snapshot':
            return gmRuntimeBridgeV2CurrencySnapshot(request);
        case 'change_gold':
            return gmRuntimeBridgeV2ChangeGold(request, false);
        case 'inventory_snapshot':
            return gmRuntimeBridgeV2InventorySnapshot(request);
        case 'inspect_contracts':
            return gmRuntimeBridgeV2InspectContracts(request);
        case 'inspect_account':
            return gmRuntimeBridgeV2InspectAccount(request);
        case 'add_gold':
            return gmRuntimeBridgeV2ChangeGold(request, true);
        default:
            return Promise.resolve(gmRuntimeBridgeV2Error(
                'UNSUPPORTED_OPERATION', 'unsupported operation', request.requestId));
    }
}

async function gmRuntimeBridgeV2HandleConnection(connection) {
    try {
        await connection.setNoDelay(true);
        var rawRequest = await gmRuntimeBridgeV2ReadRequest(connection.input);
        var request;
        try {
            request = JSON.parse(rawRequest);
        } catch (ignored) {
            await connection.output.writeAll(gmRuntimeBridgeV2AsciiBytes(
                gmRuntimeBridgeV2SerializeResponse(
                    gmRuntimeBridgeV2Error('INVALID_JSON', 'invalid JSON'))));
            return;
        }
        var response = await gmRuntimeBridgeV2Dispatch(request);
        await connection.output.writeAll(gmRuntimeBridgeV2AsciiBytes(
            gmRuntimeBridgeV2SerializeResponse(response)));
    } catch (error) {
        console.log('[gm-runtime-v2] connection error: ' +
            gmRuntimeBridgeV2AsciiSafe(error.message || error, 240));
    } finally {
        try {
            await connection.close();
        } catch (ignored) {
        }
    }
}

async function gmRuntimeBridgeV2AcceptLoop(listener) {
    while (true) {
        var connection;
        try {
            connection = await listener.accept();
        } catch (error) {
            console.log('[gm-runtime-v2] listener stopped: ' +
                gmRuntimeBridgeV2AsciiSafe(error.message || error, 240));
            return;
        }
        gmRuntimeBridgeV2HandleConnection(connection).catch(function (error) {
            console.log('[gm-runtime-v2] handler failed: ' +
                gmRuntimeBridgeV2AsciiSafe(error.message || error, 240));
        });
    }
}

function gmRuntimeBridgeV2Start() {
    if (gmRuntimeBridgeV2Started) return;
    gmRuntimeBridgeV2Started = true;
    Socket.listen({
        family: 'ipv4',
        host: GM_RUNTIME_BRIDGE_V2_HOST,
        port: GM_RUNTIME_BRIDGE_V2_PORT,
        backlog: 16
    }).then(function (listener) {
        gmRuntimeBridgeV2Listener = listener;
        console.log('[gm-runtime-v2] listening on ' +
            GM_RUNTIME_BRIDGE_V2_HOST + ':' + listener.port);
        gmRuntimeBridgeV2AcceptLoop(listener);
    }).catch(function (error) {
        gmRuntimeBridgeV2Started = false;
        console.log('[gm-runtime-v2] listen failed: ' +
            gmRuntimeBridgeV2AsciiSafe(error.message || error, 240));
    });
}

function gmRuntimeBridgeV2Stop() {
    if (gmRuntimeBridgeV2Listener !== null) {
        gmRuntimeBridgeV2Listener.close();
        gmRuntimeBridgeV2Listener = null;
    }
    gmRuntimeBridgeV2Started = false;
}
