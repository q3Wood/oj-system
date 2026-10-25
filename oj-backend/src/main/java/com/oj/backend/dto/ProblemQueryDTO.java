package com.oj.backend.dto;

import com.oj.backend.common.PageQueryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "题目多条件分页查询请求参数")
public class ProblemQueryDTO extends PageQueryDTO {

    @Schema(description = "按题号 ID 精准查询 (如 1001)")
    private Long id;

    @Schema(description = "按题目标题模糊查询")
    private String title;

    @Schema(description = "按出题人模糊查询")
    private String author;

    @Schema(description = "按公开状态筛选 (仅管理员有效: 1-公开, 0-比赛保密)")
    private Integer isPublic;
}