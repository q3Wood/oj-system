package com.oj.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.backend.common.BusinessException;
import com.oj.backend.common.PageResult;
import com.oj.backend.common.UserContext;
import com.oj.backend.entity.User;
import com.oj.backend.mapper.UserMapper;
import com.oj.backend.service.UserService;
import com.oj.backend.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 密码加密器
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    @Override
    public void register(String username, String password) {
        // 本系统为自用，因此暂时限制注册人数最多为 5 个，防止被滥用
        Long userCount = userMapper.selectCount(null);
        if (userCount >= 5) {
            throw new BusinessException("注册人数已达上限，如被邀请可通过邮箱：227604656@qq.com 联系管理员！");
        }
        
        // 1. 查询用户名是否已被占用
        User existUser = findByUsername(username);
        if (existUser != null) {
            throw new BusinessException("用户名已被注册！");
        }

        // 2. 加密密码并落库
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password)); // BCrypt 密文存储
        user.setRole("USER");
        userMapper.insert(user);
    }

    @Override
    public String login(String username, String password) {
        User user = findByUsername(username);
        if (user == null) {
            throw new BusinessException("用户名或密码错误！");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("用户名或密码错误！");
        }

        String token = JwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());
        String redisKey = "login:token:" + user.getId();
        // 真实发放签名 Token！
        stringRedisTemplate.opsForValue().set(redisKey, token, 1, TimeUnit.DAYS);
        return token;
    }

    @Override
    public PageResult<User> listUsers(Integer pageNum, Integer pageSize, String keyword) {
        // 核心校验：必须是 ROOT 权限！
        if (!"ROOT".equalsIgnoreCase(UserContext.getRole())) {
            throw new BusinessException("权限不足！只有 ROOT 超级管理员有权查看全员列表。");
        }

        Page<User> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .select(User::getId, User::getUsername, User::getRole, User::getCreatedAt)
                .like(StringUtils.hasText(keyword), User::getUsername, keyword)
                .orderByAsc(User::getId);

        userMapper.selectPage(page, wrapper);
        return new PageResult<>(page.getTotal(), page.getRecords());


    }

    @Override
    public void updateUserRole(Long targetUserId, String newRole) {
        // 核心校验：只有 ROOT 才有权操作权限体系
        if (!"ROOT".equalsIgnoreCase(UserContext.getRole())) {
            throw new BusinessException("权限不足！只有 ROOT 超级管理员允许调整人员权限。");
        }

        // ================= 【核心防御铁律 1】：任何人不得修改自己自身 =================
        Long currentUserId = UserContext.getUserId();
        if (targetUserId.equals(currentUserId)) {
            throw new BusinessException("安全限制：不得修改您自身的角色权限，防止误操作降权！");
        }

        // ================= 【核心防御铁律 2】：1 号初始账号拥有代码级最高豁免权 =================
        if (targetUserId.equals(1L)) {
            throw new BusinessException("安全拦截：系统初始 1 号 ROOT 账号拥有代码级最高特权，严禁任何降权操作！");
        }

        User targetUser = userMapper.selectById(targetUserId);
        if (targetUser == null) {
            throw new BusinessException("目标用户不存在！(ID: " + targetUserId + ")");
        }

        // 如果对方已经是 ROOT，严禁被其他 ROOT 降权
        if ("ROOT".equalsIgnoreCase(targetUser.getRole())) {
            throw new BusinessException("安全限制：不得篡改其他 ROOT 根用户的权限！");
        }

        targetUser.setRole(newRole);
        userMapper.updateById(targetUser);
        System.out.println(">>> [Security] ROOT 用户 #" + currentUserId + " 将用户 #" + targetUserId + " 的角色调整为: " + newRole);
    }
}