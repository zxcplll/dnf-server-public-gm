package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.exception.ValidationException;
import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.entity.gm.GmOperationContext;
import org.junit.After;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GmAuthorizationServiceTest {

    private static final long OPERATOR_UID = 100L;

    @After
    public void clearThreadContext() {
        ThreadUtil.setUserId(null);
        ThreadUtil.setRequestId(null);
    }

    @Test
    public void returnsCurrentHighestAdministrator() {
        AccountVO administrator = account(true);
        GmAuthorizationService service = service(administrator);
        ThreadUtil.setUserId(OPERATOR_UID);

        AccountVO actual = service.requireHighestAdmin();

        assertSame(administrator, actual);
    }

    @Test
    public void rejectsStandardBackOfficeUser() {
        GmAuthorizationService service = service(account(false));
        ThreadUtil.setUserId(OPERATOR_UID);

        try {
            service.requireHighestAdmin();
            fail("Expected a standard back-office user to be rejected");
        } catch (ValidationException expected) {
            assertTrue(expected.getMessage().contains("最高管理员"));
        }
    }

    @Test
    public void buildsOperationContextFromAuthenticatedOperatorAndDirectPeerIp() {
        GmAuthorizationService service = service(account(true));
        ThreadUtil.setUserId(OPERATOR_UID);
        ThreadUtil.setRequestId("req-100");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.7");
        request.addHeader("X-Forwarded-For", "203.0.113.99");

        GmOperationContext context = service.authorize(
                request, "currency", "change", 18000013L, 23);

        assertEquals("req-100", context.getRequestId());
        assertEquals(OPERATOR_UID, context.getOperatorUid());
        assertEquals("root-gm", context.getOperatorName());
        assertEquals("10.0.0.7", context.getClientIp());
        assertEquals("currency", context.getModule());
        assertEquals("change", context.getAction());
        assertEquals(Long.valueOf(18000013L), context.getTargetAccountId());
        assertEquals(Integer.valueOf(23), context.getTargetCharacNo());
    }

    private GmAuthorizationService service(AccountVO account) {
        AccountVODao accountVODao = mock(AccountVODao.class);
        when(accountVODao.get(OPERATOR_UID)).thenReturn(account);
        GmAuthorizationService service = new GmAuthorizationService();
        ReflectionTestUtils.setField(service, "accountVODao", accountVODao);
        return service;
    }

    private AccountVO account(boolean admin) {
        AccountVO account = new AccountVO();
        account.setUid(OPERATOR_UID);
        account.setAccountname("root-gm");
        account.setAdmin(admin);
        return account;
    }
}
