package com.oj.backend.service;

import com.oj.backend.dto.JudgeResultDTO;

public interface JudgeService {
    /**
     * 执行代码评测策略接口
     *
     * @param problemId      题目 ID
     * @param language       编程语言
     * @param code           用户源码
     * @param timeLimitMs    时限 (ms)
     * @param memoryLimitMb  内存限额 (MB)
     * @return 评测报告 DTO
     */
    JudgeResultDTO executeJudge(Long problemId, String language, String code,
                                Integer timeLimitMs, Integer memoryLimitMb);
}