package com.oj.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_contest_user")
public class ContestUser {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contestId;

    private Long userId;

    private LocalDateTime createdAt;
}