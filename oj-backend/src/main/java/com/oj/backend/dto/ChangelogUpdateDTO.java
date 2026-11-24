package com.oj.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "修改系统更新日志请求体")
public class ChangelogUpdateDTO {

    @NotBlank(message = "版本号不能为空！")
    @Size(max = 32, message = "版本号长度不能超过 32 位！")
    @Schema(description = "修改后的版本号")
    private String version;

    @NotBlank(message = "更新标题不能为空！")
    @Size(max = 128, message = "更新标题长度不能超过 128 位！")
    @Schema(description = "修改后的更新标题")
    private String title;

    @NotBlank(message = "更新类型不能为空！")
    @Pattern(regexp = "^(FEAT|OPTIMIZE|FIX|NOTICE)$", message = "更新类型不合法，仅支持 FEAT / OPTIMIZE / FIX / NOTICE！")
    @Schema(description = "修改后的更新类型: FEAT / OPTIMIZE / FIX / NOTICE")
    private String type;

    @NotBlank(message = "更新详情内容不能为空！")
    @Schema(description = "修改后的 Markdown 更新正文")
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "修改后的发布时间 (不传则保持原值)")
    private LocalDateTime releaseDate;
}