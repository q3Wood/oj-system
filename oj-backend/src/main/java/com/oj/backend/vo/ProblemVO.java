package com.oj.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.oj.backend.entity.Problem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;

@Data
@Schema(description = "题目信息视图对象 (含当前用户做题状态)")
public class ProblemVO {

    @Schema(description = "题目唯一ID (从1001起)")
    private Long id;

    @Schema(description = "题目标题")
    private String title;

    @Schema(description = "Markdown 题面描述 (列表查询时为 null，详情查询时返回)")
    private String description;

    @Schema(description = "时间限制 (ms)")
    private Integer timeLimitMs;

    @Schema(description = "内存限制 (MB)")
    private Integer memoryLimitMb;

    @Schema(description = "出题人")
    private String author;

    @Schema(description = "是否公开: 1-公开, 0-比赛保密")
    private Integer isPublic;

    @Schema(description = "当前登录用户的做题状态: NOT_STARTED(未做) / ATTEMPTED(尝试未过) / SOLVED(已通过AC)")
    private String userStatus = "NOT_STARTED";

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    public static ProblemVO fromEntity(Problem problem) {
        if (problem == null) {
            return null;
        }
        ProblemVO vo = new ProblemVO();
        BeanUtils.copyProperties(problem, vo);
        vo.setUserStatus("NOT_STARTED");
        return vo;
    }
}