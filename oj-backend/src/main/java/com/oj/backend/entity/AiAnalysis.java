package com.oj.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_ai_analysis")
public class AiAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long submissionId;
    private Long userId;
    private Long problemId;
    private String judgeStatus;

    private String congratulation;
    private String methodAnalysis;
    private String efficiencyAnalysis;
    private String styleAnalysis;
    private String rawReport;

    private String modelName;
    private Integer tokenCost;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}