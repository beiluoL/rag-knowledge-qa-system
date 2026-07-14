package com.example.ragkb.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token 黑名单服务（内存级）
 * <p>
 * 用于实现 Logout 功能：用户退出时将 access_token 加入黑名单，
 * 使其在有效期内也无法继续使用。
 * </p>
 * <p>
 * 实现原理：
 * - 使用 ConcurrentHashMap 存储被拉黑的 token 及其过期时间戳
 * - 定时任务每分钟清理已过期的 token，防止内存泄漏
 * - 适用于单实例部署；多实例需改用 Redis 共享黑名单
 * </p>
 */
@Service
@Slf4j
public class TokenBlacklistService {

    /** key: JWT token 字符串, value: token 过期时间戳（毫秒） */
    private final ConcurrentHashMap<String, Long> blacklist = new ConcurrentHashMap<>();

    /**
     * 将 token 加入黑名单
     *
     * @param token      JWT access_token 字符串
     * @param expiryTime token 的过期时间戳（毫秒），到期后自动从黑名单移除
     */
    public void blacklist(String token, long expiryTime) {
        blacklist.put(token, expiryTime);
        log.debug("Token 已加入黑名单，当前黑名单大小: {}", blacklist.size());
    }

    /**
     * 检查 token 是否在黑名单中
     *
     * @param token JWT access_token 字符串
     * @return true 表示 token 已被拉黑（已 logout），false 表示 token 有效
     */
    public boolean isBlacklisted(String token) {
        Long expiry = blacklist.get(token);
        if (expiry == null) {
            return false;
        }
        // 如果 token 已过期，顺便清理
        if (System.currentTimeMillis() > expiry) {
            blacklist.remove(token);
            return false;
        }
        return true;
    }

    /**
     * 定时清理已过期的 token，防止内存无限增长
     * 每 60 秒执行一次
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredTokens() {
        long now = System.currentTimeMillis();
        int before = blacklist.size();
        blacklist.entrySet().removeIf(entry -> now > entry.getValue());
        int removed = before - blacklist.size();
        if (removed > 0) {
            log.debug("清理过期黑名单 token: {} 条，剩余: {} 条", removed, blacklist.size());
        }
    }
}
