package com.oj.backend.controller;

import com.oj.backend.common.PageResult;
import com.oj.backend.common.Result;
import com.oj.backend.dto.SubmitDTO;
import com.oj.backend.entity.Submission;
import com.oj.backend.service.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "提交与评测模块", description = "代码提交、结果回溯与提交大厅接口")
@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    @Autowired
    private SubmissionService submissionService;

    // 3.1 提交代码进行评测 (受登录拦截器保护)
    @Operation(summary = "提交代码评测")
    @PostMapping
    public Result<Submission> submit(@RequestBody @Valid SubmitDTO dto) {
        Submission result = submissionService.submit(dto);
        return Result.success(result);
    }

    // 3.2 查询单次提交详情
    @Operation(summary = "查询单次提交记录详情")
    @GetMapping("/{id}")
    public Result<Submission> getById(@PathVariable Long id) {
        return Result.success(submissionService.getById(id));
    }

    // 3.3 分页多条件查询提交记录大厅
    @Operation(summary = "分页查询提交记录列表 (支持多条件筛选)")
    @GetMapping
    public Result<PageResult<Submission>> listSubmissions(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long problemId,
            @RequestParam(required = false) String verdict,
            @RequestParam(required = false) String username) {
        return Result.success(submissionService.listSubmissions(pageNum, pageSize, userId, problemId, verdict, username));
    }
}