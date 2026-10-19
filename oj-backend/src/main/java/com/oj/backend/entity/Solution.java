package com.oj.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_solution")
public class Solution {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long problemId;

    private Long userId;

    private String title;

    private String content;

    private Integer likes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 冗余字段 (不入库)，方便前端直接展示作者用户名
    @TableField(exist = false)
    private String authorName;

    @TableLogic
    private Integer isDeleted;
}