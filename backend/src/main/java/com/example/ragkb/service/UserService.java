package com.example.ragkb.service;

import com.example.ragkb.exception.BusinessException;
import com.example.ragkb.model.dto.ChangePasswordRequest;
import com.example.ragkb.model.dto.UpdateProfileRequest;
import com.example.ragkb.model.entity.User;
import com.example.ragkb.model.enums.UserRole;
import com.example.ragkb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 获取用户信息
     */
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
    }

    /**
     * 修改密码（需验证旧密码）
     */
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getUserById(userId);

        // 验证旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException("旧密码不正确");
        }

        // 更新密码
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    /**
     * 更新用户资料（昵称、邮箱、头像）
     * 仅更新非 null 字段，null 字段保持原值不变
     *
     * @param userId  用户 ID
     * @param request 资料更新请求
     * @return 更新后的用户实体
     */
    public User updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getUserById(userId);

        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }

        return userRepository.save(user);
    }

    /**
     * 获取所有用户列表（仅管理员调用）
     * 按创建时间倒序排列
     *
     * @return 用户列表
     */
    public List<User> getAllUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 切换用户启用/禁用状态（仅管理员调用）
     * 不能禁用自己
     *
     * @param targetUserId 目标用户 ID
     * @param operatorId   操作者 ID（防止自己禁用自己）
     */
    public void toggleUserEnabled(Long targetUserId, Long operatorId) {
        if (targetUserId.equals(operatorId)) {
            throw new BusinessException("不能禁用自己");
        }
        User user = getUserById(targetUserId);
        user.setEnabled(!user.getEnabled());
        userRepository.save(user);
    }

    /**
     * 修改用户角色（仅管理员调用）
     * 不能修改自己的角色
     *
     * @param targetUserId 目标用户 ID
     * @param operatorId   操作者 ID
     * @param role         新角色
     */
    public void updateUserRole(Long targetUserId, Long operatorId, String role) {
        if (targetUserId.equals(operatorId)) {
            throw new BusinessException("不能修改自己的角色");
        }
        User user = getUserById(targetUserId);
        try {
            user.setRole(UserRole.valueOf(role));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("无效的角色: " + role);
        }
        userRepository.save(user);
    }
}
