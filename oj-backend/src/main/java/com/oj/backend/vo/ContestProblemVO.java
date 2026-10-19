package com.oj.backend.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContestProblemVO {
    private String displayId;    // "A", "B", "C"...
    private String displayTitle; // 比赛展示标题
    private Long problemId;      // 真实题目 ID
    private String description;  // 题面描述 (查单题时有值，查列表时为 null)
    private Integer timeLimitMs;
    private Integer memoryLimitMb;
}