package com.example.ragkb.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 50)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 4, max = 100)
    private String password;

    @Size(max = 100)
    private String email;
}
