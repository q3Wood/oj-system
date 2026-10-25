package com.oj.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "比赛综合信息视图对象")
public class ContestVO {

    @Schema(description = "比赛唯一 ID")
    private Long id;

    @Schema(description = "比赛标题")
    private String title;

    @Schema(description = "比赛规程描述")
    private String description;

    @Schema(description = "开赛时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;

    @Schema(description = "当前状态: NOT_STARTED / RUNNING / ENDED")
    private String status;

    @Schema(description = "包含题目数量")
    private Integer problemCount = 0;

    @Schema(description = "已报名人数")
    private Integer registeredCount = 0;

    @Schema(description = "当前登录用户是否已报名")
    private Boolean isRegistered = false;

    @Schema(description = "题目是否已全量公开到公共题库 (管理员关注)")
    private Boolean isAllPublic = false;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
}