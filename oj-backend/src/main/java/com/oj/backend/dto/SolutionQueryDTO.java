package com.oj.backend.dto;

import com.oj.backend.common.PageQueryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "题解列表分页查询参数")
public class SolutionQueryDTO extends PageQueryDTO {

    @NotNull(message = "题目 ID 不能为空")
    @Schema(description = "题目 ID")
    private Long problemId;

    @Schema(description = "作者用户 ID (可选，用于查某人的题解)")
    private Long userId;

    @Schema(description = "题解标题关键字搜索 (可选)")
    private String keyword;
}