package com.oj.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ProblemUpdateDTO {
    @NotBlank(message = "题目标题不能为空")
    private String title;

    @NotBlank(message = "题面描述不能为空")
    private String description;

    @NotNull(message = "时间限制不能为空")
    private Integer timeLimitMs;

    @NotNull(message = "内存限制不能为空")
    private Integer memoryLimitMb;

    // 可选更新可见性 (1-公开, 0-保密)
    private Integer isPublic;

    // 【核心新增】：选填！如果出题人上传了新压缩包，则覆盖解压；不传则保留原用例
    private MultipartFile file;
}