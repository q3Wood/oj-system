package com.oj.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oj.backend.entity.Solution;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SolutionMapper extends BaseMapper<Solution> {
}