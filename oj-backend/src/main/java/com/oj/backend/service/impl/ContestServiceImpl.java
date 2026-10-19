package com.oj.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.backend.common.BusinessException;
import com.oj.backend.common.PageResult;
import com.oj.backend.common.UserContext;
import com.oj.backend.dto.*;
import com.oj.backend.vo.*;
import com.oj.backend.entity.*;
import com.oj.backend.mapper.*;
import com.oj.backend.service.ContestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.oj.backend.dto.ContestUpdateDTO;
import com.oj.backend.vo.AdminContestVO;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ContestServiceImpl implements ContestService {

    @Autowired
    private ContestMapper contestMapper;

    @Autowired
    private ContestProblemMapper contestProblemMapper;

    @Autowired
    private ProblemMapper problemMapper;

    @Autowired
    private SubmissionMapper submissionMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private ContestUserMapper contestUserMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 5.1 获取比赛列表 (自动计算状态)
    @Override
    public PageResult<Contest> listContests(Integer pageNum, Integer pageSize) {
        Page<Contest> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Contest> queryWrapper = new LambdaQueryWrapper<Contest>()
                .orderByDesc(Contest::getId);

        contestMapper.selectPage(page, queryWrapper);
        List<Contest> records = page.getRecords();
        records.forEach(this::calculateStatus);

        return new PageResult<>(page.getTotal(), records);
    }

    // 5.2 获取单场比赛详情
    @Override
    public Contest getContestById(Long id) {
        Contest contest = contestMapper.selectById(id);
        if (contest == null) {
            throw new BusinessException("比赛不存在！(ID: " + id + ")");
        }
        calculateStatus(contest);
        return contest;
    }

    // 5.3 获取比赛题目列表 (未开赛时拦截)
    @Override
    public List<ContestProblemVO> getContestProblems(Long contestId) {
        Contest contest = getContestById(contestId);
        String status = contest.getStatus();

        // 1. 未开赛：除了管理员，一律不准看题
        if ("NOT_STARTED".equals(status) && !"ADMIN".equalsIgnoreCase(UserContext.getRole())) {
            throw new BusinessException("比赛尚未开始，题目保密中！");
        }

        // 2. 比赛进行中：必须是已报名的用户才能看题 (管理员免报名)
        if ("RUNNING".equals(status) && !"ADMIN".equalsIgnoreCase(UserContext.getRole())) {
            if (!getRegisterStatus(contestId)) {
                throw new BusinessException("您尚未报名本场比赛，请先报名再查看题目！");
            }
        }

        // 3. 比赛已结束：所有人自由公开看题，不设拦截！

        List<ContestProblem> cpList = contestProblemMapper.selectList(
                new LambdaQueryWrapper<ContestProblem>()
                        .eq(ContestProblem::getContestId, contestId)
                        .orderByAsc(ContestProblem::getDisplayId)
        );

        return cpList.stream().map(cp -> new ContestProblemVO(
                cp.getDisplayId(), cp.getDisplayTitle(), cp.getProblemId(), null, null, null
        )).collect(Collectors.toList());
    }

    // 5.4 获取单题详情 (题面做题页)
    @Override
    public ContestProblemVO getContestProblemDetail(Long contestId, String displayId) {
        Contest contest = getContestById(contestId);
        String status = contest.getStatus();
        // 1. 未开赛：除了管理员，一律不准看题
        if ("NOT_STARTED".equals(status) && !"ADMIN".equalsIgnoreCase(UserContext.getRole())) {
            throw new BusinessException("比赛尚未开始，题目保密中！");
        }

        // 2. 比赛进行中：必须是已报名的用户才能看题 (管理员免报名)
        if ("RUNNING".equals(status) && !"ADMIN".equalsIgnoreCase(UserContext.getRole())) {
            if (!getRegisterStatus(contestId)) {
                throw new BusinessException("您尚未报名本场比赛，请先报名再查看题目！");
            }
        }

        // 3. 比赛已结束：所有人自由公开看题，不设拦截！
        ContestProblem cp = contestProblemMapper.selectOne(
                new LambdaQueryWrapper<ContestProblem>()
                        .eq(ContestProblem::getContestId, contestId)
                        .eq(ContestProblem::getDisplayId, displayId)
        );
        if (cp == null) {
            throw new BusinessException("比赛题目不存在！(题号: " + displayId + ")");
        }

        Problem problem = problemMapper.selectById(cp.getProblemId());
        if (problem == null) {
            throw new BusinessException("底层题目数据丢失！");
        }

        return new ContestProblemVO(
                cp.getDisplayId(), cp.getDisplayTitle(), cp.getProblemId(),
                problem.getDescription(), problem.getTimeLimitMs(), problem.getMemoryLimitMb()
        );
    }

    // 5.5 比赛内提交代码 (打上 contest_id 异步推队列)
    @Override
    public Submission submitContest(Long contestId, ContestSubmitDTO dto) {
        Contest contest = getContestById(contestId);
        if ("NOT_STARTED".equals(contest.getStatus())) {
            throw new BusinessException("比赛尚未开始，严禁提前提交！");
        }

        if ("RUNNING".equals(contest.getStatus()) &&
                !"ADMIN".equalsIgnoreCase(UserContext.getRole())) {
            if (!getRegisterStatus(contestId)) {
                throw new BusinessException("您尚未报名本场比赛，请先报名再提交代码！");
            }
        }
//        if ("ENDED".equals(contest.getStatus())) {
//            throw new BusinessException("比赛已结束，请前往公共题库练习！");
//        }

        // 校验题目是否属于该场比赛
        ContestProblem cp = contestProblemMapper.selectOne(
                new LambdaQueryWrapper<ContestProblem>()
                        .eq(ContestProblem::getContestId, contestId)
                        .eq(ContestProblem::getProblemId, dto.getProblemId())
        );
        if (cp == null) {
            throw new BusinessException("非法提交：该题目不属于本场比赛！");
        }

        Long userId = UserContext.getUserId();

        String rateLimitKey = "limit:submit:user:" + userId;
        Boolean acquire = stringRedisTemplate.opsForValue().setIfAbsent(rateLimitKey, "1", 5, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(acquire)) {
            throw new BusinessException("提交过于频繁，请稍后再试！");
        }

        // 插入数据库，带上 contest_id
        Submission submission = new Submission();
        submission.setUserId(userId);
        submission.setProblemId(dto.getProblemId());
        submission.setContestId(contestId); // 核心绑定比赛 ID
        submission.setLanguage(dto.getLanguage());
        submission.setCode(dto.getCode());
        submission.setVerdict("PENDING");
        submission.setMaxTimeMs(0);
        submission.setPeakMemoryKb(0);
        submission.setJudgeDetails("[]");
        submission.setCreatedAt(LocalDateTime.now());
        submission.setUpdatedAt(LocalDateTime.now());

        submissionMapper.insert(submission);

        // 推入 Redis 队列
        stringRedisTemplate.opsForList().rightPush(
                SubmissionServiceImpl.JUDGE_QUEUE_KEY,
                submission.getId().toString()
        );

        return submission;
    }

    // 5.6 核心 ICPC 榜单与罚时动态计算引擎
    @Override
    public List<ContestRankVO> getContestRanklist(Long contestId) {
        Contest contest = getContestById(contestId);

        // 1. 获取比赛内所有题目映射 (problemId -> displayId 如: 1001 -> "A")
        List<ContestProblem> cpList = contestProblemMapper.selectList(
                new LambdaQueryWrapper<ContestProblem>().eq(ContestProblem::getContestId, contestId)
        );
        Map<Long, String> probToDisplayMap = cpList.stream()
                .collect(Collectors.toMap(ContestProblem::getProblemId, ContestProblem::getDisplayId));

        // 2. 查出本场比赛在起止时间内的所有提交记录 (严格按时间升序)
        List<Submission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getContestId, contestId)
                        .ge(Submission::getCreatedAt, contest.getStartTime())
                        .le(Submission::getCreatedAt, contest.getEndTime())
                        .orderByAsc(Submission::getId)
        );

        // 3. 按选手分组计算 ICPC 状态
        Map<Long, ContestRankVO> userRankMap = new HashMap<>();

        for (Submission sub : submissions) {
            Long uid = sub.getUserId();
            String displayId = probToDisplayMap.get(sub.getProblemId());
            if (displayId == null) continue;

            ContestRankVO rankVO = userRankMap.computeIfAbsent(uid, k -> {
                ContestRankVO vo = new ContestRankVO();
                vo.setUserId(uid);
                return vo;
            });

            ContestRankVO.ProblemResult pResult = rankVO.getProblemResults()
                    .computeIfAbsent(displayId, k -> new ContestRankVO.ProblemResult());

            // 规则：只要这道题已经 AC 了，后续的提交一律忽略
            if (pResult.isSolved()) {
                continue;
            }

            if ("Accepted".equals(sub.getVerdict())) {
                pResult.setSolved(true);
                // 计算开赛到首个 AC 的分钟数
                long timeMin = Math.max(0, Duration.between(contest.getStartTime(), sub.getCreatedAt()).toMinutes());
                pResult.setTimeMin(timeMin);

                // 累加总成绩：解题数+1，总罚时增加 (耗时 + 错题数 * 20)
                rankVO.setSolvedCount(rankVO.getSolvedCount() + 1);
                long currentProblemPenalty = timeMin + (pResult.getWrongTries() * 20L);
                rankVO.setTotalPenalty(rankVO.getTotalPenalty() + currentProblemPenalty);

            } else if (!"Compile Error".equals(sub.getVerdict()) && !"PENDING".equals(sub.getVerdict()) && !"RUNNING".equals(sub.getVerdict())) {
                // ICPC 规则：编译错误 (CE) 不计入罚时！其他非 AC 提交累加一次错误尝试
                pResult.setWrongTries(pResult.getWrongTries() + 1);
            }
        }

        // 4. 排序：解题数降序 -> 总罚时升序
        List<ContestRankVO> rankList = new ArrayList<>(userRankMap.values());
        rankList.sort((a, b) -> {
            if (!a.getSolvedCount().equals(b.getSolvedCount())) {
                return b.getSolvedCount() - a.getSolvedCount(); // AC 多的排前
            }
            if (!a.getTotalPenalty().equals(b.getTotalPenalty())) {
                return a.getTotalPenalty().compareTo(b.getTotalPenalty()); // 罚时少的排前
            }
            return a.getUserId().compareTo(b.getUserId());
        });

        // 5. 补齐用户名与名次序号 (1, 2, 3...)
        if (!rankList.isEmpty()) {
            Set<Long> userIds = rankList.stream().map(ContestRankVO::getUserId).collect(Collectors.toSet());
            List<User> users = userMapper.selectBatchIds(userIds);
            Map<Long, String> userNames = users.stream().collect(Collectors.toMap(User::getId, User::getUsername));

            int currentRank = 1;
            for (ContestRankVO vo : rankList) {
                vo.setRank(currentRank++);
                vo.setUsername(userNames.getOrDefault(vo.getUserId(), "选手 " + vo.getUserId()));
            }
        }

        return rankList;
    }

    // 5.7 管理员创建比赛 (批量绑定并自动发布公告贴)
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Contest createContest(ContestCreateDTO dto) {
        if (!"ADMIN".equalsIgnoreCase(UserContext.getRole()) && !"ROOT".equalsIgnoreCase(UserContext.getRole())) {
            throw new BusinessException("权限不足！只有系统管理员 (ADMIN) 允许发布比赛。");
        }
        if (!dto.getStartTime().isBefore(dto.getEndTime())) {
            throw new BusinessException("比赛结束时间必须晚于开始时间！");
        }

        // 1. 插入比赛主表
        Contest contest = new Contest();
        contest.setTitle(dto.getTitle());
        contest.setDescription(dto.getDescription());
        contest.setStartTime(dto.getStartTime());
        contest.setEndTime(dto.getEndTime());
        contest.setCreatedAt(LocalDateTime.now());
        contest.setUpdatedAt(LocalDateTime.now());
        contestMapper.insert(contest);

        // 2. 批量绑定题目，自动赋予 A, B, C, D...
        char letter = 'A';
        for (ContestCreateDTO.ContestProblemItem item : dto.getProblems()) {
            ContestProblem cp = new ContestProblem();
            cp.setContestId(contest.getId());
            cp.setProblemId(item.getProblemId());
            cp.setDisplayId(String.valueOf(letter++));
            cp.setDisplayTitle(item.getDisplayTitle());
            cp.setCreatedAt(LocalDateTime.now());
            contestProblemMapper.insert(cp);
        }

        // 3. 【核心联动】：在 tb_post 自动发布官方置顶比赛公告贴！
        Post post = new Post();
        post.setUserId(UserContext.getUserId());
        post.setTitle("【官方赛事公告】" + contest.getTitle() + " 正式发布！");
        post.setContent(generateContestPostMarkdown(contest));
        post.setCategory("ANNOUNCEMENT");
        post.setRelatedId(contest.getId());
        post.setIsPinned(1); // 置顶
        post.setViews(0);
        post.setLikes(0);
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        postMapper.insert(post);

        return contest;
    }

    // 5.8 赛后一键公开题目到公共题库
    @Override
    public void publishContestProblems(Long contestId) {
        if (!"ADMIN".equalsIgnoreCase(UserContext.getRole())) {
            throw new BusinessException("权限不足！只有系统管理员允许公开题目。");
        }

        List<ContestProblem> cpList = contestProblemMapper.selectList(
                new LambdaQueryWrapper<ContestProblem>().eq(ContestProblem::getContestId, contestId)
        );
        if (cpList.isEmpty()) return;

        List<Long> problemIds = cpList.stream().map(ContestProblem::getProblemId).collect(Collectors.toList());

        // 一条 SQL 批量翻转可见性状态：UPDATE tb_problem SET is_public = 1 WHERE id IN (...)
        problemMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Problem>()
                        .in(Problem::getId, problemIds)
                        .set(Problem::getIsPublic, 1)
        );
        System.out.println(">>> [Contest] 成功将比赛 #" + contestId + " 的 " + problemIds.size() + " 道保密题目开放至公共题库！");
    }

    // 5.9 用户报名参赛
    @Override
    public void registerContest(Long contestId) {
        Long userId = UserContext.getUserId();
        Contest contest = getContestById(contestId);

        if ("ENDED".equals(contest.getStatus())) {
            throw new BusinessException("比赛已结束，无需报名，可直接自由练习！");
        }

        // 检查是否已经报名过
        Long count = contestUserMapper.selectCount(
                new LambdaQueryWrapper<ContestUser>()
                        .eq(ContestUser::getContestId, contestId)
                        .eq(ContestUser::getUserId, userId)
        );
        if (count > 0) {
            throw new BusinessException("您已经报名过本场比赛，无需重复报名！");
        }

        ContestUser cu = new ContestUser();
        cu.setContestId(contestId);
        cu.setUserId(userId);
        cu.setCreatedAt(LocalDateTime.now());
        contestUserMapper.insert(cu);
    }

    // 5.10 查询当前用户是否已报名
    @Override
    public boolean getRegisterStatus(Long contestId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return false; // 未登录必定未报名
        }
        Long count = contestUserMapper.selectCount(
                new LambdaQueryWrapper<ContestUser>()
                        .eq(ContestUser::getContestId, contestId)
                        .eq(ContestUser::getUserId, userId)
        );
        return count > 0;
    }



    // 1. 管理员后台列表 (带题数、报名人数、全公开标志)
    @Override
    public PageResult<AdminContestVO> listContestsForAdmin(Integer pageNum, Integer pageSize, String keyword) {
        checkAdminOrRoot();

        Page<Contest> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Contest> wrapper = new LambdaQueryWrapper<Contest>()
                .like(StringUtils.hasText(keyword), Contest::getTitle, keyword)
                .orderByDesc(Contest::getId);

        contestMapper.selectPage(page, wrapper);
        List<Contest> records = page.getRecords();

        List<AdminContestVO> voList = new ArrayList<>();
        for (Contest c : records) {
            calculateStatus(c);
            AdminContestVO vo = new AdminContestVO();
            vo.setId(c.getId());
            vo.setTitle(c.getTitle());
            vo.setDescription(c.getDescription());
            vo.setStartTime(c.getStartTime());
            vo.setEndTime(c.getEndTime());
            vo.setStatus(c.getStatus());
            vo.setCreatedAt(c.getCreatedAt());

            // 统计题目数
            Long pCount = contestProblemMapper.selectCount(
                    new LambdaQueryWrapper<ContestProblem>().eq(ContestProblem::getContestId, c.getId())
            );
            vo.setProblemCount(pCount.intValue());

            // 统计报名人数
            Long uCount = contestUserMapper.selectCount(
                    new LambdaQueryWrapper<ContestUser>().eq(ContestUser::getContestId, c.getId())
            );
            vo.setRegisteredCount(uCount.intValue());

            // 检查题目是否已全部公开
            List<ContestProblem> cpList = contestProblemMapper.selectList(
                    new LambdaQueryWrapper<ContestProblem>().eq(ContestProblem::getContestId, c.getId())
            );
            if (!cpList.isEmpty()) {
                List<Long> pIds = cpList.stream().map(ContestProblem::getProblemId).collect(Collectors.toList());
                Long secretCount = problemMapper.selectCount(
                        new LambdaQueryWrapper<Problem>().in(Problem::getId, pIds).eq(Problem::getIsPublic, 0)
                );
                vo.setIsAllPublic(secretCount == 0);
            } else {
                vo.setIsAllPublic(true);
            }

            voList.add(vo);
        }

        return new PageResult<>(page.getTotal(), voList);
    }

    // 2. 修改比赛 (防作弊红线落地)
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateContest(Long id, ContestUpdateDTO dto) {
        checkAdminOrRoot();

        Contest contest = contestMapper.selectById(id);
        if (contest == null) {
            throw new BusinessException("比赛不存在！");
        }
        calculateStatus(contest);
        String currentStatus = contest.getStatus();

        if (!dto.getStartTime().isBefore(dto.getEndTime())) {
            throw new BusinessException("结束时间必须晚于开始时间！");
        }

        // ================= 【核心业务防作弊红线】 =================
        if ("RUNNING".equals(currentStatus)) {
            // 比赛进行中：严禁修改开始时间！
            if (!contest.getStartTime().isEqual(dto.getStartTime())) {
                throw new BusinessException("比赛正在进行中，严禁修改开始时间 (防止榜单罚时错乱)！");
            }
            // 只允许延长结束时间，不允许提前结束正在打的比赛
            if (dto.getEndTime().isBefore(contest.getEndTime())) {
                throw new BusinessException("比赛进行中，只允许延长比赛结束时间！");
            }
        } else if ("ENDED".equals(currentStatus)) {
            // 比赛已结束：禁止再次修改时间
            if (!contest.getStartTime().isEqual(dto.getStartTime()) || !contest.getEndTime().isEqual(dto.getEndTime())) {
                throw new BusinessException("比赛已圆满结束，禁止修改历史起止时间！");
            }
        }

        contest.setTitle(dto.getTitle());
        contest.setDescription(dto.getDescription());
        contest.setStartTime(dto.getStartTime());
        contest.setEndTime(dto.getEndTime());
        contest.setUpdatedAt(LocalDateTime.now());
        contestMapper.updateById(contest);

        // 如果未开赛，允许重新编排 A/B/C 题目映射
        if ("NOT_STARTED".equals(currentStatus)) {
            // 先清空旧映射
            contestProblemMapper.delete(new LambdaQueryWrapper<ContestProblem>().eq(ContestProblem::getContestId, id));
            char letter = 'A';
            for (ContestCreateDTO.ContestProblemItem item : dto.getProblems()) {
                ContestProblem cp = new ContestProblem();
                cp.setContestId(id);
                cp.setProblemId(item.getProblemId());
                cp.setDisplayId(String.valueOf(letter++));
                cp.setDisplayTitle(item.getDisplayTitle());
                cp.setCreatedAt(LocalDateTime.now());
                contestProblemMapper.insert(cp);
            }
        }
    }

    // 3. 删除比赛 (安全边界：绝不误删底层题目)
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteContest(Long id) {
        checkAdminOrRoot();

        Contest contest = contestMapper.selectById(id);
        if (contest == null) {
            throw new BusinessException("比赛不存在！");
        }

        // 1. 删除比赛主表
        contestMapper.deleteById(id);
        // 2. 删除题目映射关系 (注意：底层的 tb_problem 毫发无损！)
        contestProblemMapper.delete(new LambdaQueryWrapper<ContestProblem>().eq(ContestProblem::getContestId, id));
        // 3. 删除报名记录
        contestUserMapper.delete(new LambdaQueryWrapper<ContestUser>().eq(ContestUser::getContestId, id));
    }

    private void checkAdminOrRoot() {
        String role = UserContext.getRole();
        if (!"ADMIN".equalsIgnoreCase(role) && !"ROOT".equalsIgnoreCase(role)) {
            throw new BusinessException("权限不足！只有管理员才允许进行此操作。");
        }
    }


    // 辅助方法：动态计算当前比赛状态
    private void calculateStatus(Contest contest) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(contest.getStartTime())) {
            contest.setStatus("NOT_STARTED");
        } else if (now.isAfter(contest.getEndTime())) {
            contest.setStatus("ENDED");
        } else {
            contest.setStatus("RUNNING");
        }
    }

    // 辅助方法：自动发帖 Markdown 模板生成
    private String generateContestPostMarkdown(Contest contest) {
        return "### 🏆 " + contest.getTitle() + " 已经发布！\n\n" +
                "- **赛制模式**：标准 ACM/ICPC 赛制 (通过题数优先，同题数按罚时排序)\n" +
                "- **开赛时间**：`" + contest.getStartTime() + "`\n" +
                "- **结束时间**：`" + contest.getEndTime() + "`\n\n" +
                "#### 竞赛规程须知：\n" +
                "1. 比赛期间题目处于保密状态，开赛后准时解锁并开放提交；\n" +
                "2. 每道题首次 AC 之前的非 CE 错误提交均计入 **20 分钟罚时**；\n" +
                "3. 比赛结束后，本场所有题目将一键开放至公共题库供赛后补题！\n\n" +
                "祝各位选手发挥出色，AK 全场！💪";
    }
}