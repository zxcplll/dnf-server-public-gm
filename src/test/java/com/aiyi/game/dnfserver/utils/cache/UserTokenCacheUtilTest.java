package com.aiyi.game.dnfserver.utils.cache;

import com.aiyi.game.dnfserver.conf.CommonAttr;
import com.aiyi.game.dnfserver.dao.User;
import org.junit.After;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class UserTokenCacheUtilTest {

    private static final long USER_ID = 918273645L;
    private final String firstToken = UUID.randomUUID().toString();
    private final String secondToken = UUID.randomUUID().toString();

    @After
    public void tearDown() {
        UserTokenCacheUtil.clear(USER_ID);
        CacheUtil.expire(Key.as(CommonAttr.CACHE.LOGIN_KEY, firstToken));
        CacheUtil.expire(Key.as(CommonAttr.CACHE.LOGIN_KEY, secondToken));
    }

    @Test
    public void keepsExistingSessionValidWhenTheSameAccountLogsInAgain() {
        User firstLogin = user();
        User secondLogin = user();

        UserTokenCacheUtil.putUserCache(firstToken, firstLogin);
        UserTokenCacheUtil.putUserCache(secondToken, secondLogin);

        User firstSession = UserTokenCacheUtil.getUser(firstToken);
        User secondSession = UserTokenCacheUtil.getUser(secondToken);

        assertNotNull("the first browser session should remain valid", firstSession);
        assertNotNull("the second browser session should be valid", secondSession);
        assertEquals(USER_ID, firstSession.getId());
        assertEquals(USER_ID, secondSession.getId());
    }

    private User user() {
        User user = new User();
        user.setId(USER_ID);
        user.setAccount("shared-admin");
        return user;
    }
}
