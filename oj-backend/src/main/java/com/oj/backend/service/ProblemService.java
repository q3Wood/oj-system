package com.oj.backend.service;

import com.oj.backend.common.PageResult;
import com.oj.backend.dto.ProblemCreateDTO;
import com.oj.backend.dto.ProblemQueryDTO;
import com.oj.backend.dto.ProblemUpdateDTO;
import com.oj.backend.vo.ProblemVO;

public interface ProblemService {

    // 多条件分页查询题目列表
    PageResult<ProblemVO> listProblems(ProblemQueryDTO queryDTO);

    // 获取单题详情
    ProblemVO getProblemById(Long id);

    // 创建题目
    ProblemVO createProblem(ProblemCreateDTO dto);

    // 更新题目
    ProblemVO updateProblem(Long id, ProblemUpdateDTO dto);

    // 逻辑软删除题目
    void deleteProblem(Long id);

    void updateProblemVisibility(Long id, Integer isPublic);
}