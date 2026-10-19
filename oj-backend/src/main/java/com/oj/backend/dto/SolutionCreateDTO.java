package com.oj.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SolutionCreateDTO {
    @NotNull(message = "题目 ID 不能为空")
    private Long problemId;

    @NotBlank(message = "题解标题不能为空")
    @Size(min = 4, max = 64, message = "标题长度需在 4 到 64 个字符之间")
    private String title;

    @NotBlank(message = "题解内容不能为空")
    private String content;
}