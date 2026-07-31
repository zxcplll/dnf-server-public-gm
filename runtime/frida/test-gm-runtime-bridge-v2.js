'use strict';

const assert = require('assert');
const fs = require('fs');
const vm = require('vm');

function pointer(name) {
    return {
        name,
        isNull: () => false
    };
}

const world = pointer('world');
const inventory = pointer('inventory');
const party = pointer('party');
const onlineUser = pointer('online-user');
const pendingUser = pointer('pending-user');
const users = {
    'online-user': {
        accountId: 42,
        characNo: 23,
        state: 3,
        level: 86,
        job: 1,
        growType: 2,
        fatigue: 12,
        maxFatigue: 156,
        village: 1,
        area: 2,
        posX: 3,
        posY: 4,
        guildId: 9,
        inTrade: false
    },
    'pending-user': {
        accountId: 43,
        characNo: 24,
        state: 2
    }
};
let gold = 1000;
let mainThreadCalls = 0;
let insideMainThread = false;
let gainCalls = 0;
let useCalls = 0;
let refreshCalls = 0;
let forcedUse = null;

function nativeValue(user, field) {
    assert.strictEqual(insideMainThread, true, field + ' must run on the main thread');
    return users[user.name][field];
}

global.G_GameWorld = () => {
    assert.strictEqual(insideMainThread, true);
    return world;
};
global.GameWorld_find_user_from_world_byaccid = (_world, accountId) => {
    assert.strictEqual(insideMainThread, true);
    return accountId === 42 ? onlineUser : { isNull: () => true };
};
let mapNextCalls = 0;
let onlineIteratorUsers = [onlineUser, pendingUser];
let reportedOnlineCount = 1;
let nonProgressIterator = false;
global.api_gameworld_user_map_begin = () => {
    assert.strictEqual(insideMainThread, true);
    return { index: 0 };
};
global.api_gameworld_user_map_end = () => {
    assert.strictEqual(insideMainThread, true);
    return { index: onlineIteratorUsers.length };
};
global.gameworld_user_map_not_equal = (left, right) => {
    assert.strictEqual(insideMainThread, true);
    return left.index !== right.index ? 1 : 0;
};
global.api_gameworld_user_map_get = iterator => {
    assert.strictEqual(insideMainThread, true);
    return onlineIteratorUsers[iterator.index];
};
global.api_gameworld_user_map_next = iterator => {
    assert.strictEqual(insideMainThread, true);
    mapNextCalls += 1;
    const previous = { index: iterator.index };
    if (!nonProgressIterator) iterator.index += 1;
    return previous;
};
global.GameWorld_get_UserCount_InWorld = () => {
    assert.strictEqual(insideMainThread, true);
    return reportedOnlineCount;
};
global.CUser_get_state = user => nativeValue(user, 'state');
global.CUser_get_acc_id = user => nativeValue(user, 'accountId');
global.CUserCharacInfo_getCurCharacNo = user => nativeValue(user, 'characNo');
global.CUserCharacInfo_get_charac_level = user => nativeValue(user, 'level');
global.CUserCharacInfo_get_charac_job = user => nativeValue(user, 'job');
global.CUserCharacInfo_getCurCharacGrowType = user => nativeValue(user, 'growType');
global.CUser_getCurCharacTotalFatigue = user => nativeValue(user, 'fatigue');
global.CUser_getCurCharacTotalMaxFatigue = user => nativeValue(user, 'maxFatigue');
global.CUser_GetCurCharacVill = user => nativeValue(user, 'village');
global.CUser_GetArea = user => nativeValue(user, 'area');
global.CUser_GetPosX = user => nativeValue(user, 'posX');
global.CUser_GetPosY = user => nativeValue(user, 'posY');
global.CUser_GetParty = user => nativeValue(user, 'accountId') === 42 ? party : { isNull: () => true };
global.CUserCharacInfo_get_charac_guildkey = user => nativeValue(user, 'guildId');
global.CUser_CheckInTrade = user => nativeValue(user, 'inTrade') ? 1 : 0;
global.CUserCharacInfo_getCurCharacInvenR = () => {
    assert.strictEqual(insideMainThread, true);
    return inventory;
};
global.CUserCharacInfo_getCurCharacInvenW = global.CUserCharacInfo_getCurCharacInvenR;
global.CInventory_get_money = () => {
    assert.strictEqual(insideMainThread, true);
    return gold;
};
global.CInventory_gain_money = (_inventory, amount) => {
    assert.strictEqual(insideMainThread, true);
    gainCalls += 1;
    gold += amount;
};
global.CInventory_use_money = (_inventory, amount) => {
    assert.strictEqual(insideMainThread, true);
    useCalls += 1;
    gold -= forcedUse === null ? amount : forcedUse;
};
global.CUser_send_itemspace = () => {
    assert.strictEqual(insideMainThread, true);
    refreshCalls += 1;
};
global.ENUM_ITEMSPACE_INVENTORY = 0;
global.CUser_GetCera = () => 99;
global.CUser_GetCeraPoint = () => 88;
global.CUser_GetWinPoint = () => 77;
global.CInventory_GetInvenRef = (_inventory, space, slot) => {
    assert.strictEqual(insideMainThread, true);
    if (space === 1 && slot === 7) throw new Error('bad slot');
    return { space, slot, isNull: () => false };
};
global.Inven_Item_isEmpty = item => item.space === 1 && item.slot === 4 ? 0 : 1;
global.Inven_Item_getKey = () => 1001;
global.Inven_Item_get_add_info = () => 3;
global.gmOverEquipContractInspectUser = user => {
    assert.strictEqual(insideMainThread, true);
    return {
        ok: true,
        accountId: nativeValue(user, 'accountId'),
        characNo: nativeValue(user, 'characNo'),
        characLevel: nativeValue(user, 'level'),
        configuredLevel: 10,
        hookInstalled: true,
        activePremiumTypes: [22],
        levels: [{ equipmentType: 10, rawLevel: 5, effectiveLevel: 10 }]
    };
};
global.api_scheduleOnMainThread = (callback, args) => {
    mainThreadCalls += 1;
    setImmediate(() => {
        insideMainThread = true;
        try {
            callback.apply(null, args || []);
        } finally {
            insideMainThread = false;
        }
    });
};

