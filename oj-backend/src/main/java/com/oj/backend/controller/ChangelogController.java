package com.oj.backend.controller;

import com.oj.backend.common.PageResult;
import com.oj.backend.common.Result;
import com.oj.backend.dto.ChangelogCreateDTO;
import com.oj.backend.dto.ChangelogQueryDTO;
import com.oj.backend.dto.ChangelogUpdateDTO;
import com.oj.backend.service.ChangelogService;
import com.oj.backend.vo.ChangelogLatestVO;
import com.oj.backend.vo.ChangelogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "系统更新日志模块", description = "全站版本演进时间轴、最新提醒摘要与 ROOT 专属维护接口")
@RestController
@RequestMapping("/api/changelog")
public class ChangelogController {

    @Autowired
    private ChangelogService changelogService;

    // 6.1 分页获取更新日志时间轴列表 (全网公开) -> GET /api/changelog/list
    @Operation(summary = "分页获取系统更新日志时间轴 (公开)")
    @GetMapping("/list")
    public Result<PageResult<ChangelogVO>> listChangelogs(@ParameterObject @Valid ChangelogQueryDTO queryDTO) {
        return Result.success(changelogService.listChangelogs(queryDTO));
    }

    // 6.2 获取最新一条更新日志摘要 (专供导航栏 NEW 角标检测，Redis 缓存加速) -> GET /api/changelog/latest
    @Operation(summary = "获取最新更新日志摘要 (公开)")
    @GetMapping("/latest")
    public Result<ChangelogLatestVO> getLatestChangelog() {
        return Result.success(changelogService.getLatestChangelog());
    }

    // 6.3 发布新更新日志 (仅限 ROOT) -> POST /api/changelog/admin
    @Operation(summary = "发布系统更新日志 (需 ROOT 权限)")
    @PostMapping("/admin")
    public Result<ChangelogVO> createChangelog(@RequestBody @Valid ChangelogCreateDTO dto) {
        return Result.success(changelogService.createChangelog(dto));
    }

    // 6.4 编辑修改更新日志 (仅限 ROOT) -> PUT /api/changelog/admin/{id}
    @Operation(summary = "编辑修改系统更新日志 (需 ROOT 权限)")
    @PutMapping("/admin/{id}")
    public Result<ChangelogVO> updateChangelog(@PathVariable Long id, @RequestBody @Valid ChangelogUpdateDTO dto) {
        return Result.success(changelogService.updateChangelog(id, dto));
    }

    // 6.5 删除更新日志 (逻辑软删除，仅限 ROOT) -> DELETE /api/changelog/admin/{id}
    @Operation(summary = "删除系统更新日志 (逻辑软删除，需 ROOT 权限)")
    @DeleteMapping("/admin/{id}")
    public Result<Void> deleteChangelog(@PathVariable Long id) {
        changelogService.deleteChangelog(id);
        return Result.success();
    }
}