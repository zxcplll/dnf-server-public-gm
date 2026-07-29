package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.exception.ValidationException;
import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import org.junit.After;
import org.junit.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GuildManagementServiceTest {

    private static final long ADMIN_UID = 100L;

    @After
    public void clearThreadUser() {
        ThreadUtil.setUserId(null);
    }

    private GuildManagementService service(JdbcTemplate jdbcTemplate, boolean admin) {
        GuildManagementService service = new GuildManagementService();
        AccountVODao accountVODao = mock(AccountVODao.class);
        AccountVO operator = new AccountVO();
        operator.setUid(ADMIN_UID);
        operator.setAccountname("GM");
        operator.setAdmin(admin);
        when(accountVODao.get(ADMIN_UID)).thenReturn(operator);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "accountVODao", accountVODao);
        ThreadUtil.setUserId(ADMIN_UID);
        return service;
    }

    private Map<String, Object> guildRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("guildId", 1);
        row.put("serverId", 3);
        row.put("guildName", "Test Guild");
        row.put("masterId", 18000013L);
        row.put("masterNo", 23);
        row.put("masterName", "Leader");
        row.put("level", 30);
        row.put("guildExp", 54L);
        row.put("fund", 403831200L);
        row.put("memberCount", 3);
        row.put("createTime", "2026-07-29 01:59:46");
        row.put("expireFlag", 0);
        return row;
    }

    private Map<String, Object> availableCharacterRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("memberId", 18000088L);
        row.put("characNo", 88);
        row.put("characName", "Free Member");
        row.put("accountname", "free-account");
        row.put("job", 0);
        row.put("growType", 19);
        row.put("level", 86);
        row.put("guildId", 0);
        return row;
    }

    @Test
    public void listsGuildsWithPagination() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GuildManagementService service = service(jdbcTemplate, true);
        when(jdbcTemplate.queryForObject(
                contains("COUNT(*) FROM d_guild.guild_info"),
                any(Object[].class),
                eq(Number.class)))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(
                contains("FROM d_guild.guild_info gi"),
                eq("%Test%"),
                eq("%Test%"),
                eq(20),
                eq(0)))
                .thenReturn(Collections.singletonList(guildRow()));

        Map<String, Object> result = service.listGuilds("Test", null, 1, 20);

        assertEquals(1, result.get("page"));
        assertEquals(20, result.get("pageSize"));
        assertEquals(1, result.get("totalSize"));
        assertEquals(1, ((java.util.List<?>) result.get("list")).size());
        Map<?, ?> guild = (Map<?, ?>) ((java.util.List<?>) result.get("list")).get(0);
        assertEquals("Test Guild", guild.get("guildName"));
        assertEquals(403831200L, guild.get("fund"));
    }

    @Test
    public void rejectsGuildAccessForNonAdminOperator() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GuildManagementService service = service(jdbcTemplate, false);

        try {
            service.listGuilds(null, null, 1, 20);
            fail("Expected non-admin guild access to be rejected");
        } catch (ValidationException expected) {
            assertTrue(expected.getMessage().contains("最高管理员"));
        }
        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class));
    }

    @Test
    public void updatesGuildLevelAndFundWithExpectedValues() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GuildManagementService service = service(jdbcTemplate, true);
        when(jdbcTemplate.queryForList(contains("FOR UPDATE"), eq(1)))
                .thenReturn(Collections.singletonList(guildRow()));
        when(jdbcTemplate.update(contains("UPDATE d_guild.guild_info"),
                eq(20), eq(20), eq(123456L), eq(1)))
                .thenReturn(1);
        when(jdbcTemplate.update(contains("UPDATE d_guild.guild_search"), eq(20), eq(1)))
                .thenReturn(0);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("level", 20);
        payload.put("fund", 123456L);
        payload.put("expectedLevel", 30);
        payload.put("expectedFund", 403831200L);

        Map<String, Object> result = service.updateGuild(1, payload);

        InOrder order = inOrder(jdbcTemplate);
        order.verify(jdbcTemplate).queryForList(contains("FOR UPDATE"), eq(1));
        order.verify(jdbcTemplate).update(
                contains("SET lev_up_time=CASE WHEN lev<>? THEN NOW() ELSE lev_up_time END,lev=?,guild_fund=?"),
                eq(20), eq(20), eq(123456L), eq(1));
        order.verify(jdbcTemplate).update(contains("UPDATE d_guild.guild_search"), eq(20), eq(1));
        assertEquals(20, result.get("level"));
        assertEquals(123456L, result.get("fund"));
    }

    @Test
    public void rejectsStaleGuildUpdate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GuildManagementService service = service(jdbcTemplate, true);
        when(jdbcTemplate.queryForList(contains("FOR UPDATE"), eq(1)))
                .thenReturn(Collections.singletonList(guildRow()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("level", 20);
        payload.put("fund", 123456L);
        payload.put("expectedLevel", 29);
        payload.put("expectedFund", 403831200L);

        try {
            service.updateGuild(1, payload);
            fail("Expected stale guild settings to be rejected");
        } catch (ValidationException expected) {
            assertTrue(expected.getMessage().contains("已被其他人修改"));
        }
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    public void rejectsGuildFundOutsideUnsignedIntRange() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GuildManagementService service = service(jdbcTemplate, true);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("level", 30);
        payload.put("fund", 4294967296L);
        payload.put("expectedLevel", 30);
        payload.put("expectedFund", 0);

        try {
            service.updateGuild(1, payload);
            fail("Expected an overflowing guild fund to be rejected");
        } catch (ValidationException expected) {
            assertTrue(expected.getMessage().contains("资金"));
        }
        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class));
    }

    @Test
    public void updatesNonLeaderMemberGradeAndWritesAuditLog() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GuildManagementService service = service(jdbcTemplate, true);
        when(jdbcTemplate.queryForList(contains("FROM d_guild.guild_info"), eq(1)))
                .thenReturn(Collections.singletonList(guildRow()));
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("guildId", 1);
        member.put("memberId", 18000013L);
        member.put("serverId", 3);
        member.put("characNo", 26);
        member.put("characName", "Member");
        member.put("grade", 3);
        member.put("level", 1);
        when(jdbcTemplate.queryForList(contains("FROM d_guild.guild_member gm"), eq(1), eq(26)))
                .thenReturn(Collections.singletonList(member));
        when(jdbcTemplate.update(contains("SET grade=?"), eq(2), eq(1), eq(26), eq(3)))
                .thenReturn(1);
        when(jdbcTemplate.update(contains("INSERT INTO d_guild.guild_grade_log"),
                eq(1), eq(18000013L), eq(3), eq(26), eq("Member"),
                eq(3), eq(2), eq("GM后台修改"), eq(ADMIN_UID), eq("GM")))
                .thenReturn(1);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("grade", 2);
        payload.put("expectedGrade", 3);

        Map<String, Object> result = service.updateMemberGrade(1, 26, payload);

        InOrder order = inOrder(jdbcTemplate);
        order.verify(jdbcTemplate).update(contains("SET grade=?"), eq(2), eq(1), eq(26), eq(3));
        order.verify(jdbcTemplate).update(contains("INSERT INTO d_guild.guild_grade_log"),
                eq(1), eq(18000013L), eq(3), eq(26), eq("Member"),
                eq(3), eq(2), eq("GM后台修改"), eq(ADMIN_UID), eq("GM"));
        assertEquals(2, result.get("grade"));
        assertEquals("副会长", result.get("gradeName"));
    }

    @Test
    public void rejectsChangingLeaderOrAssigningLeaderGrade() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GuildManagementService service = service(jdbcTemplate, true);
        when(jdbcTemplate.queryForList(contains("FROM d_guild.guild_info"), eq(1)))
                .thenReturn(Collections.singletonList(guildRow()));
        Map<String, Object> leader = new LinkedHashMap<>();
        leader.put("guildId", 1);
        leader.put("characNo", 23);
        leader.put("characName", "Leader");
        leader.put("grade", 1);
        when(jdbcTemplate.queryForList(contains("FROM d_guild.guild_member gm"), eq(1), eq(23)))
                .thenReturn(Collections.singletonList(leader));

        Map<String, Object> demote = new LinkedHashMap<>();
        demote.put("grade", 4);
        demote.put("expectedGrade", 1);
        try {
            service.updateMemberGrade(1, 23, demote);
            fail("Expected leader grade to be immutable");
        } catch (ValidationException expected) {
            assertTrue(expected.getMessage().contains("会长职级不可修改"));
        }

        Map<String, Object> assignLeader = new LinkedHashMap<>();
        assignLeader.put("grade", 1);
        assignLeader.put("expectedGrade", 3);
        try {
            service.updateMemberGrade(1, 26, assignLeader);
            fail("Expected grade 1 assignment to be rejected");
        } catch (ValidationException expected) {
            assertTrue(expected.getMessage().contains("只能设置为 2、3 或 4"));
        }
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    public void listsOnlyCharactersWithoutGuild() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GuildManagementService service = service(jdbcTemplate, true);
        when(jdbcTemplate.queryForObject(
                contains("FROM taiwan_cain.charac_info c"),
                eq(new Object[]{"%Free%", "%Free%", "Free", "Free"}),
                eq(Number.class)))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(
                contains("FROM taiwan_cain.charac_info c"),
                eq("%Free%"), eq("%Free%"), eq("Free"), eq("Free"), eq(20), eq(0)))
                .thenReturn(Collections.singletonList(availableCharacterRow()));

        Map<String, Object> result = service.listAvailableCharacters("Free", 1, 20);

        assertEquals(1, result.get("totalSize"));
        List<?> candidates = (List<?>) result.get("list");
        assertEquals(1, candidates.size());
        Map<?, ?> candidate = (Map<?, ?>) candidates.get(0);
        assertEquals(88, candidate.get("characNo"));
        assertEquals("Free Member", candidate.get("characName"));
        assertEquals("free-account", candidate.get("accountname"));
        verify(jdbcTemplate).queryForObject(
                contains("c.delete_flag=0 AND c.guild_id=0"),
                eq(new Object[]{"%Free%", "%Free%", "Free", "Free"}),
                eq(Number.class));
        verify(jdbcTemplate).queryForObject(
                contains("gm.member_flag=1 AND gm.secede_type=0"),
                eq(new Object[]{"%Free%", "%Free%", "Free", "Free"}),
                eq(Number.class));
        verify(jdbcTemplate).queryForObject(
                contains("EXISTS (SELECT 1 FROM taiwan_cain.charac_stat"),
                eq(new Object[]{"%Free%", "%Free%", "Free", "Free"}),
                eq(Number.class));
    }

    @Test
    public void addsAvailableCharacterToSelectedGuildInOneTransaction() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GuildManagementService service = service(jdbcTemplate, true);
        Map<String, Object> guild = guildRow();
        guild.put("guildId", 7);
        guild.put("serverId", 5);
        guild.put("memberCount", 2);
        when(jdbcTemplate.queryForList(
                contains("FROM taiwan_cain.charac_info c"),
                eq(88)))
                .thenReturn(Collections.singletonList(availableCharacterRow()));
        when(jdbcTemplate.queryForList(
                contains("FROM d_guild.guild_member WHERE charac_no=? FOR UPDATE"),
                eq(88)))
                .thenReturn(Collections.emptyList());
        when(jdbcTemplate.queryForList(contains("FROM d_guild.guild_info"), eq(7)))
                .thenReturn(Collections.singletonList(guild));
        when(jdbcTemplate.update(
                contains("UPDATE taiwan_cain.charac_info"),
                eq(7), eq(88)))
                .thenReturn(1);
        when(jdbcTemplate.update(contains("INSERT INTO d_guild.guild_member"),
                eq(7), eq(18000088L), eq(5), eq(88), eq("Free Member"), eq(0), eq(19), eq(86)))
                .thenReturn(1);
        when(jdbcTemplate.update(contains("DELETE FROM d_guild.guild_join_list"), eq(88)))
                .thenReturn(0);
        when(jdbcTemplate.update(contains("UPDATE d_guild.guild_info"), eq(7)))
                .thenReturn(1);
        when(jdbcTemplate.update(contains("UPDATE d_guild.guild_search"), eq(7), eq(7)))
                .thenReturn(0);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("characNo", 88);

        Map<String, Object> result = service.addMember(7, payload);

        InOrder order = inOrder(jdbcTemplate);
        order.verify(jdbcTemplate).queryForList(
                contains("EXISTS (SELECT 1 FROM taiwan_cain.charac_stat"), eq(88));
        order.verify(jdbcTemplate).queryForList(
                contains("FROM d_guild.guild_member WHERE charac_no=? FOR UPDATE"), eq(88));
        order.verify(jdbcTemplate).queryForList(contains("FROM d_guild.guild_info"), eq(7));
        order.verify(jdbcTemplate).update(contains("UPDATE taiwan_cain.charac_info"), eq(7), eq(88));
        order.verify(jdbcTemplate).update(contains("INSERT INTO d_guild.guild_member"),
                eq(7), eq(18000088L), eq(5), eq(88), eq("Free Member"), eq(0), eq(19), eq(86));
        order.verify(jdbcTemplate).update(contains("UPDATE d_guild.guild_info"), eq(7));
        order.verify(jdbcTemplate).update(contains("DELETE FROM d_guild.guild_join_list"), eq(88));
        order.verify(jdbcTemplate).update(contains("UPDATE d_guild.guild_search"), eq(7), eq(7));
        Map<?, ?> member = (Map<?, ?>) result.get("member");
        Map<?, ?> updatedGuild = (Map<?, ?>) result.get("guild");
        assertEquals(3, member.get("grade"));
        assertEquals("优秀", member.get("gradeName"));
        assertEquals(3, updatedGuild.get("memberCount"));
    }

    @Test
    public void rejectsAddingCharacterThatAlreadyHasGuild() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GuildManagementService service = service(jdbcTemplate, true);
        when(jdbcTemplate.queryForList(contains("FROM d_guild.guild_info"), eq(1)))
                .thenReturn(Collections.singletonList(guildRow()));
        Map<String, Object> member = availableCharacterRow();
        member.put("guildId", 9);
        when(jdbcTemplate.queryForList(
                contains("FROM taiwan_cain.charac_info c"),
                eq(88)))
                .thenReturn(Collections.singletonList(member));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("characNo", 88);

        try {
            service.addMember(1, payload);
            fail("Expected an already guilded character to be rejected");
        } catch (ValidationException expected) {
            assertTrue(expected.getMessage().contains("已有公会"));
        }
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    public void rejectsCharacterWithActiveGuildMembership() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GuildManagementService service = service(jdbcTemplate, true);
        when(jdbcTemplate.queryForList(
                contains("FROM taiwan_cain.charac_info c"),
                eq(88)))
                .thenReturn(Collections.singletonList(availableCharacterRow()));
        Map<String, Object> membership = new LinkedHashMap<>();
        membership.put("guildId", 9);
        membership.put("memberFlag", 1);
        membership.put("secedeType", 0);
        when(jdbcTemplate.queryForList(
                contains("FROM d_guild.guild_member WHERE charac_no=? FOR UPDATE"),
                eq(88)))
                .thenReturn(Collections.singletonList(membership));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("characNo", 88);

        try {
            service.addMember(1, payload);
            fail("Expected active guild membership to be rejected");
        } catch (ValidationException expected) {
            assertTrue(expected.getMessage().contains("已有公会"));
        }
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    public void reactivatesHistoricalMembershipInSelectedGuild() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GuildManagementService service = service(jdbcTemplate, true);
        Map<String, Object> guild = guildRow();
        guild.put("guildId", 7);
        guild.put("serverId", 5);
        guild.put("memberCount", 2);
        when(jdbcTemplate.queryForList(
                contains("FROM taiwan_cain.charac_info c"),
                eq(88)))
                .thenReturn(Collections.singletonList(availableCharacterRow()));
        Map<String, Object> historicalMembership = new LinkedHashMap<>();
        historicalMembership.put("guildId", 7);
        historicalMembership.put("memberFlag", 0);
        historicalMembership.put("secedeType", 1);
        historicalMembership.put("memberPoint", 55L);
        when(jdbcTemplate.queryForList(
                contains("FROM d_guild.guild_member WHERE charac_no=? FOR UPDATE"),
                eq(88)))
                .thenReturn(Collections.singletonList(historicalMembership));
        when(jdbcTemplate.queryForList(contains("FROM d_guild.guild_info"), eq(7)))
                .thenReturn(Collections.singletonList(guild));
        when(jdbcTemplate.update(
                contains("UPDATE taiwan_cain.charac_info"),
                eq(7), eq(88)))
                .thenReturn(1);
        when(jdbcTemplate.update(
                contains("UPDATE d_guild.guild_member SET m_id=?"),
                eq(18000088L), eq(5), eq("Free Member"), eq(0), eq(19), eq(86), eq(7), eq(88)))
                .thenReturn(1);
        when(jdbcTemplate.update(contains("UPDATE d_guild.guild_info"), eq(7)))
                .thenReturn(1);
        when(jdbcTemplate.update(contains("DELETE FROM d_guild.guild_join_list"), eq(88)))
                .thenReturn(0);
        when(jdbcTemplate.update(contains("UPDATE d_guild.guild_search"), eq(7), eq(7)))
                .thenReturn(0);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("characNo", 88);

        Map<String, Object> result = service.addMember(7, payload);

        verify(jdbcTemplate).update(
                contains("SET m_id=?,server_id=?,charac_name=?,grade=3"),
                eq(18000088L), eq(5), eq("Free Member"), eq(0), eq(19), eq(86), eq(7), eq(88));
        verify(jdbcTemplate, never()).update(contains("INSERT INTO d_guild.guild_member"), any(Object[].class));
        Map<?, ?> member = (Map<?, ?>) result.get("member");
        assertEquals(55L, member.get("memberPoint"));
        assertEquals(3, member.get("grade"));
    }
}
