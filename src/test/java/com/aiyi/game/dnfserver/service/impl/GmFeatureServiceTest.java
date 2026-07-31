package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.game.dnfserver.service.PostalService;
import com.aiyi.game.dnfserver.entity.Postal;
import com.aiyi.game.dnfserver.entity.common.ItemType;
import com.aiyi.game.dnfserver.entity.equipment.Equipment;
import com.aiyi.game.dnfserver.entity.stackable.Stackable;
import com.aiyi.game.dnfserver.dao.PostalDao;
import com.aiyi.game.dnfserver.dao.AccountDao;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.pvf.PvfManager;
import com.aiyi.core.util.thread.ThreadUtil;
import org.junit.Test;
import org.junit.After;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class GmFeatureServiceTest {

    private static final String LEGACY_ONLINE_REWARD_NAME = "\u00e6\u00b2\u00b9\u00e8\u2026\u00bb\u00e7\u0161\u201e\u00e5\u00b8\u02c6\u00e5\u00a7\u0090";

    @After
    public void clearThreadUser() {
        ThreadUtil.setUserId(null);
    }

    private void allowMailboxAdmin(GmFeatureService service) {
        AccountVODao accountVODao = mock(AccountVODao.class);
        AccountVO operator = new AccountVO();
        operator.setUid(100L);
        operator.setAdmin(true);
        when(accountVODao.get(100L)).thenReturn(operator);
        ReflectionTestUtils.setField(service, "accountVODao", accountVODao);
        ThreadUtil.setUserId(100L);
    }

    @Test
    public void playerMailboxSummaryReturnsCharacterAndMailCounts() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GmFeatureService service = new GmFeatureService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        allowMailboxAdmin(service);

        Map<String, Object> character = new LinkedHashMap<>();
        character.put("characNo", 23);
        character.put("characName", "礼帽型纽特");
        character.put("uid", 18000013L);
        character.put("accountname", "player");
        character.put("level", 19);
        when(jdbcTemplate.queryForList(contains("FROM taiwan_cain.charac_info"), eq(23)))
                .thenReturn(Collections.singletonList(character));
        when(jdbcTemplate.queryForObject(contains("FROM taiwan_cain_2nd.postal"), any(Object[].class), eq(Number.class)))
                .thenReturn(7);
        when(jdbcTemplate.queryForObject(contains("FROM taiwan_cain_2nd.letter"), any(Object[].class), eq(Number.class)))
                .thenReturn(3);

        Map<String, Object> result = service.getPlayerMailbox(23);

        assertEquals(23, result.get("characNo"));
        assertEquals("礼帽型纽特", result.get("characName"));
        assertEquals("player", result.get("accountname"));
        assertEquals(19, result.get("level"));
        assertEquals(7, result.get("mailCount"));
        assertEquals(3, result.get("letterCount"));
    }

    @Test(expected = com.aiyi.core.exception.ValidationException.class)
    public void clearPlayerMailboxRejectsUnknownCharacter() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GmFeatureService service = new GmFeatureService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        allowMailboxAdmin(service);
        when(jdbcTemplate.queryForList(contains("FROM taiwan_cain.charac_info"), eq(404)))
                .thenReturn(Collections.<Map<String, Object>>emptyList());

        service.clearPlayerMailbox(404);
    }

    @Test
    public void clearPlayerMailboxDeletesOwnedLettersBeforePostalEntries() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GmFeatureService service = new GmFeatureService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        allowMailboxAdmin(service);

        Map<String, Object> character = new LinkedHashMap<>();
        character.put("characNo", 23);
        character.put("characName", "礼帽型纽特");
        when(jdbcTemplate.queryForList(contains("FROM taiwan_cain.charac_info"), eq(23)))
                .thenReturn(Collections.singletonList(character));
        when(jdbcTemplate.update(contains("DELETE l FROM taiwan_cain_2nd.letter"), eq(23), eq(23), eq(23)))
                .thenReturn(2);
        when(jdbcTemplate.update(contains("DELETE FROM taiwan_cain_2nd.postal"), eq(23)))
                .thenReturn(5);

        Map<String, Object> result = service.clearPlayerMailbox(23);

        org.mockito.InOrder order = inOrder(jdbcTemplate);
        order.verify(jdbcTemplate).update(contains("DELETE l FROM taiwan_cain_2nd.letter"), eq(23), eq(23), eq(23));
        order.verify(jdbcTemplate).update(contains("DELETE FROM taiwan_cain_2nd.postal"), eq(23));
        assertEquals(5, result.get("deletedMailCount"));
        assertEquals(2, result.get("deletedLetterCount"));
        assertEquals(0, result.get("mailCount"));
        assertEquals(0, result.get("letterCount"));
        assertEquals("礼帽型纽特", result.get("characName"));
    }

    @Test
    public void clearPlayerMailboxRejectsCharacterOutsideOperatorScope() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AccountVODao accountVODao = mock(AccountVODao.class);
        GmFeatureService service = new GmFeatureService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "accountVODao", accountVODao);

        AccountVO operator = new AccountVO();
        operator.setUid(100L);
        operator.setAdmin(false);
        when(accountVODao.get(100L)).thenReturn(operator);
        when(jdbcTemplate.queryForList(
                contains("AND (c.m_id=? OR a.parent_uid=?)"),
                eq(23),
                eq(100L),
                eq(100L)))
                .thenReturn(Collections.<Map<String, Object>>emptyList());

        ThreadUtil.setUserId(100L);
        try {
            service.clearPlayerMailbox(23);
            fail("Expected an out-of-scope mailbox to be rejected");
        } catch (com.aiyi.core.exception.ValidationException expected) {
            assertTrue(expected.getMessage().contains("无权"));
        } finally {
            ThreadUtil.setUserId(null);
        }
        verify(jdbcTemplate).queryForList(
                contains("AND (c.m_id=? OR a.parent_uid=?)"),
                eq(23),
                eq(100L),
                eq(100L));
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    public void monitorUsesCharacterStatsForTodayActivityAndIncludesLevels() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GmFeatureService service = new GmFeatureService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        when(jdbcTemplate.queryForObject(anyString(), eq(Number.class))).thenReturn(0);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(Collections.<Map<String, Object>>emptyList());

        service.monitor();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).queryForList(sql.capture());
        List<String> queries = sql.getAllValues();
        assertTrue(queries.get(0).contains("c.lev AS level"));
        assertTrue(queries.get(1).contains("c.lev AS level"));
        assertTrue(queries.get(1).startsWith("SELECT DISTINCT"));
        assertTrue(queries.get(1).contains("JOIN taiwan_cain.charac_stat s ON s.charac_no=c.charac_no"));
        assertTrue(queries.get(1).contains("s.last_play_time >= CURDATE()"));
        assertTrue(queries.get(1).contains("ORDER BY s.last_play_time DESC"));
    }

    @Test
    public void monitorOnlyReturnsTheCharacterReportedOnlineByRuntime() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GameRuntimeClient gameRuntimeClient = mock(GameRuntimeClient.class);
        GmFeatureService service = new GmFeatureService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "gameRuntimeClient", gameRuntimeClient);
        when(jdbcTemplate.queryForObject(anyString(), eq(Number.class))).thenReturn(0);

        Map<String, Object> first = new LinkedHashMap<>();
        first.put("id", 22);
        first.put("name", "角色一");
        first.put("uid", 42);
        Map<String, Object> online = new LinkedHashMap<>();
        online.put("id", 23);
        online.put("name", "礼帽型纽特");
        online.put("uid", 42);
        Map<String, Object> third = new LinkedHashMap<>();
        third.put("id", 26);
        third.put("name", "角色三");
        third.put("uid", 42);

        when(jdbcTemplate.queryForList(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("login_account_1") && !sql.contains("charac_stat")) {
                return Arrays.asList(first, online, third);
            }
            return Collections.emptyList();
        });
        when(gameRuntimeClient.findOnlineCharacter(42))
                .thenReturn(new GameRuntimeClient.OnlineCharacter(42, 23, 0));

        Map<String, Object> result = service.monitor();
        Map<?, ?> overview = (Map<?, ?>) result.get("overview");
        List<?> rows = (List<?>) overview.get("online");

        assertEquals(1, rows.size());
        assertEquals(23, ((Map<?, ?>) rows.get(0)).get("id"));
        verify(gameRuntimeClient, times(1)).findOnlineCharacter(42);
    }

    @Test
    public void monitorReportsHostPhysicalMemoryCapacity() {
        java.lang.management.OperatingSystemMXBean os =
                java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        org.junit.Assume.assumeTrue(os instanceof com.sun.management.OperatingSystemMXBean);
        long expectedTotal = ((com.sun.management.OperatingSystemMXBean) os).getTotalPhysicalMemorySize();

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GmFeatureService service = new GmFeatureService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        when(jdbcTemplate.queryForObject(anyString(), eq(Number.class))).thenReturn(0);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(Collections.<Map<String, Object>>emptyList());

        Map<String, Object> result = service.monitor();
        Map<?, ?> current = (Map<?, ?>) result.get("current");

        assertEquals(expectedTotal, ((Number) current.get("memoryTotal")).longValue());
        assertTrue(((Number) current.get("memoryUsed")).longValue() <= expectedTotal);
    }

    @Test
    public void linuxMemoryUsageUsesMemAvailableInsteadOfMemFree() {
        GmFeatureService service = new GmFeatureService();
        List<String> meminfo = Arrays.asList(
                "MemTotal:       16384000 kB",
                "MemFree:          200000 kB",
                "MemAvailable:   12000000 kB",
                "Buffers:          100000 kB",
                "Cached:          4000000 kB"
        );

        long[] memory = ReflectionTestUtils.invokeMethod(service, "parseProcMeminfo", meminfo);

        assertEquals(16384000L * 1024L, memory[1]);
        assertEquals((16384000L - 12000000L) * 1024L, memory[0]);
    }

    @Test
    public void normalizesPersistedOnlineRewardProgressCharacterNames() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GmFeatureService service = new GmFeatureService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("characName", LEGACY_ONLINE_REWARD_NAME);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(Collections.singletonList(row));
        when(jdbcTemplate.queryForObject(anyString(), eq(Number.class))).thenReturn(0);

        Map<String, Object> result = service.getOnlineReward();
        List<?> progress = (List<?>) result.get("progress");

        assertEquals("\u6cb9\u817b\u7684\u5e08\u59d0", ((Map<?, ?>) progress.get(0)).get("characName"));
    }

    @Test
    public void normalizesPersistedOnlineRewardLogCharacterNames() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GmFeatureService service = new GmFeatureService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("characName", LEGACY_ONLINE_REWARD_NAME);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(Collections.singletonList(row));

        List<Map<String, Object>> logs = service.listOnlineRewardLogs(1, 20);

        assertEquals("\u6cb9\u817b\u7684\u5e08\u59d0", logs.get(0).get("characName"));
    }

    @Test
    public void sendsOnlineRewardGoldThroughGameRuntimeWithoutMailOrDatabaseWrite() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PostalService postalService = mock(PostalService.class);
        GameRuntimeClient gameRuntimeClient = mock(GameRuntimeClient.class);
        GmFeatureService service = new GmFeatureService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "postalService", postalService);
        ReflectionTestUtils.setField(service, "gameRuntimeClient", gameRuntimeClient);
        when(gameRuntimeClient.addGold("online-gold-1-1700000000000", 42, 1, 500))
                .thenReturn(new GameRuntimeClient.GoldChange(1, 1000, 500, 1500));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetType", "CHARACTERS");
        payload.put("characterIds", Arrays.asList(1));
        payload.put("items", Collections.emptyList());
        payload.put("gold", 500);
        payload.put("ceraPoint", 0);
        payload.put("directGold", true);
        payload.put("runtimeAccountId", 42);
        payload.put("runtimeRequestId", "online-gold-1-1700000000000");

        Map<String, Object> result = service.dispatchReward(payload, "online-reward");

        verify(gameRuntimeClient).addGold("online-gold-1-1700000000000", 42, 1, 500);
        verifyZeroInteractions(jdbcTemplate);
        verifyZeroInteractions(postalService);
        assertEquals(0, result.get("mailCount"));
        assertEquals(1500L, result.get("goldAfter"));
    }

    @Test
    public void scheduledOnlineRewardUsesAccountAndStableDueTimeForRuntimeRequest() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PostalService postalService = mock(PostalService.class);
        GameRuntimeClient gameRuntimeClient = mock(GameRuntimeClient.class);
        GmFeatureService service = new GmFeatureService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "postalService", postalService);
        ReflectionTestUtils.setField(service, "gameRuntimeClient", gameRuntimeClient);

        Date onlineSince = new Date(System.currentTimeMillis() - 300000L);
        Date lastAward = new Date(System.currentTimeMillis() - 120000L);
        long dueAt = lastAward.getTime() + 60000L;
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("enabled", 1);
        settings.put("interval_minutes", 1);
        settings.put("cera_point", 0);
        settings.put("gold", 500);
        Map<String, Object> player = new LinkedHashMap<>();
        player.put("characNo", 1);
        player.put("characName", "test");
        player.put("uid", 42);
        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("online_since", onlineSince);
        progress.put("last_award_at", lastAward);

        when(jdbcTemplate.queryForList(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("gm_online_reward_setting WHERE id=1")) {
                return Collections.singletonList(settings);
            }
            if (sql.contains("FROM taiwan_cain.charac_info c WHERE c.delete_flag=0")) {
                return Collections.singletonList(player);
            }
            if (sql.contains("SELECT online_since,last_award_at")) {
                return Collections.singletonList(progress);
            }
            return Collections.emptyList();
        });
        when(gameRuntimeClient.addGold("online-gold-1-" + dueAt, 42, 1, 500))
                .thenReturn(new GameRuntimeClient.GoldChange(1, 1000, 500, 1500));
        when(gameRuntimeClient.findOnlineCharacter(42))
                .thenReturn(new GameRuntimeClient.OnlineCharacter(42, 1, 1000));

        ReflectionTestUtils.invokeMethod(service, "processOnlineReward");

        verify(gameRuntimeClient).addGold("online-gold-1-" + dueAt, 42, 1, 500);
        verify(gameRuntimeClient).findOnlineCharacter(42);
        verifyZeroInteractions(postalService);
    }

    @Test
    public void scheduledOnlineRewardSkipsOtherCharactersOnTheSameOnlineAccount() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PostalService postalService = mock(PostalService.class);
        GameRuntimeClient gameRuntimeClient = mock(GameRuntimeClient.class);
        GmFeatureService service = new GmFeatureService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "postalService", postalService);
        ReflectionTestUtils.setField(service, "gameRuntimeClient", gameRuntimeClient);

        Date onlineSince = new Date(System.currentTimeMillis() - 300000L);
        Date lastAward = new Date(System.currentTimeMillis() - 120000L);
        long dueAt = lastAward.getTime() + 60000L;
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("enabled", 1);
        settings.put("interval_minutes", 1);
        settings.put("cera_point", 0);
        settings.put("gold", 500);
        Map<String, Object> active = new LinkedHashMap<>();
        active.put("characNo", 1);
        active.put("characName", "active");
        active.put("uid", 42);
        Map<String, Object> inactive = new LinkedHashMap<>();
        inactive.put("characNo", 2);
        inactive.put("characName", "inactive");
        inactive.put("uid", 42);
        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("online_since", onlineSince);
        progress.put("last_award_at", lastAward);

        when(jdbcTemplate.queryForList(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("gm_online_reward_setting WHERE id=1")) {
                return Collections.singletonList(settings);
            }
            if (sql.contains("FROM taiwan_cain.charac_info c WHERE c.delete_flag=0")) {
                return Arrays.asList(active, inactive);
            }
            if (sql.contains("SELECT online_since,last_award_at")) {
                return Collections.singletonList(progress);
            }
            return Collections.emptyList();
        });
        when(gameRuntimeClient.findOnlineCharacter(42))
                .thenReturn(new GameRuntimeClient.OnlineCharacter(42, 1, 1000));
        when(gameRuntimeClient.addGold("online-gold-1-" + dueAt, 42, 1, 500))
                .thenReturn(new GameRuntimeClient.GoldChange(1, 1000, 500, 1500));

        ReflectionTestUtils.invokeMethod(service, "processOnlineReward");

        verify(gameRuntimeClient, times(1)).findOnlineCharacter(42);
        verify(gameRuntimeClient).addGold("online-gold-1-" + dueAt, 42, 1, 500);
        verifyZeroInteractions(postalService);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMultiRecipientDirectRuntimeGold() {
        GmFeatureService service = new GmFeatureService();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetType", "CHARACTERS");
        payload.put("characterIds", Arrays.asList(1, 2));
        payload.put("items", Collections.emptyList());
        payload.put("gold", 500);
        payload.put("directGold", true);
        payload.put("runtimeAccountId", 42);
        payload.put("runtimeRequestId", "multi-role");

        service.dispatchReward(payload, "online-reward");
    }

    @Test
    public void sendsOnlineRewardCeraDirectlyToCashCeraWithoutTokenVoucher() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PostalService postalService = mock(PostalService.class);
        GmFeatureService service = new GmFeatureService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "postalService", postalService);
        when(jdbcTemplate.update(anyString(), eq(120), eq(1))).thenReturn(1);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetType", "CHARACTERS");
        payload.put("characterIds", Arrays.asList(1));
        payload.put("items", Collections.emptyList());
        payload.put("gold", 0);
        payload.put("ceraPoint", 120);
        payload.put("directCera", true);

        Map<String, Object> result = service.dispatchReward(payload, "online-reward");

        verify(jdbcTemplate).update(
                "UPDATE taiwan_billing.cash_cera p JOIN taiwan_cain.charac_info c ON CAST(p.account AS UNSIGNED)=c.m_id SET p.cera=LEAST(4294967295, p.cera+?),p.mod_date=NOW() WHERE c.charac_no=?",
                120,
                1);
        verifyZeroInteractions(postalService);
        assertEquals(0, result.get("mailCount"));
    }

    @Test
    public void carriesHighestGradeFlagToRewardMail() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PostalService postalService = mock(PostalService.class);
        PvfManager pvfManager = mock(PvfManager.class);
        GmFeatureService service = new GmFeatureService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "postalService", postalService);
        ReflectionTestUtils.setField(service, "pvfManager", pvfManager);
        Equipment equipment = new Equipment();
        equipment.setType(ItemType.equipment);
        when(pvfManager.findItem(1001)).thenReturn(equipment);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemId", 1001);
        item.put("quantity", 1);
        item.put("highestGrade", true);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetType", "CHARACTERS");
        payload.put("characterIds", Arrays.asList(1));
        payload.put("items", Arrays.asList(item));

        service.dispatchReward(payload, "gm");

        org.mockito.ArgumentCaptor<Postal> captor = org.mockito.ArgumentCaptor.forClass(Postal.class);
        verify(postalService).sendMail(captor.capture());
        assertEquals(true, captor.getValue().isHighestGrade());
        assertEquals(100, captor.getValue().getEndurance());
        assertEquals(1, captor.getValue().getAddInfo());
    }

    @Test
    public void postalServiceWritesTopQualityValueOnlyForEquipment() {
        PostalDao postalDao = mock(PostalDao.class);
        AccountDao accountDao = mock(AccountDao.class);
        PvfManager pvfManager = mock(PvfManager.class);
        PostalServiceImpl service = new PostalServiceImpl();
        ReflectionTestUtils.setField(service, "postalDao", postalDao);
        ReflectionTestUtils.setField(service, "accountDao", accountDao);
        ReflectionTestUtils.setField(service, "pvfManager", pvfManager);

        Equipment equipment = new Equipment();
        equipment.setType(ItemType.equipment);
        Stackable stackable = new Stackable();
        stackable.setType(ItemType.stackable);

        Postal equipmentMail = new Postal();
        equipmentMail.setItemId(1001);
        equipmentMail.setHighestGrade(true);
        when(pvfManager.findItem(1001)).thenReturn(equipment);
        service.sendMail(equipmentMail);
        assertEquals(100, equipmentMail.getEndurance());

        Postal stackableMail = new Postal();
        stackableMail.setItemId(2001);
        stackableMail.setHighestGrade(true);
        when(pvfManager.findItem(2001)).thenReturn(stackable);
        service.sendMail(stackableMail);
        assertEquals(0, stackableMail.getEndurance());
    }

    @Test
    public void backupUsesConfiguredRemoteDatabaseEndpoint() {
        GmFeatureService service = new GmFeatureService();
        ReflectionTestUtils.setField(service, "datasourceUrl",
                "jdbc:mysql://db.example.test:3300/game?useUnicode=true&characterEncoding=utf8");
        ReflectionTestUtils.setField(service, "datasourceUsername", "backup-user");
        ReflectionTestUtils.setField(service, "datasourcePassword", "backup-password");

        Object connection = ReflectionTestUtils.invokeMethod(service, "resolveBackupDatabaseConnection");

        assertEquals("db.example.test", ReflectionTestUtils.getField(connection, "host"));
        assertEquals(3300, ReflectionTestUtils.getField(connection, "port"));
        assertEquals("backup-user", ReflectionTestUtils.getField(connection, "username"));
        assertEquals("backup-password", ReflectionTestUtils.getField(connection, "password"));
    }
}
