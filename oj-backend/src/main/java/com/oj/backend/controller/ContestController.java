package com.oj.backend.controller;

import com.oj.backend.common.PageResult;
import com.oj.backend.common.Result;
import com.oj.backend.dto.ContestCreateDTO;
import com.oj.backend.dto.ContestQueryDTO;
import com.oj.backend.dto.ContestUpdateDTO;
import com.oj.backend.vo.ContestProblemVO;
import com.oj.backend.vo.ContestRankVO;
import com.oj.backend.vo.ContestVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.oj.backend.service.ContestService;

import java.util.List;

@Tag(name = "竞赛模块", description = "比赛浏览、题目映射、报名与 ICPC 榜单接口")
@RestController
@RequestMapping("/api/contest")
public class ContestController {

    @Autowired
    private ContestService contestService;

    // 4.1 分页获取比赛大厅列表 (游客可看，自动回显题数、报名人数与个人报名状态) -> GET /api/contest/list
    @Operation(summary = "获取公开比赛列表 (分页)")
    @GetMapping("/list")
    public Result<PageResult<ContestVO>> listContests(@ParameterObject @Valid ContestQueryDTO queryDTO) {
        return Result.success(contestService.listContests(queryDTO));
    }

    // 4.2 获取单场比赛基本详情 -> GET /api/contest/{id}
    @Operation(summary = "获取单场比赛详情")
    @GetMapping("/{id}")
    public Result<ContestVO> getContestById(@PathVariable Long id) {
        return Result.success(contestService.getContestDetail(id));
    }

    // 4.3 获取比赛题目列表 (需开赛且已报名) -> GET /api/contest/{id}/problems
    @Operation(summary = "获取比赛题目列表")
    @GetMapping("/{id}/problems")
    public Result<List<ContestProblemVO>> getContestProblems(@PathVariable Long id) {
        return Result.success(contestService.getContestProblems(id));
    }

    // 4.4 获取比赛单题题面详情 -> GET /api/contest/{id}/problems/{displayId}
    @Operation(summary = "获取比赛单题题面详情")
    @GetMapping("/{id}/problems/{displayId}")
    public Result<ContestProblemVO> getContestProblemDetail(
            @PathVariable Long id, @PathVariable String displayId) {
        return Result.success(contestService.getContestProblemDetail(id, displayId));
    }

    // 4.5 获取比赛实时 ICPC 榜单 -> GET /api/contest/{id}/ranklist
    @Operation(summary = "获取比赛实时 ICPC 榜单 (滚榜)")
    @GetMapping("/{id}/ranklist")
    public Result<List<ContestRankVO>> getContestRanklist(@PathVariable Long id) {
        return Result.success(contestService.getContestRanklist(id));
    }

    // 4.6 用户报名比赛 -> POST /api/contest/{id}/register
    @Operation(summary = "用户报名参赛 (需登录)")
    @PostMapping("/{id}/register")
    public Result<Void> registerContest(@PathVariable Long id) {
        contestService.registerContest(id);
        return Result.success();
    }

    // ==================== 以下为后台管理接口 (/api/contest/admin/**) ====================

    // 4.7 管理员创建比赛 -> POST /api/contest/admin
    @Operation(summary = "管理员创建比赛并自动发帖")
    @PostMapping("/admin")
    public Result<ContestVO> createContest(@RequestBody @Valid ContestCreateDTO dto) {
        return Result.success(contestService.createContest(dto));
    }

    // 4.8 管理员编辑比赛 -> PUT /api/contest/admin/{id}
    @Operation(summary = "管理员编辑比赛 (防作弊约束)")
    @PutMapping("/admin/{id}")
    public Result<Void> updateContest(
            @PathVariable Long id,
            @RequestBody @Valid ContestUpdateDTO dto) {
        contestService.updateContest(id, dto);
        return Result.success();
    }

    // 4.9 管理员删除比赛 -> DELETE /api/contest/admin/{id}
    @Operation(summary = "管理员删除比赛")
    @DeleteMapping("/admin/{id}")
    public Result<Void> deleteContest(@PathVariable Long id) {
        contestService.deleteContest(id);
        return Result.success();
    }

    // 4.10 赛后一键公开保密题到公共题库 -> POST /api/contest/admin/{id}/publish-problems
    @Operation(summary = "管理员赛后一键公开题目至公共题库")
    @PostMapping("/admin/{id}/publish-problems")
    public Result<Void> publishContestProblems(@PathVariable Long id) {
        contestService.publishContestProblems(id);
        return Result.success();
    }
}