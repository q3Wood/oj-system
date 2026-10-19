package com.oj.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserDTO {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 16, message = "用户名长度必须在4到16位之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 5, max = 16, message = "密码长度必须在5到16位之间")
    private String password;
}