package com.oj.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminContestVO {
    private Long id;
    private String title;
    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private String status;           // NOT_STARTED / RUNNING / ENDED
    private Integer problemCount;    // 本场包含题数
    private Integer registeredCount; // 累计报名人数
    private Boolean isAllPublic;     // 题目是否已全部公开到题库

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}