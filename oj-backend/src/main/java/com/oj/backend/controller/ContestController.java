package com.oj.backend.controller;

import com.oj.backend.common.PageResult;
import com.oj.backend.common.Result;
import com.oj.backend.dto.*;
import com.oj.backend.entity.Contest;
import com.oj.backend.entity.Submission;
import com.oj.backend.service.ContestService;
import com.oj.backend.vo.ContestProblemVO;
import com.oj.backend.vo.ContestRankVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.oj.backend.dto.ContestUpdateDTO;
import com.oj.backend.vo.AdminContestVO;
import java.util.List;

@Tag(name = "竞赛模块", description = "比赛浏览、题目映射、比赛提交与 ICPC 榜单接口")
@RestController
@RequestMapping("/api")
public class ContestController {

    @Autowired
    private ContestService contestService;

    // 5.1 获取公开比赛列表 (公开)
    @Operation(summary = "获取公开比赛列表 (分页)")
    @GetMapping("/contests")
    public Result<PageResult<Contest>> listContests(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(contestService.listContests(pageNum, pageSize));
    }

    // 5.2 获取单场比赛详情 (公开)
    @Operation(summary = "获取比赛详情")
    @GetMapping("/contests/{id}")
    public Result<Contest> getContestById(@PathVariable Long id) {
        return Result.success(contestService.getContestById(id));
    }

    // 5.3 获取比赛题目列表 (需开赛)
    @Operation(summary = "获取比赛题目列表 (需开赛)")
    @GetMapping("/contests/{id}/problems")
    public Result<List<ContestProblemVO>> getContestProblems(@PathVariable Long id) {
        return Result.success(contestService.getContestProblems(id));
    }

    // 5.4 获取比赛单题详情
    @Operation(summary = "获取比赛单题题面详情")
    @GetMapping("/contests/{id}/problems/{displayId}")
    public Result<ContestProblemVO> getContestProblemDetail(
            @PathVariable Long id, @PathVariable String displayId) {
        return Result.success(contestService.getContestProblemDetail(id, displayId));
    }

    // 5.5 比赛内提交代码 (需登录)
    @Operation(summary = "比赛内提交代码进行评测")
    @PostMapping("/contests/{id}/submissions")
    public Result<Submission> submitContest(
            @PathVariable Long id, @RequestBody @Valid ContestSubmitDTO dto) {
        return Result.success(contestService.submitContest(id, dto));
    }

    // 5.6 获取比赛实时 ICPC 榜单 (公开)
    @Operation(summary = "获取比赛实时 ICPC 榜单 (滚榜)")
    @GetMapping("/contests/{id}/ranklist")
    public Result<List<ContestRankVO>> getContestRanklist(@PathVariable Long id) {
        return Result.success(contestService.getContestRanklist(id));
    }

    // 5.7 管理员创建比赛 (需 ADMIN)
    @Operation(summary = "管理员创建比赛并自动发帖")
    @PostMapping("/admin/contests")
    public Result<Contest> createContest(@RequestBody @Valid ContestCreateDTO dto) {
        return Result.success(contestService.createContest(dto));
    }

    // 5.8 赛后一键公开题目到公共题库 (需 ADMIN)
    @Operation(summary = "管理员赛后一键公开题目至公共题库")
    @PostMapping("/admin/contests/{id}/publish-problems")
    public Result<Void> publishContestProblems(@PathVariable Long id) {
        contestService.publishContestProblems(id);
        return Result.success();
    }

    // 5.9 用户报名比赛 (需登录)
    @Operation(summary = "用户报名参赛")
    @PostMapping("/contests/{id}/register")
    public Result<Void> registerContest(@PathVariable Long id) {
        contestService.registerContest(id);
        return Result.success();
    }

    // 5.10 查询当前用户是否已报名
    @Operation(summary = "查询个人报名状态")
    @GetMapping("/contests/{id}/register-status")
    public Result<Boolean> getRegisterStatus(@PathVariable Long id) {
        return Result.success(contestService.getRegisterStatus(id));
    }



    // 管理员获取比赛管理列表
    @Operation(summary = "管理员获取全量比赛列表 (带题数与报名人数统计)")
    @GetMapping("/admin/contests")
    public Result<PageResult<AdminContestVO>> listContestsForAdmin(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(contestService.listContestsForAdmin(pageNum, pageSize, keyword));
    }

    // 管理员编辑比赛
    @Operation(summary = "管理员编辑比赛")
    @PutMapping("/admin/contests/{id}")
    public Result<Void> updateContest(
            @PathVariable Long id,
            @RequestBody @Valid ContestUpdateDTO dto) {
        contestService.updateContest(id, dto);
        return Result.success();
    }

    // 管理员删除比赛
    @Operation(summary = "管理员删除比赛")
    @DeleteMapping("/admin/contests/{id}")
    public Result<Void> deleteContest(@PathVariable Long id) {
        contestService.deleteContest(id);
        return Result.success();
    }
}