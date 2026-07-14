package com.example.ragkb.controller;

import com.example.ragkb.model.dto.ChangePasswordRequest;
import com.example.ragkb.model.dto.UpdateProfileRequest;
import com.example.ragkb.model.entity.User;
import com.example.ragkb.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        User user = userService.getUserById(userId);
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail() != null ? user.getEmail() : "",
                "role", user.getRole().name(),
                "createdAt", user.getCreatedAt().toString()
        ));
    }

    /**
     * 修改密码
     */
    @PutMapping("/password")
    public ResponseEntity<Map<String, String>> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        userService.changePassword(userId, request);
        return ResponseEntity.ok(Map.of("message", "密码修改成功"));
    }

    /**
     * 更新用户资料（昵称、邮箱、头像）
     * 仅更新请求中非 null 的字段
     */
    @PutMapping("/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        User user = userService.updateProfile(userId, request);
        return ResponseEntity.ok(buildUserResponse(user));
    }

    /**
     * 构建用户信息响应 Map
     */
    private Map<String, Object> buildUserResponse(User user) {
        return Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail() != null ? user.getEmail() : "",
                "nickname", user.getNickname() != null ? user.getNickname() : "",
                "avatar", user.getAvatar() != null ? user.getAvatar() : "",
                "role", user.getRole().name(),
                "createdAt", user.getCreatedAt().toString()
        );
    }
}
