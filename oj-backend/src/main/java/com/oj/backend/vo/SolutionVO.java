package com.oj.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.oj.backend.entity.Solution;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;

@Data
@Schema(description = "题解视图对象")
public class SolutionVO {

    @Schema(description = "题解唯一 ID")
    private Long id;

    @Schema(description = "所属题目 ID")
    private Long problemId;

    @Schema(description = "作者用户 ID")
    private Long userId;

    @Schema(description = "作者用户名")
    private String authorName;

    @Schema(description = "题解标题")
    private String title;

    @Schema(description = "Markdown 正文 (列表查询时为 null，详情查询时返回)")
    private String content;

    @Schema(description = "点赞数")
    private Integer likes;

    @Schema(description = "当前登录用户是否已点赞")
    private Boolean hasLiked = false;

    @Schema(description = "发布时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    public static SolutionVO fromEntity(Solution entity) {
        if (entity == null) return null;
        SolutionVO vo = new SolutionVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}