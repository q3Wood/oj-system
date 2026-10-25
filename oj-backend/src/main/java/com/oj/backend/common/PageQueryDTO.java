package com.oj.backend.common;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public abstract class PageQueryDTO {

    @Schema(description = "当前页码，默认第1页", example = "1")
    @Min(value = 1, message = "页码最小为 1")
    private Integer pageNum = 1;

    @Schema(description = "每页显示条数，默认10条", example = "10")
    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大不能超过 100")
    private Integer pageSize = 10;
}