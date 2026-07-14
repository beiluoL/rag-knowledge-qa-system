package com.example.ragkb.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器
 * <p>
 * 从请求头 Authorization: Bearer &lt;token&gt; 中提取 JWT，
 * 验证有效性并检查 Token 黑名单（支持 Logout 主动失效），
 * 通过后将用户信息写入 Spring Security 上下文。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final com.example.ragkb.service.TokenBlacklistService tokenBlacklistService;

    /**
     * 核心过滤逻辑：提取 Token → 验证签名 → 检查黑名单 → 设置 SecurityContext
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        // 验证 Token 签名有效 且 未被加入黑名单
        if (StringUtils.hasText(token) && jwtTokenProvider.validateAccessToken(token)
                && !tokenBlacklistService.isBlacklisted(token)) {
            String userId = jwtTokenProvider.getUserIdFromAccessToken(token).toString();
            String username = jwtTokenProvider.getUsernameFromAccessToken(token);
            String role = jwtTokenProvider.getRoleFromAccessToken(token);

            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + role)
            );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            authentication.setDetails(username);

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头中提取 Bearer Token
     *
     * @param request HTTP 请求
     * @return JWT 字符串，无 Token 时返回 null
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
