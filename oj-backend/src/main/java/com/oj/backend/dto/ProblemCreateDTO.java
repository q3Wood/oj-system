package com.oj.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ProblemCreateDTO {

    @NotBlank(message = "题目标题不能为空")
    private String title;

    @NotBlank(message = "题面描述不能为空")
    private String description;

    @NotNull(message = "时间限制不能为空")
    @Min(value = 100, message = "时间限制不能低于 100ms")
    private Integer timeLimitMs = 1000;

    @NotNull(message = "内存限制不能为空")
    @Min(value = 16, message = "内存限制不能低于 16MB")
    private Integer memoryLimitMb = 256;

    // 是否公开：1-公开到公共题库，0-比赛保密题 (默认为 1)
    private Integer isPublic = 1;

    @NotNull(message = "测试用例压缩包 (zip) 不能为空")
    private MultipartFile file;
}