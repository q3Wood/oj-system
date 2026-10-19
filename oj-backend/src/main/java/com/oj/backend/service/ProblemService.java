package com.oj.backend.service;

import com.oj.backend.common.PageResult;
import com.oj.backend.dto.ProblemCreateDTO;
import com.oj.backend.dto.ProblemUpdateDTO;
import com.oj.backend.entity.Problem;

public interface ProblemService {
    // 1. 统一题目查询 (智能识别：普通用户只看公开题，管理员看全量，支持多关键词模糊搜索)
    PageResult<Problem> listProblems(Integer pageNum, Integer pageSize, String keyword, Integer isPublic);

    // 2. 查单题详情
    Problem getProblemById(Long id);

    // 3. 创建题目 (自动发号 + 解压 Zip)
    Problem createProblem(ProblemCreateDTO dto);

    // 4. 统一更新题目 (支持改时空限制、题面描述与公开状态)
    Problem updateProblem(Long id, ProblemUpdateDTO dto);
}