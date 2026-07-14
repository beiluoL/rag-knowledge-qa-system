package com.example.ragkb.controller;

import com.example.ragkb.config.JwtTokenProvider;
import com.example.ragkb.exception.BusinessException;
import com.example.ragkb.model.dto.LoginRequest;
import com.example.ragkb.model.dto.LoginResponse;
import com.example.ragkb.model.dto.RegisterRequest;
import com.example.ragkb.model.entity.User;
import com.example.ragkb.repository.UserRepository;
import com.example.ragkb.service.AuthService;
import com.example.ragkb.service.TokenBlacklistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * 刷新 Token
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        return ResponseEntity.ok(authService.refreshToken(refreshToken));
    }

    /**
     * 用户登出
     * 将当前 access_token 加入黑名单，使其在有效期内也无法继续使用
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestHeader("Authorization") String authHeader) {
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            // 计算 token 过期时间，将 token 加入黑名单直到过期
            try {
                long expiryTime = jwtTokenProvider.getExpiryFromAccessToken(token);
                tokenBlacklistService.blacklist(token, expiryTime);
            } catch (Exception e) {
                // token 解析失败也视为登出成功
            }
        }
        return ResponseEntity.ok(Map.of("message", "登出成功"));
    }

    /**
     * 获取当前登录用户信息（用于刷新后恢复会话角色，路由守卫据此判断管理员权限）
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "role", user.getRole().name()
        ));
    }
}
