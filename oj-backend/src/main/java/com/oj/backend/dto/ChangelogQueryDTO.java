package com.oj.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Schema(description = "更新日志分页筛选参数")
public class ChangelogQueryDTO {

    @Min(value = 1, message = "当前页码最小值为 1！")
    @Schema(description = "当前页码", defaultValue = "1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "每页显示条数必须在 1 ~ 100 之间！")
    @Max(value = 100, message = "每页显示条数必须在 1 ~ 100 之间！")
    @Schema(description = "每页显示条数", defaultValue = "20")
    private Integer pageSize = 20;

    @Schema(description = "按更新类型精准筛选: FEAT / OPTIMIZE / FIX / NOTICE")
    private String type;
}