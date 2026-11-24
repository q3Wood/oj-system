package com.oj.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oj.backend.entity.Changelog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChangelogMapper extends BaseMapper<Changelog> {
}