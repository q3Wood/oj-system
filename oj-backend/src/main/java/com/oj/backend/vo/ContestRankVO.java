package com.oj.backend.vo;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

@Data
public class ContestRankVO {
    private Integer rank;                  // 当前名次 (1, 2, 3...)
    private Long userId;                   // 用户 ID
    private String username;               // 用户名
    private Integer solvedCount = 0;       // AC 题目总数
    private Long totalPenalty = 0L;        // 累计总罚时 (分钟)

    // 各题目的作答细节字典: Key 为 "A", "B" 等题号
    private Map<String, ProblemResult> problemResults = new HashMap<>();

    @Data
    public static class ProblemResult {
        private boolean solved = false;    // 该题是否已通过 (AC)
        private Long timeMin = 0L;         // 首次 AC 距离开赛的分钟数
        private Integer wrongTries = 0;    // 首次 AC 前的错误提交次数 (WA/TLE等)
    }
}