package com.oj.backend.service;

import com.oj.backend.common.PageResult;
import com.oj.backend.dto.SubmitDTO;
import com.oj.backend.entity.Submission;

public interface SubmissionService {
    // 3.1 提交代码评测
    Submission submit(SubmitDTO dto);

    // 3.2 根据 ID 查询提交详情
    Submission getById(Long id);

    // 3.3 分页多条件查询提交记录
    PageResult<Submission> listSubmissions(Integer pageNum, Integer pageSize,
                                           Long userId, Long problemId, String verdict,
                                           String username);
}