package com.oj.backend.service;

import com.oj.backend.common.PageResult;
import com.oj.backend.dto.SolutionCreateDTO;
import com.oj.backend.dto.SolutionUpdateDTO;
import com.oj.backend.entity.Solution;

public interface SolutionService {
    // 分页获取某题目的题解列表 (带作者名，不含大文本 content)
    PageResult<Solution> listSolutions(Long problemId, Integer pageNum, Integer pageSize);

    // 根据 ID 查看题解详情 (包含完整 Markdown 内容)
    Solution getSolutionById(Long id);

    // 发布新题解
    Solution createSolution(SolutionCreateDTO dto);

    // 修改题解
    Solution updateSolution(Long id, SolutionUpdateDTO dto);

    // 删除题解
    void deleteSolution(Long id);
}