const bridge = fs.readFileSync(__dirname + '/gm-runtime-bridge-v2.js', 'utf8');
vm.runInThisContext(bridge, { filename: 'gm-runtime-bridge-v2.js' });

(async () => {
    const ping = await gmRuntimeBridgeV2Dispatch({ op: 'ping' });
    assert.strictEqual(ping.ok, true);
    assert.strictEqual(ping.protocolVersion, 2);
    assert.ok(ping.capabilities.includes('change_gold'));
    assert.strictEqual(mainThreadCalls, 0);

    const online = await gmRuntimeBridgeV2Dispatch({ op: 'online_snapshot', offset: 0, limit: 50 });
    assert.strictEqual(online.ok, true);
    assert.strictEqual(online.total, 1);
    assert.strictEqual(online.players.length, 1);
    assert.strictEqual(online.players[0].accountId, 42);
    assert.strictEqual(online.players[0].characNo, 23);
    assert.strictEqual(mapNextCalls, 2);
    assert.strictEqual(mainThreadCalls, 1);

    const player = await gmRuntimeBridgeV2Dispatch({
        op: 'inspect_player', accountId: 42, characNo: 23
    });
    assert.strictEqual(player.ok, true);
    assert.strictEqual(player.level, 86);
    assert.strictEqual(player.gold, 1000);
    assert.strictEqual(player.inParty, true);
    assert.strictEqual(mainThreadCalls, 2);

    const currencies = await gmRuntimeBridgeV2Dispatch({
        op: 'currency_snapshot', accountId: 42, characNo: 23
    });
    assert.strictEqual(currencies.gold, 1000);
    assert.strictEqual(currencies.cera, 99);
    assert.strictEqual(currencies.ceraPoint, 88);
    assert.strictEqual(currencies.winPoint, 77);
    assert.deepStrictEqual(currencies.unavailable, []);
    assert.strictEqual(mainThreadCalls, 3);

    const inventorySnapshot = await gmRuntimeBridgeV2Dispatch({
        op: 'inventory_snapshot', accountId: 42, characNo: 23
    });
    assert.strictEqual(inventorySnapshot.ok, true);
    assert.strictEqual(inventorySnapshot.scannedSlots, 686);
    assert.strictEqual(inventorySnapshot.totalOccupied, 1);
    assert.deepStrictEqual(inventorySnapshot.slots, [
        { space: 1, slot: 4, itemId: 1001, addInfo: 3 }
    ]);
    assert.deepStrictEqual(inventorySnapshot.errors, [
        { space: 1, slot: 7, code: 'READ_FAILED' }
    ]);
    assert.strictEqual(inventorySnapshot.accountCargoAvailable, false);
    assert.strictEqual(mainThreadCalls, 4);

    const changeRequest = {
        op: 'change_gold', requestId: 'gold-minus-1', accountId: 42, characNo: 23, delta: -500
    };
    const changed = await gmRuntimeBridgeV2Dispatch({ ...changeRequest });
    const duplicate = await gmRuntimeBridgeV2Dispatch({ ...changeRequest });
    assert.deepStrictEqual(duplicate, changed);
    assert.strictEqual(changed.ok, true);
    assert.strictEqual(changed.before, 1000);
    assert.strictEqual(changed.delta, -500);
    assert.strictEqual(changed.after, 500);
    assert.strictEqual(useCalls, 1);
    assert.strictEqual(refreshCalls, 1);
    assert.strictEqual(mainThreadCalls, 5);

    const conflict = await gmRuntimeBridgeV2Dispatch({
        ...changeRequest, delta: -400
    });
    assert.strictEqual(conflict.ok, false);
    assert.strictEqual(conflict.code, 'REQUEST_ID_CONFLICT');
    assert.strictEqual(useCalls, 1);
    assert.strictEqual(mainThreadCalls, 5);

    const legacy = await gmRuntimeBridgeV2Dispatch({
        op: 'add_gold', requestId: 'legacy-plus-1', accountId: 42, characNo: 23, amount: 200
    });
    assert.strictEqual(legacy.ok, true);
    assert.strictEqual(legacy.added, 200);
    assert.strictEqual(legacy.after, 700);
    assert.strictEqual(gainCalls, 1);
    assert.strictEqual(refreshCalls, 2);
    assert.strictEqual(mainThreadCalls, 6);

    const crossOperationConflict = await gmRuntimeBridgeV2Dispatch({
        op: 'change_gold', requestId: 'legacy-plus-1', accountId: 42, characNo: 23, delta: 200
    });
    assert.strictEqual(crossOperationConflict.ok, false);
    assert.strictEqual(crossOperationConflict.code, 'REQUEST_ID_CONFLICT');
    assert.strictEqual(mainThreadCalls, 6);

    gold = 1000;
    forcedUse = 100;
    const partialRequest = {
        op: 'change_gold', requestId: 'partial-minus-1', accountId: 42, characNo: 23, delta: -500
    };
    const partial = await gmRuntimeBridgeV2Dispatch({ ...partialRequest });
    const partialDuplicate = await gmRuntimeBridgeV2Dispatch({ ...partialRequest });
    assert.deepStrictEqual(partialDuplicate, partial);
    assert.strictEqual(partial.ok, false);
    assert.strictEqual(partial.mutated, true);
    assert.strictEqual(partial.delta, -100);
    assert.strictEqual(useCalls, 2);
    assert.strictEqual(mainThreadCalls, 7);
    forcedUse = null;

    gold = 0xffffffff;
    const overflow = await gmRuntimeBridgeV2Dispatch({
        op: 'change_gold', requestId: 'overflow-1', accountId: 42, characNo: 23, delta: 1
    });
    assert.strictEqual(overflow.ok, false);
    assert.strictEqual(overflow.code, 'BALANCE_OUT_OF_RANGE');
    assert.strictEqual(gainCalls, 1);
    assert.strictEqual(mainThreadCalls, 8);
    gold = 700;

    const contracts = await gmRuntimeBridgeV2Dispatch({
        op: 'inspect_contracts', accountId: 42, characNo: 23
    });
    assert.strictEqual(contracts.ok, true);
    assert.strictEqual(contracts.status, 'AVAILABLE');
    assert.deepStrictEqual(contracts.activePremiumTypes, [22]);
    assert.strictEqual(contracts.levels[0].effectiveLevel, 10);
    assert.strictEqual(mainThreadCalls, 9);

    const wrongCharacter = await gmRuntimeBridgeV2Dispatch({
        op: 'inspect_player', accountId: 42, characNo: 99
    });
    assert.strictEqual(wrongCharacter.ok, false);
    assert.strictEqual(wrongCharacter.code, 'CHARACTER_MISMATCH');
    assert.strictEqual(mainThreadCalls, 10);

    assert.throws(() => gmRuntimeBridgeV2AsciiBytes('not-ascii-\u00e9'), /ASCII/);
    assert.throws(() => gmRuntimeBridgeV2ValidateRequestId('bad request id'), /requestId/);
    const oversizedResponse = JSON.parse(gmRuntimeBridgeV2SerializeResponse({
        ok: true,
        payload: 'x'.repeat(GM_RUNTIME_BRIDGE_V2_MAX_RESPONSE_BYTES)
    }).trim());
    assert.strictEqual(oversizedResponse.code, 'RESPONSE_TOO_LARGE');
    await assert.rejects(() => gmRuntimeBridgeV2ReadRequest({
        read: async size => new Uint8Array(size).fill(65).buffer
    }), /too large/);

    // A stale host count must not manufacture online players when the map is empty.
    onlineIteratorUsers = [];
    reportedOnlineCount = 512;
    mapNextCalls = 0;
    const staleEmpty = await gmRuntimeBridgeV2Dispatch({
        op: 'online_snapshot', offset: 0, limit: 50
    });
    assert.strictEqual(staleEmpty.ok, true);
    assert.strictEqual(staleEmpty.total, 0);
    assert.strictEqual(staleEmpty.players.length, 0);
    assert.strictEqual(staleEmpty.truncated, false);
    assert.strictEqual(mapNextCalls, 0);

    // Repeated map entries for one role must be returned once, even with a stale count.
    onlineIteratorUsers = [onlineUser, onlineUser, pendingUser, onlineUser];
    reportedOnlineCount = 512;
    mapNextCalls = 0;
    const duplicateRoles = await gmRuntimeBridgeV2Dispatch({
        op: 'online_snapshot', offset: 0, limit: 50
    });
    assert.strictEqual(duplicateRoles.ok, true);
    assert.strictEqual(duplicateRoles.total, 1);
    assert.strictEqual(duplicateRoles.players.length, 1);
    assert.strictEqual(duplicateRoles.players[0].accountId, 42);
    assert.strictEqual(duplicateRoles.players[0].characNo, 23);
    assert.strictEqual(duplicateRoles.duplicateCount, 2);
    assert.strictEqual(duplicateRoles.truncated, false);
    assert.strictEqual(mapNextCalls, 4);

    // A broken next() implementation must terminate at the scan cap, not loop forever.
    onlineIteratorUsers = [onlineUser];
    reportedOnlineCount = 512;
    nonProgressIterator = true;
    mapNextCalls = 0;
    const stuckIterator = await gmRuntimeBridgeV2Dispatch({
        op: 'online_snapshot', offset: 0, limit: 50
    });
    assert.strictEqual(stuckIterator.ok, true);
    assert.strictEqual(stuckIterator.total, 1);
    assert.strictEqual(stuckIterator.players.length, 1);
    assert.strictEqual(stuckIterator.truncated, true);
    assert.strictEqual(stuckIterator.duplicateCount, 511);
    assert.strictEqual(mapNextCalls, 512);
    console.log('gm runtime bridge v2 tests passed');
})().catch(error => {
    console.error(error);
    process.exitCode = 1;
});
