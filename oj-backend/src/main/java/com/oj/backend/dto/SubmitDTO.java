package com.oj.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmitDTO {
    @NotNull(message = "题目 ID 不能为空")
    private Long problemId;

    @NotBlank(message = "语言类型不能为空")
    private String language; // "cpp"

    @NotBlank(message = "代码不能为空")
    private String code;
}