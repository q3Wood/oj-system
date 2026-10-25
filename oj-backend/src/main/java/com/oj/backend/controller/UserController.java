package com.oj.backend.controller;

import com.oj.backend.common.PageResult;
import com.oj.backend.common.Result;
import com.oj.backend.common.UserContext;
import com.oj.backend.dto.UserDTO;
import com.oj.backend.dto.UserQueryDTO;
import com.oj.backend.dto.UserRoleUpdateDTO;
import com.oj.backend.service.UserService;
import com.oj.backend.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户模块", description = "提供用户注册、登录、信息查询及后台权限管理接口")
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    // 1.1 用户注册 -> POST /api/user/register
    @Operation(summary = "用户注册", description = "接收用户名和密码，返回注册结果")
    @PostMapping("/register")
    public Result<Void> register(@RequestBody @Valid UserDTO dto) {
        userService.register(dto.getUsername(), dto.getPassword());
        return Result.success();
    }

    // 1.2 用户登录 -> POST /api/user/login
    @Operation(summary = "用户登录", description = "接收用户名和密码，返回JWT令牌")
    @PostMapping("/login")
    public Result<String> login(@RequestBody @Valid UserDTO dto) {
        String token = userService.login(dto.getUsername(), dto.getPassword());
        return Result.success(token);
    }

    // 1.3 获取当前登录用户信息 -> GET /api/user/info
    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/info")
    public Result<UserVO> getUserInfo() {
        Long userId = UserContext.getUserId();
        return Result.success(userService.getUserInfoById(userId));
    }

    // 1.4 用户登出 -> POST /api/user/logout
    @Operation(summary = "用户登出", description = "清除Redis中的登录凭证")
    @PostMapping("/logout")
    public Result<Void> logout() {
        Long userId = UserContext.getUserId();
        userService.logout(userId);
        return Result.success();
    }

    // ==================== 以下为后台管理接口 (/api/user/admin/**) ====================

    // 1.5 多条件分页获取用户列表 (仅 ROOT 权限) -> GET /api/user/admin/list
    @Operation(summary = "多条件分页查询用户列表 (仅 ROOT 权限)")
    @GetMapping("/admin/list")
    public Result<PageResult<UserVO>> listUsers(@ParameterObject @Valid UserQueryDTO queryDTO) {
        return Result.success(userService.listUsers(queryDTO));
    }

    // 1.6 修改指定用户角色 (仅 ROOT 权限) -> PUT /api/user/admin/{id}/role
    @Operation(summary = "修改用户角色 (仅 ROOT 权限)")
    @PutMapping("/admin/{id}/role")
    public Result<Void> updateUserRole(
            @PathVariable Long id,
            @RequestBody @Valid UserRoleUpdateDTO dto) {
        userService.updateUserRole(id, dto.getRole());
        return Result.success();
    }
}