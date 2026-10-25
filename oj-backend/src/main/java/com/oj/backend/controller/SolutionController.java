package com.oj.backend.controller;

import com.oj.backend.common.PageResult;
import com.oj.backend.common.Result;
import com.oj.backend.dto.SolutionCreateDTO;
import com.oj.backend.dto.SolutionQueryDTO;
import com.oj.backend.dto.SolutionUpdateDTO;
import com.oj.backend.service.SolutionService;
import com.oj.backend.vo.SolutionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "题解模块", description = "题解浏览、正文阅读、发布与点赞互动接口")
@RestController
@RequestMapping("/api/solution")
public class SolutionController {

    @Autowired
    private SolutionService solutionService;

    // 5.1 分页获取某题的题解列表 (保密题拦截，排除长文本 content) -> GET /api/solution/list
    @Operation(summary = "获取某题目的题解列表 (分页)")
    @GetMapping("/list")
    public Result<PageResult<SolutionVO>> listSolutions(@ParameterObject @Valid SolutionQueryDTO queryDTO) {
        return Result.success(solutionService.listSolutions(queryDTO));
    }

    // 5.2 获取题解完整详情 -> GET /api/solution/{id}
    @Operation(summary = "根据 ID 获取题解详情 (Markdown 正文)")
    @GetMapping("/{id}")
    public Result<SolutionVO> getSolutionById(@PathVariable Long id) {
        return Result.success(solutionService.getSolutionById(id));
    }

    // 5.3 发布新题解 (需登录，比赛保密题禁止发题解) -> POST /api/solution
    @Operation(summary = "发布新题解")
    @PostMapping
    public Result<SolutionVO> createSolution(@RequestBody @Valid SolutionCreateDTO dto) {
        return Result.success(solutionService.createSolution(dto));
    }

    // 5.4 修改题解 (仅作者本人与管理员可用) -> PUT /api/solution/{id}
    @Operation(summary = "修改题解")
    @PutMapping("/{id}")
    public Result<SolutionVO> updateSolution(
            @PathVariable Long id,
            @RequestBody @Valid SolutionUpdateDTO dto) {
        return Result.success(solutionService.updateSolution(id, dto));
    }

    // 5.5 逻辑删除题解 (仅作者本人与管理员可用) -> DELETE /api/solution/{id}
    @Operation(summary = "删除题解 (逻辑删除)")
    @DeleteMapping("/{id}")
    public Result<Void> deleteSolution(@PathVariable Long id) {
        solutionService.deleteSolution(id);
        return Result.success();
    }

    // 5.6 题解点赞 / 取消点赞切换 (需登录) -> POST /api/solution/{id}/like
    @Operation(summary = "题解点赞 / 取消点赞切换")
    @PostMapping("/{id}/like")
    public Result<Boolean> toggleLikeSolution(@PathVariable Long id) {
        return Result.success(solutionService.toggleLikeSolution(id));
    }
}