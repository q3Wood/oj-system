package com.oj.backend.service;

import com.oj.backend.common.PageResult;
import com.oj.backend.entity.User;

public interface UserService {
    // 注册业务
    void register(String username, String password);

    // 登录业务，成功返回生成的 Token
    String login(String username, String password);

    // 根据用户名查询用户
    User findByUsername(String username);

    // 分页查询所有用户
    PageResult<User> listUsers(Integer pageNum, Integer pageSize, String keyword);

    // 更新用户角色
    void updateUserRole(Long targetUserId, String newRole);
}