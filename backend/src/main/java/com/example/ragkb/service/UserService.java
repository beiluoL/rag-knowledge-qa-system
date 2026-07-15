package com.example.ragkb.service;

import com.example.ragkb.exception.BusinessException;
import com.example.ragkb.model.dto.ChangePasswordRequest;
import com.example.ragkb.model.dto.UpdateProfileRequest;
import com.example.ragkb.model.entity.User;
import com.example.ragkb.model.enums.UserRole;
import com.example.ragkb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.upload-dir:./data/documents}")
    private String uploadDir;

    /** 头像 URL 前缀（前端 <img src> 直接引用，端点 permitAll） */
    private static final String AVATAR_URL_PREFIX = "/api/files/avatar/";
    private static final long MAX_AVATAR_SIZE = 2L * 1024 * 1024; // 2MB
    private static final List<String> ALLOWED_EXT = List.of("png", "jpg", "jpeg", "webp", "gif");

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

    /**
     * 上传并更新用户头像
     * 校验图片类型与大小，保存至 upload-dir/avatars 目录，
     * 若原头像是本地文件则删除旧图，避免磁盘堆积。
     *
     * @return 更新后的用户实体（avatar 字段为可公开访问的 URL）
     */
    public User uploadAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new BusinessException("头像图片不能超过 2MB");
        }
        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        }
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("仅支持 PNG / JPG / WEBP / GIF 格式");
        }

        User user = getUserById(userId);

        // 删除旧头像文件（仅当为本地上传的）
        deleteOldAvatarFile(user.getAvatar());

        try {
            Path avatarDir = resolveAvatarDir();
            String savedName = UUID.randomUUID().toString() + "." + ext;
            Path target = avatarDir.resolve(savedName);
            file.transferTo(target.toFile());
            user.setAvatar(AVATAR_URL_PREFIX + savedName);
            return userRepository.save(user);
        } catch (java.io.IOException e) {
            throw new BusinessException("头像保存失败: " + e.getMessage());
        }
    }

    /**
     * 解析头像存储目录：upload-dir/avatars（相对路径则落到用户主目录下）
     */
    private Path resolveAvatarDir() throws java.io.IOException {
        Path base = Paths.get(uploadDir);
        if (!base.isAbsolute()) {
            String rel = uploadDir.startsWith("./") ? uploadDir.substring(2) : uploadDir;
            base = Paths.get(System.getProperty("user.home"), rel);
        }
        Path avatarDir = base.resolve("avatars");
        Files.createDirectories(avatarDir);
        return avatarDir;
    }

    /**
     * 删除旧的本地头像文件
     */
    private void deleteOldAvatarFile(String avatar) {
        if (avatar == null || !avatar.startsWith(AVATAR_URL_PREFIX)) return;
        try {
            String filename = avatar.substring(AVATAR_URL_PREFIX.length());
            Path avatarDir = resolveAvatarDir();
            Path old = avatarDir.resolve(filename);
            // 路径穿越防护：解析后必须仍在头像目录内
            if (old.normalize().startsWith(avatarDir.normalize())) {
                Files.deleteIfExists(old);
            }
        } catch (java.io.IOException ignored) {
            // 旧文件删除失败不影响新上传
        }
    }
}
