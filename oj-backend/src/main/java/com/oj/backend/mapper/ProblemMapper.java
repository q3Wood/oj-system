package com.oj.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oj.backend.entity.Problem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {
    // 继承 BaseMapper，直接拥有题目表的所有单表 CRUD 功能
}