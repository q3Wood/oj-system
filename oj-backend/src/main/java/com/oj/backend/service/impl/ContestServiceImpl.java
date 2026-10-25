package com.oj.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.backend.common.BusinessException;
import com.oj.backend.common.JudgeConstants;
import com.oj.backend.common.PageResult;
import com.oj.backend.common.UserContext;
import com.oj.backend.dto.ContestCreateDTO;
import com.oj.backend.dto.ContestQueryDTO;
import com.oj.backend.dto.ContestUpdateDTO;
import com.oj.backend.entity.*;
import com.oj.backend.mapper.*;
import com.oj.backend.service.ContestService;
import com.oj.backend.vo.ContestProblemVO;
import com.oj.backend.vo.ContestRankVO;
import com.oj.backend.vo.ContestVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
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

    @Override
    public PageResult<ContestVO> listContests(ContestQueryDTO queryDTO) {
        Page<Contest> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        LambdaQueryWrapper<Contest> queryWrapper = new LambdaQueryWrapper<Contest>()
                .like(StringUtils.hasText(queryDTO.getKeyword()), Contest::getTitle, queryDTO.getKeyword())
                .orderByDesc(Contest::getId);

        contestMapper.selectPage(page, queryWrapper);
        List<Contest> records = page.getRecords();
        if (records.isEmpty()) {
            return new PageResult<>(page.getTotal(), Collections.emptyList());
        }

        Long currentUserId = UserContext.getUserId();
        List<Long> contestIds = records.stream().map(Contest::getId).toList();

        // 1. 批量查询各比赛绑定的题目映射
        List<ContestProblem> cpList = contestProblemMapper.selectList(
                new LambdaQueryWrapper<ContestProblem>().in(ContestProblem::getContestId, contestIds)
        );
        Map<Long, List<ContestProblem>> cpByContestMap = cpList.stream()
                .collect(Collectors.groupingBy(ContestProblem::getContestId));

        // 批量查出所有关联题目的公开状态，用于计算 isAllPublic (拒绝 N+1 查询)
        Set<Long> allProblemIds = cpList.stream().map(ContestProblem::getProblemId).collect(Collectors.toSet());
        Set<Long> secretProblemIds = allProblemIds.isEmpty() ? Collections.emptySet() :
                problemMapper.selectList(
                        new LambdaQueryWrapper<Problem>()
                                .select(Problem::getId)
                                .in(Problem::getId, allProblemIds)
                                .eq(Problem::getIsPublic, 0)
                ).stream().map(Problem::getId).collect(Collectors.toSet());

        // 2. 批量统计报名人数与当前用户报名状态
        List<ContestUser> cuList = contestUserMapper.selectList(
                new LambdaQueryWrapper<ContestUser>().in(ContestUser::getContestId, contestIds)
        );
        Map<Long, Long> registeredCountMap = cuList.stream()
                .collect(Collectors.groupingBy(ContestUser::getContestId, Collectors.counting()));

        Set<Long> userRegisteredContestIds = (currentUserId == null) ? Collections.emptySet() :
                cuList.stream()
                        .filter(cu -> cu.getUserId().equals(currentUserId))
                        .map(ContestUser::getContestId)
                        .collect(Collectors.toSet());

        List<ContestVO> voList = records.stream().map(c -> {
            ContestVO vo = new ContestVO();
            BeanUtils.copyProperties(c, vo);
            vo.setStatus(calculateStatus(c));

            List<ContestProblem> myProblems = cpByContestMap.getOrDefault(c.getId(), Collections.emptyList());
            vo.setProblemCount(myProblems.size());
            vo.setRegisteredCount(registeredCountMap.getOrDefault(c.getId(), 0L).intValue());
            vo.setIsRegistered(userRegisteredContestIds.contains(c.getId()));

            // 判断本场比赛的所有题目是否都已经公开 (没有任何一道题在 secretProblemIds 中)
            boolean allPublic = myProblems.isEmpty() ||
                    myProblems.stream().noneMatch(cp -> secretProblemIds.contains(cp.getProblemId()));
            vo.setIsAllPublic(allPublic);

            return vo;
        }).toList();

        return new PageResult<>(page.getTotal(), voList);
    }

    @Override
    public ContestVO getContestDetail(Long id) {
        Contest contest = contestMapper.selectById(id);
        if (contest == null) {
            throw new BusinessException("比赛不存在！(ID: " + id + ")");
        }
        ContestVO vo = new ContestVO();
        BeanUtils.copyProperties(contest, vo);
        vo.setStatus(calculateStatus(contest));

        Long pCount = contestProblemMapper.selectCount(
                new LambdaQueryWrapper<ContestProblem>().eq(ContestProblem::getContestId, id)
        );
        vo.setProblemCount(pCount == null ? 0 : pCount.intValue());

        Long uCount = contestUserMapper.selectCount(
                new LambdaQueryWrapper<ContestUser>().eq(ContestUser::getContestId, id)
        );
        vo.setRegisteredCount(uCount == null ? 0 : uCount.intValue());

        Long uid = UserContext.getUserId();
        if (uid != null) {
            vo.setIsRegistered(isUserRegistered(id, uid));
        }

        return vo;
    }

    @Override
    public List<ContestProblemVO> getContestProblems(Long contestId) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) {
            throw new BusinessException("比赛不存在！");
        }
        String status = calculateStatus(contest);

        if ("NOT_STARTED".equals(status) && !isAdminOrRoot()) {
            throw new BusinessException("比赛尚未开始，题目保密中！");
        }
        if ("RUNNING".equals(status) && !isAdminOrRoot()) {
            if (!isUserRegistered(contestId, UserContext.getUserId())) {
                throw new BusinessException("您尚未报名本场比赛，请先报名再查看题目！");
            }
        }

        List<ContestProblem> cpList = contestProblemMapper.selectList(
                new LambdaQueryWrapper<ContestProblem>()
                        .eq(ContestProblem::getContestId, contestId)
                        .orderByAsc(ContestProblem::getDisplayId)
        );

        return cpList.stream().map(cp -> new ContestProblemVO(
                cp.getDisplayId(), cp.getDisplayTitle(), cp.getProblemId(), null, null, null
        )).toList();
    }

    @Override
    public ContestProblemVO getContestProblemDetail(Long contestId, String displayId) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) {
            throw new BusinessException("比赛不存在！");
        }
        String status = calculateStatus(contest);

        if ("NOT_STARTED".equals(status) && !isAdminOrRoot()) {
            throw new BusinessException("比赛尚未开始，题目保密中！");
        }
        if ("RUNNING".equals(status) && !isAdminOrRoot()) {
            if (!isUserRegistered(contestId, UserContext.getUserId())) {
                throw new BusinessException("您尚未报名本场比赛，请先报名再查看题目！");
            }
        }

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

    @Override
    public List<ContestRankVO> getContestRanklist(Long contestId) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) {
            throw new BusinessException("比赛不存在！");
        }

        List<ContestProblem> cpList = contestProblemMapper.selectList(
                new LambdaQueryWrapper<ContestProblem>().eq(ContestProblem::getContestId, contestId)
        );
        Map<Long, String> probToDisplayMap = cpList.stream()
                .collect(Collectors.toMap(ContestProblem::getProblemId, ContestProblem::getDisplayId));

        List<Submission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getContestId, contestId)
                        .ge(Submission::getCreatedAt, contest.getStartTime())
                        .le(Submission::getCreatedAt, contest.getEndTime())
                        .orderByAsc(Submission::getId)
        );

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

            if (pResult.isSolved()) continue;

            String verdict = sub.getVerdict();
            boolean isAc = JudgeConstants.ACCEPTED.equalsIgnoreCase(verdict) || JudgeConstants.AC.equalsIgnoreCase(verdict);

            if (isAc) {
                pResult.setSolved(true);
                long timeMin = Math.max(0, Duration.between(contest.getStartTime(), sub.getCreatedAt()).toMinutes());
                pResult.setTimeMin(timeMin);

                rankVO.setSolvedCount(rankVO.getSolvedCount() + 1);
                long currentProblemPenalty = timeMin + (pResult.getWrongTries() * JudgeConstants.ICPC_PENALTY_MINUTES_PER_WRONG);
                rankVO.setTotalPenalty(rankVO.getTotalPenalty() + currentProblemPenalty);

            } else if (!JudgeConstants.COMPILE_ERROR.equalsIgnoreCase(verdict) &&
                    !JudgeConstants.PENDING.equalsIgnoreCase(verdict) &&
                    !JudgeConstants.RUNNING.equalsIgnoreCase(verdict)) {
                pResult.setWrongTries(pResult.getWrongTries() + 1);
            }
        }

        List<ContestRankVO> rankList = new ArrayList<>(userRankMap.values());
        rankList.sort((a, b) -> {
            if (!a.getSolvedCount().equals(b.getSolvedCount())) {
                return b.getSolvedCount() - a.getSolvedCount();
            }
            if (!a.getTotalPenalty().equals(b.getTotalPenalty())) {
                return a.getTotalPenalty().compareTo(b.getTotalPenalty());
            }
            return a.getUserId().compareTo(b.getUserId());
        });

        if (!rankList.isEmpty()) {
            Set<Long> userIds = rankList.stream().map(ContestRankVO::getUserId).collect(Collectors.toSet());
            Map<Long, String> userNames = userMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getUsername));

            int currentRank = 1;
            for (ContestRankVO vo : rankList) {
                vo.setRank(currentRank++);
                vo.setUsername(userNames.getOrDefault(vo.getUserId(), "选手 " + vo.getUserId()));
            }
        }
        return rankList;
    }

    @Override
    public void registerContest(Long contestId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录再报名参赛！");
        }
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) {
            throw new BusinessException("比赛不存在！");
        }
        if ("ENDED".equals(calculateStatus(contest))) {
            throw new BusinessException("比赛已圆满结束，无需报名，可直接前往题库补题练习！");
        }

        if (isUserRegistered(contestId, userId)) {
            throw new BusinessException("您已经报名过本场比赛，无需重复报名！");
        }

        ContestUser cu = new ContestUser();
        cu.setContestId(contestId);
        cu.setUserId(userId);
        cu.setCreatedAt(LocalDateTime.now());
        contestUserMapper.insert(cu);
        log.info(">>> [Contest] 用户 #{} 成功报名了比赛 #{}: {}", userId, contestId, contest.getTitle());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContestVO createContest(ContestCreateDTO dto) {
        checkAdminOrRoot();
        if (!dto.getStartTime().isBefore(dto.getEndTime())) {
            throw new BusinessException("比赛结束时间必须晚于开始时间！");
        }

        Contest contest = new Contest();
        contest.setTitle(dto.getTitle());
        contest.setDescription(dto.getDescription());
        contest.setStartTime(dto.getStartTime());
        contest.setEndTime(dto.getEndTime());
        contest.setCreatedAt(LocalDateTime.now());
        contest.setUpdatedAt(LocalDateTime.now());
        contestMapper.insert(contest);

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

        Post post = new Post();
        post.setUserId(UserContext.getUserId());
        post.setTitle("【官方赛事公告】" + contest.getTitle() + " 正式发布！");
        post.setContent(generateContestPostMarkdown(contest));
        post.setCategory("ANNOUNCEMENT");
        post.setRelatedId(contest.getId());
        post.setIsPinned(1);
        post.setViews(0);
        post.setLikes(0);
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        postMapper.insert(post);

        ContestVO vo = new ContestVO();
        BeanUtils.copyProperties(contest, vo);
        vo.setStatus(calculateStatus(contest));
        vo.setProblemCount(dto.getProblems().size());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateContest(Long id, ContestUpdateDTO dto) {
        checkAdminOrRoot();
        Contest contest = contestMapper.selectById(id);
        if (contest == null) {
            throw new BusinessException("比赛不存在！");
        }
        String currentStatus = calculateStatus(contest);

        if (!dto.getStartTime().isBefore(dto.getEndTime())) {
            throw new BusinessException("结束时间必须晚于开始时间！");
        }

        if ("RUNNING".equals(currentStatus)) {
            if (!contest.getStartTime().isEqual(dto.getStartTime())) {
                throw new BusinessException("比赛正在进行中，严禁修改开始时间 (防止榜单罚时错乱)！");
            }
            if (dto.getEndTime().isBefore(contest.getEndTime())) {
                throw new BusinessException("比赛进行中，只允许延长比赛结束时间！");
            }
        } else if ("ENDED".equals(currentStatus)) {
            if (!contest.getStartTime().isEqual(dto.getStartTime()) || !contest.getEndTime().isEqual(dto.getEndTime())) {
                throw new BusinessException("比赛已结束，禁止修改历史起止时间！");
            }
        }

        contest.setTitle(dto.getTitle());
        contest.setDescription(dto.getDescription());
        contest.setStartTime(dto.getStartTime());
        contest.setEndTime(dto.getEndTime());
        contest.setUpdatedAt(LocalDateTime.now());
        contestMapper.updateById(contest);

        if ("NOT_STARTED".equals(currentStatus) && dto.getProblems() != null) {
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteContest(Long id) {
        checkAdminOrRoot();
        Contest contest = contestMapper.selectById(id);
        if (contest == null) {
            throw new BusinessException("比赛不存在！");
        }
        contestMapper.deleteById(id);
        contestProblemMapper.delete(new LambdaQueryWrapper<ContestProblem>().eq(ContestProblem::getContestId, id));
        contestUserMapper.delete(new LambdaQueryWrapper<ContestUser>().eq(ContestUser::getContestId, id));
    }

    @Override
    public void publishContestProblems(Long contestId) {
        checkAdminOrRoot();
        List<ContestProblem> cpList = contestProblemMapper.selectList(
                new LambdaQueryWrapper<ContestProblem>().eq(ContestProblem::getContestId, contestId)
        );
        if (cpList.isEmpty()) return;

        List<Long> problemIds = cpList.stream().map(ContestProblem::getProblemId).toList();
        problemMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Problem>()
                        .in(Problem::getId, problemIds)
                        .set(Problem::getIsPublic, 1)
        );
        log.info(">>> [Contest] 成功将比赛 #{} 的 {} 道题目开放至公共题库！", contestId, problemIds.size());
    }

    private boolean isUserRegistered(Long contestId, Long userId) {
        if (userId == null) return false;
        Long count = contestUserMapper.selectCount(
                new LambdaQueryWrapper<ContestUser>()
                        .eq(ContestUser::getContestId, contestId)
                        .eq(ContestUser::getUserId, userId)
        );
        return count != null && count > 0;
    }

    private boolean isAdminOrRoot() {
        String role = UserContext.getRole();
        return "ADMIN".equalsIgnoreCase(role) || "ROOT".equalsIgnoreCase(role);
    }

    private void checkAdminOrRoot() {
        if (!isAdminOrRoot()) {
            throw new BusinessException(403, "权限不足！只有系统管理员才允许进行此操作。");
        }
    }

    private String calculateStatus(Contest contest) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(contest.getStartTime())) return "NOT_STARTED";
        if (now.isAfter(contest.getEndTime())) return "ENDED";
        return "RUNNING";
    }

    private String generateContestPostMarkdown(Contest contest) {
        return "### 🏆 " + contest.getTitle() + " 已经发布！\n\n" +
                "- **赛制模式**：标准 ACM/ICPC 赛制\n" +
                "- **开赛时间**：`" + contest.getStartTime() + "`\n" +
                "- **结束时间**：`" + contest.getEndTime() + "`\n\n" +
                "#### 竞赛规程须知：\n" +
                "1. 比赛期间题目处于保密状态，开赛后准时解锁并开放提交；\n" +
                "2. 首次通过 (AC) 之前的每次非 CE 提交累加 **20 分钟罚时**；\n" +
                "3. 比赛结束后，本场所有题目将开放至公共题库供赛后补题！\n\n" +
                "祝各位选手发挥出色！💪";
    }
}