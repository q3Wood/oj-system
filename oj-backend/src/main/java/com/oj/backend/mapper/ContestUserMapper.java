package com.oj.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oj.backend.entity.ContestUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContestUserMapper extends BaseMapper<ContestUser> {
}