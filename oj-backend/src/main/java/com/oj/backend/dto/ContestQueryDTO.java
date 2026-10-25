package com.oj.backend.dto;

import com.oj.backend.common.PageQueryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "比赛分页查询参数")
public class ContestQueryDTO extends PageQueryDTO {

    @Schema(description = "按比赛标题搜索 (可选)")
    private String keyword;
}