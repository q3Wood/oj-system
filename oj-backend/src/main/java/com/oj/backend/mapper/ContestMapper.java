package com.oj.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oj.backend.entity.Contest;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContestMapper extends BaseMapper<Contest> {
}