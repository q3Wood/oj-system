package com.oj.backend.controller;

import com.oj.backend.common.PageResult;
import com.oj.backend.common.Result;
import com.oj.backend.dto.ProblemCreateDTO;
import com.oj.backend.dto.ProblemUpdateDTO;
import com.oj.backend.entity.Problem;
import com.oj.backend.service.ProblemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@Tag(name = "题目模块", description = "题目统一查询、题面浏览与后台管理")
@RestController
@RequestMapping("/api/problems")
public class ProblemController {

    @Autowired
    private ProblemService problemService;

    // 1. 全局统一查询接口 (自动兼顾前台题库大厅与后台管理列表！)
    @Operation(summary = "分页获取题目列表 (智能权限识别)")
    @GetMapping
    public Result<PageResult<Problem>> listProblems(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer isPublic) {
        return Result.success(problemService.listProblems(pageNum, pageSize, keyword, isPublic));
    }

    // 2. 查单题详情
    @Operation(summary = "获取指定题目详情")
    @GetMapping("/{id}")
    public Result<Problem> getProblemById(@PathVariable Long id) {
        return Result.success(problemService.getProblemById(id));
    }

    // 3. 管理员出题 (自动发号 + 解压 Zip)
    @Operation(summary = "管理员创建题目 (multipart/form-data)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Problem> createProblem(@ModelAttribute @Valid ProblemCreateDTO dto) {
        return Result.success(problemService.createProblem(dto));
    }

    // 4. 管理员统一编辑题目 (可改时限、题面描述与公开状态)
    @Operation(summary = "管理员编辑修改题目")
    @PutMapping(value = "/{id}")
    public Result<Problem> updateProblem(
            @PathVariable Long id,
            @ModelAttribute ProblemUpdateDTO dto) {
        return Result.success(problemService.updateProblem(id, dto));
    }
}