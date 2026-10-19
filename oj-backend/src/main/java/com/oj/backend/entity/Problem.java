package com.oj.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_problem")
public class Problem {
    // 题目 ID (如 1001，手动分配/自定义主键)
    @TableId(type = IdType.INPUT)
    private Long id;

    private String title;

    private String description;

    private Integer timeLimitMs;

    private Integer memoryLimitMb;

    private String author;

    // 是否公开：1-公开, 0-保密
    private Integer isPublic;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}