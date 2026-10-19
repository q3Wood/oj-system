package com.oj.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UserRoleUpdateDTO {
    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(USER|ADMIN)$", message = "只能将用户调整为 USER 或 ADMIN 角色")
    private String role;
}