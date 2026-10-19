package com.oj.backend.service;

import com.oj.backend.common.PageResult;
import com.oj.backend.dto.*;
import com.oj.backend.entity.Contest;
import com.oj.backend.entity.Submission;
import com.oj.backend.vo.AdminContestVO;
import com.oj.backend.vo.ContestProblemVO;
import com.oj.backend.vo.ContestRankVO;

import java.util.List;

public interface ContestService {
    // 5.1 获取公开比赛列表 (带动态计算的 status)
    PageResult<Contest> listContests(Integer pageNum, Integer pageSize);

    // 5.2 获取单场比赛详情
    Contest getContestById(Long id);

    // 5.3 获取比赛题目列表 (未开赛时拦截)
    List<ContestProblemVO> getContestProblems(Long contestId);

    // 5.4 获取比赛单题详情
    ContestProblemVO getContestProblemDetail(Long contestId, String displayId);

    // 5.5 比赛内提交代码 (打上 contest_id 异步入队)
    Submission submitContest(Long contestId, ContestSubmitDTO dto);

    // 5.6 核心 ICPC 榜单与罚时动态计算引擎
    List<ContestRankVO> getContestRanklist(Long contestId);

    // 5.7 管理员创建比赛 (批量绑定 A/B/C 并自动发布公告贴)
    Contest createContest(ContestCreateDTO dto);

    // 5.8 赛后一键公开题目到公共题库
    void publishContestProblems(Long contestId);

    // 5.9 用户报名比赛
    void registerContest(Long contestId);

    // 5.10 查询当前登录用户是否已报名
    boolean getRegisterStatus(Long contestId);

    PageResult<AdminContestVO> listContestsForAdmin(Integer pageNum, Integer pageSize, String keyword);

    void updateContest(Long id, ContestUpdateDTO dto);

    void deleteContest(Long id);
}