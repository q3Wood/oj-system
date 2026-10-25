package com.oj.backend.service;

import com.oj.backend.common.PageResult;
import com.oj.backend.dto.UserQueryDTO;
import com.oj.backend.entity.User;
import com.oj.backend.vo.UserVO;

public interface UserService {

    // 1.1 注册业务
    void register(String username, String password);

    // 1.2 登录业务，成功返回生成的 Token
    String login(String username, String password);

    // 1.3 根据主键 ID 获取脱敏后的用户信息
    UserVO getUserInfoById(Long userId);

    // 1.4 登出业务，清除 Redis 凭证
    void logout(Long userId);

    // 1.5 多条件分页查询所有用户 (返回脱敏 VO)
    PageResult<UserVO> listUsers(UserQueryDTO queryDTO);

    // 1.6 更新用户角色
    void updateUserRole(Long targetUserId, String newRole);

    // 内部辅助查询：根据用户名查询用户实体
    User findByUsername(String username);
}