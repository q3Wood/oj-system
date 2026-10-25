package com.oj.backend.service;

import com.oj.backend.common.PageResult;
import com.oj.backend.dto.ContestCreateDTO;
import com.oj.backend.dto.ContestQueryDTO;
import com.oj.backend.dto.ContestUpdateDTO;
import com.oj.backend.vo.ContestProblemVO;
import com.oj.backend.vo.ContestRankVO;
import com.oj.backend.vo.ContestVO;

import java.util.List;

public interface ContestService {

    /**
     * 4.1 分页多条件获取比赛大厅列表 (支持按标题搜索，自动统计题数、报名人数与个人报名状态)
     */
    PageResult<ContestVO> listContests(ContestQueryDTO queryDTO);

    /**
     * 4.2 获取单场比赛基本详情
     */
    ContestVO getContestDetail(Long id);

    /**
     * 4.3 获取比赛题目列表 (需开赛且已报名，未开赛仅管理员可查看)
     */
    List<ContestProblemVO> getContestProblems(Long contestId);

    /**
     * 4.4 获取比赛单题题面详情 (需开赛且已报名)
     */
    ContestProblemVO getContestProblemDetail(Long contestId, String displayId);

    /**
     * 4.5 获取比赛实时 ICPC 榜单 (动态计算解题数、开赛分钟数与罚时)
     */
    List<ContestRankVO> getContestRanklist(Long contestId);

    /**
     * 4.6 用户报名参加比赛 (需登录，比赛结束后不可报名)
     */
    void registerContest(Long contestId);

    // ==================== 以下为管理员接口 ====================

    /**
     * 4.7 管理员创建新比赛并自动发布置顶赛事公告贴 (需 ADMIN / ROOT)
     */
    ContestVO createContest(ContestCreateDTO dto);

    /**
     * 4.8 管理员编辑修改比赛信息 (严格遵守防作弊红线)
     */
    void updateContest(Long id, ContestUpdateDTO dto);

    /**
     * 4.9 管理员删除比赛 (级联清理题目映射与报名记录，底层题目数据不删)
     */
    void deleteContest(Long id);

    /**
     * 4.10 赛后管理员一键将保密题公开到公共题库
     */
    void publishContestProblems(Long contestId);
}