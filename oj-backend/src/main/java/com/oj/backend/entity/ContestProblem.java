package com.oj.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_contest_problem")
public class ContestProblem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contestId;

    private Long problemId;

    private String displayId; // "A", "B", "C"...

    private String displayTitle; // 比赛内展示标题

    private LocalDateTime createdAt;
}