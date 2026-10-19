package com.oj.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_submission")
public class Submission {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long problemId;

    private String language;

    private String code;

    // 状态: PENDING / Accepted / Wrong Answer / ...
    private String verdict;

    private Integer maxTimeMs;

    private Integer peakMemoryKb;

    // 存 core-judge 返回的 details JSON 字符串
    @JsonRawValue
    private String judgeDetails;

    private Long contestId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String username; // 提交者用户名 (非数据库字段)

}