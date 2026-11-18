package com.oj.backend.vo;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI 代码诊断报告视图对象")
public class AiAnalysisReportVO {

    @Schema(description = "AI 鼓励语 / 诊断总评大标题")
    private String congratulation;

    @Schema(description = "算法与方法分析板块")
    private MethodAnalysis method;

    @Schema(description = "运行效率与复杂度板块")
    private EfficiencyAnalysis efficiency;

    @Schema(description = "代码风格与工程规范板块")
    private StyleAnalysis style;

    @Data
    public static class MethodAnalysis {
        @JsonAlias("current_method")
        @Schema(description = "用户当前采用的方法分析")
        private String currentMethod;

        @JsonAlias("suggested_method")
        @Schema(description = "AI 推荐的标准/更优算法思路")
        private String suggestedMethod;

        @JsonAlias("core_concept")
        @Schema(description = "考察的核心算法概念与知识点")
        private String coreConcept;

        @JsonAlias("thinking_prompt")
        @Schema(description = "苏格拉底式引导思考题")
        private String thinkingPrompt;
    }

    @Data
    public static class EfficiencyAnalysis {
        @JsonAlias("current_time_complexity")
        @Schema(description = "当前代码的时间复杂度评估")
        private String currentTimeComplexity;

        @JsonAlias("suggested_time_complexity")
        @Schema(description = "目标最优时间复杂度")
        private String suggestedTimeComplexity;

        @JsonAlias("optimization_advice")
        @Schema(description = "针对时间复杂度的性能优化建议")
        private String optimizationAdvice;

        @JsonAlias("current_space_complexity")
        @Schema(description = "当前代码的空间复杂度评估")
        private String currentSpaceComplexity;

        @JsonAlias("suggested_space_complexity")
        @Schema(description = "目标最优空间复杂度")
        private String suggestedSpaceComplexity;

        @JsonAlias("space_optimization_advice")
        @Schema(description = "针对空间复杂度的内存优化建议")
        private String spaceOptimizationAdvice;
    }

    @Data
    public static class StyleAnalysis {
        @Schema(description = "可读性评价")
        private String readability;

        @Schema(description = "代码模块化与结构评价")
        private String structure;

        @Schema(description = "代码风格改进建议")
        private String advice;
    }
}