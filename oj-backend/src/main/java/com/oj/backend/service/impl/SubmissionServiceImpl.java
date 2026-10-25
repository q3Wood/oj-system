package com.oj.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.backend.common.BusinessException;
import com.oj.backend.common.JudgeConstants;
import com.oj.backend.common.PageResult;
import com.oj.backend.common.RedisConstants;
import com.oj.backend.common.UserContext;
import com.oj.backend.dto.SubmissionQueryDTO;
import com.oj.backend.dto.SubmitDTO;
import com.oj.backend.entity.*;
import com.oj.backend.mapper.*;
import com.oj.backend.service.SubmissionService;
import com.oj.backend.vo.SubmissionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SubmissionServiceImpl implements SubmissionService {

    @Autowired
    private SubmissionMapper submissionMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ProblemMapper problemMapper;

    @Autowired
    private ContestMapper contestMapper;

    @Autowired
    private ContestProblemMapper contestProblemMapper;

    @Autowired
    private ContestUserMapper contestUserMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    // @Transactional(rollbackFor = Exception.class)
    public SubmissionVO submit(SubmitDTO dto) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录后再提交代码！");
        }

        // 1. 提交频率限流 (使用 RedisConstants 常量)
        String rateLimitKey = RedisConstants.RATE_LIMIT_SUBMIT_PREFIX + userId;
        Boolean acquire = stringRedisTemplate.opsForValue().setIfAbsent(
                rateLimitKey, "1", RedisConstants.RATE_LIMIT_SUBMIT_SECONDS, TimeUnit.SECONDS
        );
        if (Boolean.FALSE.equals(acquire)) {
            throw new BusinessException("提交过于频繁，请等待 " + RedisConstants.RATE_LIMIT_SUBMIT_SECONDS + " 秒后再试！");
        }

        Problem problem = problemMapper.selectById(dto.getProblemId());
        if (problem == null) {
            throw new BusinessException("题目不存在！(ID: " + dto.getProblemId() + ")");
        }

        Long finalContestId = dto.getContestId();

        // 2. 校验提交场景：比赛提交 VS 日常提交
        if (finalContestId != null) {
            Contest contest = contestMapper.selectById(finalContestId);
            if (contest == null) {
                throw new BusinessException("提交的关联比赛不存在！(ID: " + finalContestId + ")");
            }

            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(contest.getStartTime())) {
                throw new BusinessException("比赛尚未开始，严禁提前提交！");
            } else if (now.isAfter(contest.getEndTime())) {
                // 若比赛已结束，降级为日常练习提交，不记录 contestId
                finalContestId = null;
                log.info(">>> [Submit] 比赛 #{} 已结束，用户 #{} 本次提交自动转为日常补题练习", contest.getId(), userId);
            } else {
                // 比赛进行中：非管理员必须校验是否报名、是否是比赛题
                if (!isAdminOrRoot()) {
                    Long registered = contestUserMapper.selectCount(
                            new LambdaQueryWrapper<ContestUser>()
                                    .eq(ContestUser::getContestId, finalContestId)
                                    .eq(ContestUser::getUserId, userId)
                    );
                    if (registered == null || registered == 0) {
                        throw new BusinessException("您尚未报名本场比赛，请先报名再提交代码！");
                    }
                }
                Long cpCount = contestProblemMapper.selectCount(
                        new LambdaQueryWrapper<ContestProblem>()
                                .eq(ContestProblem::getContestId, finalContestId)
                                .eq(ContestProblem::getProblemId, dto.getProblemId())
                );
                if (cpCount == null || cpCount == 0) {
                    throw new BusinessException("非法提交：该题目不属于本场比赛！");
                }
            }
        }

        // 若最终判定为日常练习提交，严禁直接提交未公开的保密题
        if (finalContestId == null) {
            if (problem.getIsPublic() != null && problem.getIsPublic() == 0 && !isAdminOrRoot()) {
                throw new BusinessException(403, "该题目属于比赛保密题，暂未对公众开放，禁止提交！");
            }
        }

        // 3. 落库插入提交记录
        Submission submission = new Submission();
        submission.setUserId(userId);
        submission.setProblemId(dto.getProblemId());
        submission.setContestId(finalContestId);
        submission.setLanguage(dto.getLanguage());
        submission.setCode(dto.getCode());
        submission.setVerdict(JudgeConstants.PENDING);
        submission.setMaxTimeMs(0);
        submission.setPeakMemoryKb(0);
        submission.setJudgeDetails("[]");
        submission.setCreatedAt(LocalDateTime.now());
        submission.setUpdatedAt(LocalDateTime.now());
        submissionMapper.insert(submission);

        // 4. 异步推入 Redis 判题队列
        try {
            stringRedisTemplate.opsForList().rightPush(RedisConstants.JUDGE_QUEUE_KEY, submission.getId().toString());
        } catch (Exception e) {
            log.error("推入判题队列失败, submissionId={}", submission.getId(), e);
            submissionMapper.deleteById(submission.getId()); // 入队失败时清理该记录
            throw new BusinessException("系统繁忙，未能加入评测队列，请重试！");
        }

        SubmissionVO vo = SubmissionVO.fromEntity(submission);
        vo.setUsername(UserContext.getUsername());
        vo.setProblemTitle(problem.getTitle());
        return vo;
    }

    @Override
    public SubmissionVO getById(Long id) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "请先登录后再查看提交详情！");
        }

        Submission submission = submissionMapper.selectById(id);
        if (submission == null) {
            throw new BusinessException("提交记录不存在！(ID: " + id + ")");
        }

        boolean isSelf = submission.getUserId().equals(currentUserId);
        boolean isAdmin = isAdminOrRoot();

        // 核心防抄袭红线：非本人且非管理员看他人提交时，执行严格审查
        if (!isSelf && !isAdmin) {
            if (submission.getContestId() != null) {
                Contest contest = contestMapper.selectById(submission.getContestId());
                if (contest != null && LocalDateTime.now().isBefore(contest.getEndTime())) {
                    throw new BusinessException(403, "比赛正在进行中，严禁查看其他选手的提交代码与明细！");
                }
            }
            Problem problem = problemMapper.selectById(submission.getProblemId());
            if (problem != null && problem.getIsPublic() != null && problem.getIsPublic() == 0) {
                throw new BusinessException(403, "该题目尚在保密阶段，禁止查看代码！");
            }
        }

        SubmissionVO vo = SubmissionVO.fromEntity(submission);

        User user = userMapper.selectById(submission.getUserId());
        if (user != null) vo.setUsername(user.getUsername());
        Problem prob = problemMapper.selectById(submission.getProblemId());
        if (prob != null) vo.setProblemTitle(prob.getTitle());

        return vo;
    }

    @Override
    public PageResult<SubmissionVO> listSubmissions(SubmissionQueryDTO queryDTO) {
        Page<Submission> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());

        LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<Submission>()
                .select(Submission::getId, Submission::getUserId, Submission::getProblemId,
                        Submission::getContestId, Submission::getLanguage, Submission::getVerdict,
                        Submission::getMaxTimeMs, Submission::getPeakMemoryKb, Submission::getCreatedAt)
                .eq(queryDTO.getUserId() != null, Submission::getUserId, queryDTO.getUserId())
                .eq(queryDTO.getProblemId() != null, Submission::getProblemId, queryDTO.getProblemId())
                .eq(queryDTO.getContestId() != null, Submission::getContestId, queryDTO.getContestId())
                .eq(StringUtils.hasText(queryDTO.getVerdict()), Submission::getVerdict, queryDTO.getVerdict())
                .orderByDesc(Submission::getId);

        String username = queryDTO.getUsername();
        if (StringUtils.hasText(username)) {
            List<User> matchedUsers = userMapper.selectList(
                    new LambdaQueryWrapper<User>().like(User::getUsername, username.trim())
            );
            if (matchedUsers.isEmpty()) {
                return new PageResult<>(0L, Collections.emptyList());
            }
            wrapper.in(Submission::getUserId, matchedUsers.stream().map(User::getId).toList());
        }

        submissionMapper.selectPage(page, wrapper);
        List<Submission> records = page.getRecords();
        if (records.isEmpty()) {
            return new PageResult<>(page.getTotal(), Collections.emptyList());
        }

        Set<Long> userIds = records.stream().map(Submission::getUserId).collect(Collectors.toSet());
        Map<Long, String> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        Set<Long> probIds = records.stream().map(Submission::getProblemId).collect(Collectors.toSet());
        Map<Long, String> probMap = problemMapper.selectBatchIds(probIds).stream()
                .collect(Collectors.toMap(Problem::getId, Problem::getTitle));

        List<SubmissionVO> voList = records.stream().map(sub -> {
            SubmissionVO vo = SubmissionVO.fromEntity(sub);
            vo.setUsername(userMap.getOrDefault(sub.getUserId(), "用户#" + sub.getUserId()));
            vo.setProblemTitle(probMap.getOrDefault(sub.getProblemId(), "题目#" + sub.getProblemId()));
            return vo;
        }).toList();

        return new PageResult<>(page.getTotal(), voList);
    }

    private boolean isAdminOrRoot() {
        String role = UserContext.getRole();
        return "ADMIN".equalsIgnoreCase(role) || "ROOT".equalsIgnoreCase(role);
    }
}