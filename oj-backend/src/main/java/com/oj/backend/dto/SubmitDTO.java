package com.oj.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "代码提交评测入参")
public class SubmitDTO {

    @NotNull(message = "题目 ID 不能为空")
    @Schema(description = "题目唯一 ID")
    private Long problemId;

    @Schema(description = "比赛 ID (日常练习提交为 null，比赛内提交传入对应比赛 ID)")
    private Long contestId;

    @NotBlank(message = "语言类型不能为空")
    @Schema(description = "编程语言: cpp / c")
    private String language;

    @NotBlank(message = "代码不能为空")
    @Schema(description = "源码正文")
    @Size(max = 65535, message = "代码长度不能超过 64kB!")
    private String code;
}