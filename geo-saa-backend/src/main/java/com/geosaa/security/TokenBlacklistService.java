package com.geosaa.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Token 黑名单服务。
 *
 * <p>单独抽出该服务是为了打破原有的循环依赖：
 * SecurityConfig -> JwtAuthenticationFilter -> AuthService -> PasswordEncoder(定义于 SecurityConfig)。
 * 过滤器现在只依赖本服务与 RedisTemplate，不再反向依赖 AuthService。
 */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String TOKEN_BLACKLIST_KEY = "geo:token:blacklist:";
    private static final long TOKEN_BLACKLIST_TTL_HOURS = 24;

    private final RedisTemplate<String, Object> redisTemplate;

    /** 将 token 加入黑名单，TTL 与 token 最长有效期保持一致 */
    public void blacklist(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        redisTemplate.opsForValue()
                .set(TOKEN_BLACKLIST_KEY + token, "logout", TOKEN_BLACKLIST_TTL_HOURS, TimeUnit.HOURS);
    }

    public boolean isBlacklisted(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_BLACKLIST_KEY + token));
    }
}
