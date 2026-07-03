package com.example.ragkb.controller;

import com.example.ragkb.model.dto.ChangePasswordRequest;
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
}
