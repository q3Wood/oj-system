package com.oj.backend.service;

import com.oj.backend.vo.AiAnalysisReportVO;

public interface AiCoachService {

    /**
     * 针对单次提交触发或获取 AI 代码诊断报告
     *
     * @param submissionId 提交记录主键 ID
     * @return 结构化 AI 诊断报告视图对象 (小驼峰格式)
     */
    AiAnalysisReportVO analyzeSubmission(Long submissionId);
}