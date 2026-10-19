package com.oj.backend.controller;

import com.oj.backend.common.PageResult;
import com.oj.backend.common.Result;
import com.oj.backend.dto.SolutionCreateDTO;
import com.oj.backend.dto.SolutionUpdateDTO;
import com.oj.backend.entity.Solution;
import com.oj.backend.service.SolutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.service.annotation.DeleteExchange;

@Tag(name = "题解模块", description = "题解浏览、正文阅读与发布接口")
@RestController
@RequestMapping("/api/solutions")
public class SolutionController {

    @Autowired
    private SolutionService solutionService;

    // 4.1 获取某题的题解列表
    @Operation(summary = "获取某题目的题解列表 (分页)")
    @GetMapping
    public Result<PageResult<Solution>> listSolutions(
            @RequestParam Long problemId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(solutionService.listSolutions(problemId, pageNum, pageSize));
    }

    // 4.2 获取题解详情 (完整 Markdown)
    @Operation(summary = "根据 ID 获取题解详情")
    @GetMapping("/{id}")
    public Result<Solution> getSolutionById(@PathVariable Long id) {
        return Result.success(solutionService.getSolutionById(id));
    }

    // 4.3 发布新题解 (需登录)
    @Operation(summary = "发布新题解")
    @PostMapping
    public Result<Solution> createSolution(@RequestBody @Valid SolutionCreateDTO dto) {
        return Result.success(solutionService.createSolution(dto));
    }

    @Operation(summary = "修改题解")
    @PutMapping("/{id}")
    public Result<Solution> updateSolution(@PathVariable Long id, @RequestBody @Valid SolutionUpdateDTO dto) {
        return Result.success(solutionService.updateSolution(id, dto));
    }

    @Operation(summary = "删除题解")
    @DeleteMapping("/{id}")
    public Result<Void> deleteSolution(@PathVariable Long id) {
        solutionService.deleteSolution(id);
        return Result.success();
    }
}