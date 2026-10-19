package com.oj.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    // 返回 JSON 时忽略密码，严防密码泄露
    @JsonIgnore
    private String password;

    private String role; // USER / ADMIN

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}