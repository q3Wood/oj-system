package com.oj.backend.controller;

import com.oj.backend.common.PageResult;
import com.oj.backend.common.Result;
import com.oj.backend.dto.ProblemCreateDTO;
import com.oj.backend.dto.ProblemQueryDTO;
import com.oj.backend.dto.ProblemUpdateDTO;
import com.oj.backend.service.ProblemService;
import com.oj.backend.vo.ProblemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@Tag(name = "题目模块", description = "题目多条件查询、题面浏览与后台出题管理")
@RestController
@RequestMapping("/api/problem")
public class ProblemController {

    @Autowired
    private ProblemService problemService;

    // 2.1 多条件分页获取题目列表 (智能权限识别 + 自动携带用户做题状态) -> GET /api/problem/list
    @Operation(summary = "多条件分页获取题目列表")
    @GetMapping("/list")
    public Result<PageResult<ProblemVO>> listProblems(@ParameterObject @Valid ProblemQueryDTO queryDTO) {
        return Result.success(problemService.listProblems(queryDTO));
    }

    // 2.2 获取单题详情 -> GET /api/problem/{id}
    @Operation(summary = "获取指定题目详情")
    @GetMapping("/{id}")
    public Result<ProblemVO> getProblemById(@PathVariable Long id) {
        return Result.success(problemService.getProblemById(id));
    }

    // ==================== 以下为后台管理接口 (/api/problem/admin/**) ====================

    // 2.3 管理员创建题目 -> POST /api/problem/admin
    @Operation(summary = "管理员创建题目 (multipart/form-data)")
    @PostMapping(value = "/admin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ProblemVO> createProblem(@ModelAttribute @Valid ProblemCreateDTO dto) {
        return Result.success(problemService.createProblem(dto));
    }

    // 2.4 管理员编辑修改题目 -> PUT /api/problem/admin/{id}
    @Operation(summary = "管理员编辑修改题目 (multipart/form-data)")
    @PutMapping(value = "/admin/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ProblemVO> updateProblem(
            @PathVariable Long id,
            @ModelAttribute @Valid ProblemUpdateDTO dto) {
        return Result.success(problemService.updateProblem(id, dto));
    }

    // 2.5 管理员逻辑删除题目 -> DELETE /api/problem/admin/{id}
    @Operation(summary = "管理员删除题目 (逻辑软删除)")
    @DeleteMapping("/admin/{id}")
    public Result<Void> deleteProblem(@PathVariable Long id) {
        problemService.deleteProblem(id);
        return Result.success();
    }

    // 2.6 在 ProblemController.java 中新增 (2.6 接口)：
    @Operation(summary = "管理员快捷切换题目可见性 (1-公开, 0-比赛保密)")
    @PutMapping("/admin/{id}/visibility")
    public Result<Void> updateProblemVisibility(
            @PathVariable Long id,
            @RequestParam Integer isPublic) {
        problemService.updateProblemVisibility(id, isPublic);
        return Result.success();
    }
}