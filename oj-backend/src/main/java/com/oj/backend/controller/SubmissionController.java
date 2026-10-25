package com.oj.backend.controller;

import com.oj.backend.common.PageResult;
import com.oj.backend.common.Result;
import com.oj.backend.dto.SubmissionQueryDTO;
import com.oj.backend.dto.SubmitDTO;
import com.oj.backend.service.SubmissionService;
import com.oj.backend.vo.SubmissionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "提交与评测模块", description = "代码提交、结果回溯与提交大厅接口")
@RestController
@RequestMapping("/api/submission")
public class SubmissionController {

    @Autowired
    private SubmissionService submissionService;

    // 3.1 统一代码提交入口 (支持日常提交与比赛提交自适应) -> POST /api/submission
    @Operation(summary = "统一提交代码评测 (需登录)")
    @PostMapping
    public Result<SubmissionVO> submit(@RequestBody @Valid SubmitDTO dto) {
        return Result.success(submissionService.submit(dto));
    }

    // 3.2 查询单次提交记录详情 (包含代码与测试点明细，赛中非本人禁止偷看) -> GET /api/submission/{id}
    @Operation(summary = "查询单次提交记录详情 (需登录且有权查看)")
    @GetMapping("/{id}")
    public Result<SubmissionVO> getById(@PathVariable Long id) {
        return Result.success(submissionService.getById(id));
    }

    // 3.3 分页多条件查询提交记录大厅 (游客可看列表) -> GET /api/submission/list
    @Operation(summary = "分页查询提交记录列表 (支持多条件筛选)")
    @GetMapping("/list")
    public Result<PageResult<SubmissionVO>> listSubmissions(@ParameterObject @Valid SubmissionQueryDTO queryDTO) {
        return Result.success(submissionService.listSubmissions(queryDTO));
    }
}