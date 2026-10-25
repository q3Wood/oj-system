package com.oj.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.oj.backend.entity.Submission;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;

@Data
@Schema(description = "提交记录视图对象")
public class SubmissionVO {

    @Schema(description = "提交唯一 ID")
    private Long id;

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "提交者用户名")
    private String username;

    @Schema(description = "题目 ID")
    private Long problemId;

    @Schema(description = "题目标题")
    private String problemTitle;

    @Schema(description = "比赛 ID (日常提交为 null)")
    private Long contestId;

    @Schema(description = "编程语言")
    private String language;

    @Schema(description = "评测结果状态码")
    private String verdict;

    @Schema(description = "消耗最大时间 (ms)")
    private Integer maxTimeMs;

    @Schema(description = "消耗峰值内存 (KB)")
    private Integer peakMemoryKb;

    @Schema(description = "源代码 (列表查询时为 null，详情查询时根据权限返回)")
    private String code;

    @Schema(description = "测试点评测报告明细 JSON")
    private String judgeDetails;

    @Schema(description = "提交时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    public static SubmissionVO fromEntity(Submission sub) {
        if (sub == null) return null;
        SubmissionVO vo = new SubmissionVO();
        BeanUtils.copyProperties(sub, vo);
        return vo;
    }
}