package com.oj.backend.service;

import com.oj.backend.common.PageResult;
import com.oj.backend.dto.SolutionCreateDTO;
import com.oj.backend.dto.SolutionQueryDTO;
import com.oj.backend.dto.SolutionUpdateDTO;
import com.oj.backend.vo.SolutionVO;

public interface SolutionService {

    PageResult<SolutionVO> listSolutions(SolutionQueryDTO queryDTO);

    SolutionVO getSolutionById(Long id);

    SolutionVO createSolution(SolutionCreateDTO dto);

    SolutionVO updateSolution(Long id, SolutionUpdateDTO dto);

    void deleteSolution(Long id);

    /**
     * 点赞 / 取消点赞切换
     * @return true-点赞成功，false-取消点赞成功
     */
    boolean toggleLikeSolution(Long id);
}