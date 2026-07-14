package com.example.ragkb.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT Token 提供者
 * <p>
 * 负责 access_token 和 refresh_token 的生成、验证和解析。
 * 采用 HMAC-SHA 签名，access_key 和 refresh_key 基于同一 secret 派生。
 * </p>
 */
@Component
public class JwtTokenProvider {

    private final SecretKey accessTokenKey;
    private final long accessTokenExpiration;
    private final SecretKey refreshTokenKey;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${app.jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        this.accessTokenKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenKey = Keys.hmacShaKeyFor((secret + "_refresh").getBytes(StandardCharsets.UTF_8));
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    /**
     * 生成访问 Token（含用户 ID、用户名、角色）
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param role     用户角色（ADMIN/USER）
     * @return JWT access_token 字符串
     */
    public String generateAccessToken(Long userId, String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(accessTokenKey)
                .compact();
    }

    /**
     * 生成刷新 Token（仅含用户 ID，用于无感续期）
     *
     * @param userId 用户 ID
     * @return JWT refresh_token 字符串
     */
    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(refreshTokenKey)
                .compact();
    }

    /**
     * 验证 access_token 签名是否有效
     *
     * @param token JWT 字符串
     * @return true 表示签名有效
     */
    public boolean validateAccessToken(String token) {
        try {
            Jwts.parser().verifyWith(accessTokenKey).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** 从 access_token 中解析用户 ID */
    public Long getUserIdFromAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(accessTokenKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.parseLong(claims.getSubject());
    }

    /** 从 access_token 中解析用户名 */
    public String getUsernameFromAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(accessTokenKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("username", String.class);
    }

    /** 从 access_token 中解析用户角色 */
    public String getRoleFromAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(accessTokenKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("role", String.class);
    }

    /**
     * 获取 access_token 的过期时间戳（毫秒）
     * 用于 Token 黑名单服务，确定黑名单条目的自动清理时间
     *
     * @param token JWT access_token 字符串
     * @return 过期时间戳（毫秒）
     */
    public long getExpiryFromAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(accessTokenKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getExpiration().getTime();
    }

    /** 从 refresh_token 中解析用户 ID */
    public Long getUserIdFromRefreshToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(refreshTokenKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 验证 refresh_token 签名是否有效
     *
     * @param token JWT 字符串
     * @return true 表示签名有效
     */
    public boolean validateRefreshToken(String token) {
        try {
            Jwts.parser().verifyWith(refreshTokenKey).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
