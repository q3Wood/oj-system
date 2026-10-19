package com.oj.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ContestCreateDTO {
    @NotBlank(message = "比赛标题不能为空")
    private String title;

    @NotBlank(message = "比赛说明不能为空")
    private String description;

    @NotNull(message = "开始时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @NotEmpty(message = "比赛至少需要包含一道题目")
    private List<ContestProblemItem> problems;

    @Data
    public static class ContestProblemItem {
        @NotNull(message = "题目 ID 不能为空")
        private Long problemId;

        @NotBlank(message = "比赛展示标题不能为空")
        private String displayTitle;
    }
}