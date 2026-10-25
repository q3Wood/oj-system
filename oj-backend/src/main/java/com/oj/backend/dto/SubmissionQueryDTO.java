package com.oj.backend.dto;

import com.oj.backend.common.PageQueryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "提交记录多条件分页查询参数")
public class SubmissionQueryDTO extends PageQueryDTO {

    @Schema(description = "指定用户 ID 筛选")
    private Long userId;

    @Schema(description = "指定题目 ID 筛选")
    private Long problemId;

    @Schema(description = "指定比赛 ID 筛选 (null 查日常，有值查特定比赛)")
    private Long contestId;

    @Schema(description = "按评测状态筛选 (如 Accepted, Wrong Answer 等)")
    private String verdict;

    @Schema(description = "按提交者用户名模糊搜索")
    private String username;
}