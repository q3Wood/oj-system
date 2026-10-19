package com.oj.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oj.backend.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 继承 BaseMapper 后，自带单表的所有增删改查，无需手写任何 SQL！
}