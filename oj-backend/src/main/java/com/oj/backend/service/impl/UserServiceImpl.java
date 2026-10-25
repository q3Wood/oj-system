package com.oj.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.backend.common.BusinessException;
import com.oj.backend.common.PageResult;
import com.oj.backend.common.UserContext;
import com.oj.backend.dto.UserQueryDTO;
import com.oj.backend.entity.User;
import com.oj.backend.mapper.UserMapper;
import com.oj.backend.service.UserService;
import com.oj.backend.utils.JwtUtil;
import com.oj.backend.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 统一管理 Redis Token 前缀，避免魔法字符串散落各处
    private static final String REDIS_TOKEN_PREFIX = "login:token:";

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
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("用户名或密码错误！");
        }

        String token = JwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());
        String redisKey = REDIS_TOKEN_PREFIX + user.getId();
        // 真实发放签名 Token，有效期 1 天
        stringRedisTemplate.opsForValue().set(redisKey, token, 1, TimeUnit.DAYS);
        return token;
    }

    @Override
    public UserVO getUserInfoById(Long userId) {
        if (userId == null) {
            throw new BusinessException("用户未登录！");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在！");
        }
        return UserVO.fromEntity(user);
    }

    @Override
    public void logout(Long userId) {
        if (userId != null) {
            stringRedisTemplate.delete(REDIS_TOKEN_PREFIX + userId);
        }
    }

    @Override
    public PageResult<UserVO> listUsers(UserQueryDTO queryDTO) {
        // 核心校验：必须是 ROOT 权限！
        if (!"ROOT".equalsIgnoreCase(UserContext.getRole())) {
            throw new BusinessException("权限不足！只有 ROOT 超级管理员有权查看全员列表。");
        }

        Page<User> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());

        String keyword = queryDTO.getKeyword();
        String role = queryDTO.getRole();

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .select(User::getId, User::getUsername, User::getRole, User::getCreatedAt)
                .like(StringUtils.hasText(keyword), User::getUsername, keyword)
                .eq(StringUtils.hasText(role), User::getRole, role) // 新增：按角色精准筛选
                .orderByAsc(User::getId);

        userMapper.selectPage(page, wrapper);

        // 将 Entity 列表转换为脱敏的 VO 列表
        List<UserVO> voList = page.getRecords().stream()
                .map(UserVO::fromEntity)
                .toList();

        return new PageResult<>(page.getTotal(), voList);
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

        // 角色未发生变化则直接返回，避免无意义写库和误踢下线
        if (newRole.equalsIgnoreCase(targetUser.getRole())) {
            return;
        }

        targetUser.setRole(newRole);
        userMapper.updateById(targetUser);

        // 【关键安全闭环】：销毁目标用户的 Redis Token，强制其重新登录以刷新 JWT 内的角色声明
        stringRedisTemplate.delete(REDIS_TOKEN_PREFIX + targetUserId);

        log.info(">>> [Security] ROOT 用户 #{} 将用户 #{} 的角色调整为: {}，并已强制刷新其会话", currentUserId, targetUserId, newRole);
    }
}