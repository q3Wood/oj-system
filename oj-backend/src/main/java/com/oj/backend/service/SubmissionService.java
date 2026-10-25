package com.oj.backend.service;

import com.oj.backend.common.PageResult;
import com.oj.backend.dto.SubmissionQueryDTO;
import com.oj.backend.dto.SubmitDTO;
import com.oj.backend.vo.SubmissionVO;

public interface SubmissionService {

    SubmissionVO submit(SubmitDTO dto);

    SubmissionVO getById(Long id);

    PageResult<SubmissionVO> listSubmissions(SubmissionQueryDTO queryDTO);
}