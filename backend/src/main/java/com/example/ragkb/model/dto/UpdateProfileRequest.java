package com.example.ragkb.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户资料更新请求 DTO
 * 支持修改昵称、邮箱和头像
 */
@Data
public class UpdateProfileRequest {

    /** 昵称（可选，最大 50 字符） */
    @Size(max = 50, message = "昵称最多 50 个字符")
    private String nickname;

    /** 邮箱（可选，最大 100 字符） */
    @Size(max = 100, message = "邮箱最多 100 个字符")
    private String email;

    /** 头像 URL（可选，最大 500 字符） */
    @Size(max = 500, message = "头像 URL 最多 500 个字符")
    private String avatar;
}
