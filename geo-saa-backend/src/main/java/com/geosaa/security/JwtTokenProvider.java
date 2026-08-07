package com.geosaa.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    /** HS256 要求密钥至少 256 bit / 32 字节 */
    private static final int MIN_SECRET_BYTES = 32;

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey secretKey;
    private final long expiration;
    private final long refreshExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration:86400000}") long expiration,
            @Value("${jwt.refresh-expiration:604800000}") long refreshExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(decodeSecret(secret));
        this.expiration = expiration;
        this.refreshExpiration = refreshExpiration;
    }

    /**
     * 解析并校验密钥。
     *
     * <p>之前密钥直接硬编码在 application.yml 里且没有任何校验，
     * 一旦被配置成空串或短字符串，要么启动即崩，要么签名强度不足。
     * 这里改为显式失败并给出可操作的提示。
     */
    private static byte[] decodeSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT 密钥未配置。请设置环境变量 JWT_SECRET（Base64 编码、至少 32 字节），" +
                            "可用 `openssl rand -base64 32` 生成。");
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret.trim());
        } catch (Exception e) {
            throw new IllegalStateException("JWT 密钥不是合法的 Base64 字符串，请检查 JWT_SECRET 配置", e);
        }
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT 密钥强度不足：解码后仅 " + keyBytes.length + " 字节，HS256 要求至少 " + MIN_SECRET_BYTES + " 字节");
        }
        return keyBytes;
    }

    public String generateToken(String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshExpiration))
                .signWith(secretKey)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * 校验访问令牌。
     *
     * <p>额外拒绝 refresh 类型的令牌——修复前刷新令牌可以直接当作访问令牌使用，
     * 相当于把有效期从 1 天悄悄延长到 7 天。
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return !TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("访问令牌校验失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 校验刷新令牌：必须签名有效、未过期，且 type=refresh。
     */
    public boolean validateRefreshToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("刷新令牌校验失败: {}", e.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
