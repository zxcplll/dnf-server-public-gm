package com.aiyi.game.dnfserver.conf;

import com.aiyi.core.util.thread.ThreadUtil;
import org.junit.Assert;
import org.junit.Test;

public class RequestThreadFilterConfTest {

    @Test
    public void afterCompletionClearsRequestThreadContext() throws Exception {
        ThreadUtil.setRequestId("request-1");
        ThreadUtil.setUserId(7L);
        ThreadUtil.setUserName("admin");
        ThreadUtil.setToken("token");
        ThreadUtil.setUserEntity(new Object());
        ThreadUtil.setCacheData("key", "value");

        new RequestThreadFilterConf().afterCompletion(null, null, null, null);

        Assert.assertNull(ThreadUtil.getRequestId());
        Assert.assertNull(ThreadUtil.getUserId());
        Assert.assertNull(ThreadUtil.getUserName());
        Assert.assertNull(ThreadUtil.getToken());
        Assert.assertNull(ThreadUtil.getUserEntity());
        Assert.assertNull(ThreadUtil.getCacheData("key"));
    }
}
