package com.oj.backend.controller;

import com.oj.backend.common.PageResult;
import com.oj.backend.common.Result;
import com.oj.backend.common.UserContext;
import com.oj.backend.dto.UserDTO;
import com.oj.backend.dto.UserRoleUpdateDTO;
import com.oj.backend.entity.User;
import com.oj.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户认证模块", description = "提供用户注册和登录的接口")
@RestController
@RequestMapping("/api")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 1.1 注册接口
    // 注册接口 (极简、干净)
    @Operation(summary = "用户注册", description = "用户注册接口，接收用户名和密码，返回注册结果")
    @PostMapping("/user/register")
    public Result<Void> register(@RequestBody @Valid UserDTO dto) {
        userService.register(dto.getUsername(), dto.getPassword());
        return Result.success();
    }

    // 登录接口
    @Operation(summary = "用户登录", description = "用户登录接口，接收用户名和密码，返回JWT令牌")
    @PostMapping("/user/login")
    public Result<String> login(@RequestBody @Valid UserDTO dto) {
        String token = userService.login(dto.getUsername(), dto.getPassword());
        // System.out.println("登录成功，生成的JWT令牌：" + token);
        return Result.success(token);
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping({"/user/info", "/current"})
    public Result<User> getUserInfo() {
        // 从当前上下文直接拿到用户 ID，不需要前端传！
        Long userId = UserContext.getUserId();
        User user = userService.findByUsername(UserContext.getUsername());
        return Result.success(user);
    }

    @Operation(summary = "用户登出", description = "用户登出接口，清除Redis中的登录凭证")
    @PostMapping("/user/logout")
    public Result<Void> logout() {
        Long userId = UserContext.getUserId();
        if (userId != null) {
            stringRedisTemplate.delete("login:token:" + userId);
        }
        return Result.success();
    }

    @Operation(summary = "获取全员列表 (仅 ROOT 权限)")
    @GetMapping("/admin/users")
    public Result<PageResult<User>> listUsers(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(userService.listUsers(pageNum, pageSize, keyword));
    }

    // 1.6 修改指定用户角色 (需 ROOT)
    @Operation(summary = "修改用户角色 (仅 ROOT 权限)")
    @PutMapping("/admin/users/{id}/role")
    public Result<Void> updateUserRole(
            @PathVariable Long id,
            @RequestBody @Valid UserRoleUpdateDTO dto) {
        userService.updateUserRole(id, dto.getRole());
        return Result.success();
    }
}