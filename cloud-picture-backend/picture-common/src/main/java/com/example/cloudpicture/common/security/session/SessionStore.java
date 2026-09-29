package com.example.cloudpicture.common.security.session;

import com.example.cloudpicture.common.security.context.CurrentUser;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 登录会话：token -> 用户信息，默认 7 天过期；
 * 同时维护 userId -> token 集合，便于禁用账号时立刻踢下线
 */
@Component
public class SessionStore {

    private static final String KEY_PREFIX = "picture:session:";
    private static final String USER_KEY_PREFIX = "picture:session:user:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public SessionStore(StringRedisTemplate redisTemplate,
                        @Value("${picture.session.ttl-seconds:604800}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public String create(CurrentUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> session = new LinkedHashMap<>();
        session.put("id", String.valueOf(user.getId()));
        session.put("account", user.getAccount());
        session.put("name", user.getName() == null ? "" : user.getName());
        session.put("role", user.getRole());
        String key = key(token);
        redisTemplate.opsForHash().putAll(key, session);
        redisTemplate.expire(key, ttl);
        String userKey = userKey(user.getId());
        redisTemplate.opsForSet().add(userKey, token);
        redisTemplate.expire(userKey, ttl);
        user.setToken(token);
        return token;
    }

    public CurrentUser get(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Map<Object, Object> session = redisTemplate.opsForHash().entries(key(token));
        if (session.isEmpty()) {
            return null;
        }
        CurrentUser user = new CurrentUser();
        user.setId(Long.valueOf((String) session.get("id")));
        user.setAccount((String) session.get("account"));
        user.setName((String) session.get("name"));
        user.setRole((String) session.get("role"));
        user.setToken(token);
        return user;
    }

    public void refresh(String token, Long userId) {
        redisTemplate.expire(key(token), ttl);
        if (userId != null) {
            String userKey = userKey(userId);
            redisTemplate.opsForSet().add(userKey, token);
            redisTemplate.expire(userKey, ttl);
        }
    }

    public void remove(String token, Long userId) {
        redisTemplate.delete(key(token));
        if (userId != null) {
            redisTemplate.opsForSet().remove(userKey(userId), token);
        }
    }

    /** 清空某个用户的全部会话，用于禁用账号时立刻踢下线 */
    public void removeAllByUserId(Long userId) {
        String userKey = userKey(userId);
        Set<String> tokens = redisTemplate.opsForSet().members(userKey);
        if (tokens != null && !tokens.isEmpty()) {
            redisTemplate.delete(tokens.stream().map(SessionStore::key).toList());
        }
        redisTemplate.delete(userKey);
    }

    private static String key(String token) {
        return KEY_PREFIX + token;
    }

    private static String userKey(Long userId) {
        return USER_KEY_PREFIX + userId;
    }
}
