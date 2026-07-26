package com.aiyi.game.dnfserver.utils.cache;

import com.aiyi.core.beans.Method;
import com.aiyi.core.sql.where.C;
import com.aiyi.game.dnfserver.conf.CommonAttr;
import com.aiyi.game.dnfserver.dao.User;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @Author: 郭胜凯
 * @Date: 2020/10/9 17:22
 * @Email 719348277@qq.com
 * @Description:
 */
public class UserTokenCacheUtil {

    /**
     * 缓存时间(单位: 小时)
     */
    private static final int expire = 2;


    /**
     * 缓存一个token和用户的对应关系
     * @param token
     *      登录令牌
     * @param user
     *      用户对象
     */
    public static synchronized void putUserCache(String token, User user){
        // 缓存2小时
        CacheUtil.put(Key.as(CommonAttr.CACHE.LOGIN_KEY, token), user, TimeUnit.HOURS, expire);
        Set<String> tokens = getTokens(user.getId());
        tokens.add(token);
        CacheUtil.put(userTokenKey(user.getId()), tokens, TimeUnit.HOURS, expire);
    }

    /**
     * 清除用户的登录缓存
     * @param userId
     *      对应的用户ID
     */
    public static synchronized void clear(long userId) {
        Set<String> tokens = getTokens(userId);
        for (String token : tokens) {
            CacheUtil.expire(Key.as(CommonAttr.CACHE.LOGIN_KEY, token));
        }
        CacheUtil.expire(userTokenKey(userId));
    }

    /**
     * 更新缓存中的用户(token对应的用户对象)
     * @param user
     *      新的用户对象
     */
    public static synchronized void updateCacheUser(User user) {
        Set<String> activeTokens = new HashSet<>();
        for (String token : getTokens(user.getId())) {
            if (null != getUser(token)) {
                CacheUtil.put(Key.as(CommonAttr.CACHE.LOGIN_KEY, token), user, TimeUnit.HOURS, expire);
                activeTokens.add(token);
            }
        }
        if (activeTokens.isEmpty()) {
            CacheUtil.expire(userTokenKey(user.getId()));
        } else {
            CacheUtil.put(userTokenKey(user.getId()), activeTokens, TimeUnit.HOURS, expire);
        }
    }

    /**
     * 通过Token获得用户信息
     * @param token
     *      token
     * @return
     */
    public static User getUser(String token){
        if (StringUtils.isEmpty(token)){
            return null;
        }
        return CacheUtil.get(Key.as(CommonAttr.CACHE.LOGIN_KEY, token), User.class);
    }

    private static Set<String> getTokens(long userId) {
        Object cachedTokens = CacheUtil.get(userTokenKey(userId), Object.class);
        Set<String> tokens = new HashSet<>();
        if (cachedTokens instanceof String) {
            tokens.add((String) cachedTokens);
        } else if (cachedTokens instanceof Iterable<?>) {
            for (Object cachedToken : (Iterable<?>) cachedTokens) {
                if (cachedToken instanceof String) {
                    tokens.add((String) cachedToken);
                }
            }
        }
        return tokens;
    }

    private static Key userTokenKey(long userId) {
        return Key.as(CommonAttr.CACHE.USER_ID_TOKEN, String.valueOf(userId));
    }
}
