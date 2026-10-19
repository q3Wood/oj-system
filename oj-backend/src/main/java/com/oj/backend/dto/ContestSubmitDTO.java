package com.oj.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ContestSubmitDTO {
    @NotNull(message = "题目 ID 不能为空")
    private Long problemId;

    @NotBlank(message = "编程语言不能为空")
    private String language;

    @NotBlank(message = "代码不能为空")
    private String code;
}