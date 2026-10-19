package com.oj.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ProblemCreateDTO {
    // 删除了 private Long id; 改由 Redis 原子自增发放

    @NotBlank(message = "题目标题不能为空")
    private String title;

    @NotBlank(message = "题面描述不能为空")
    private String description;

    private Integer timeLimitMs = 1000;

    private Integer memoryLimitMb = 256;

    // 是否公开：1-公开到公共题库，0-比赛保密题 (默认为 1)
    private Integer isPublic = 1;

    @NotNull(message = "测试用例压缩包 (zip) 不能为空")
    private MultipartFile file;
}