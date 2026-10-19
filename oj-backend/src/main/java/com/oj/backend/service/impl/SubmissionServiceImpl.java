package com.oj.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.backend.common.BusinessException;
import com.oj.backend.common.PageResult;
import com.oj.backend.common.UserContext;
import com.oj.backend.dto.JudgeResultDTO;
import com.oj.backend.dto.SubmitDTO;
import com.oj.backend.entity.Problem;
import com.oj.backend.entity.Submission;
import com.oj.backend.entity.User;
import com.oj.backend.mapper.ProblemMapper;
import com.oj.backend.mapper.SubmissionMapper;
import com.oj.backend.mapper.UserMapper;
import com.oj.backend.service.JudgeService;
import com.oj.backend.service.SubmissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class SubmissionServiceImpl implements SubmissionService {

    @Autowired
    private SubmissionMapper submissionMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ProblemMapper problemMapper;

    @Autowired
    private JudgeService judgeService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public static final String JUDGE_QUEUE_KEY = "oj:judge:queue";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Submission submit(SubmitDTO dto) {
        Long userId = UserContext.getUserId();

        String rateLimitKey = "limit:submit:user:" + userId;
        Boolean acquire = stringRedisTemplate.opsForValue().setIfAbsent(rateLimitKey, "1", 5, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(acquire)) {
            throw new BusinessException("提交过于频繁，请稍后再试！");
        }

        Problem problem = problemMapper.selectById(dto.getProblemId());
        if (problem == null) {
            throw new BusinessException("题目不存在！(ID: " + dto.getProblemId() + ")");
        }

        Submission submission = new Submission();
        submission.setUserId(userId);
        submission.setProblemId(dto.getProblemId());
        submission.setLanguage(dto.getLanguage());
        submission.setCode(dto.getCode());
        submission.setVerdict("PENDING");
        submission.setMaxTimeMs(0);
        submission.setPeakMemoryKb(0);
        submission.setJudgeDetails("[]");
        submission.setCreatedAt(LocalDateTime.now());
        submission.setUpdatedAt(LocalDateTime.now());
        submissionMapper.insert(submission);

//        JudgeResultDTO judgeResult = judgeService.executeJudge(
//                problem.getId(),
//                dto.getLanguage(),
//                dto.getCode(),
//                problem.getTimeLimitMs(),
//                problem.getMemoryLimitMb()
//        );
//
//        submission.setVerdict(judgeResult.getVerdict());
//        submission.setMaxTimeMs(judgeResult.getMaxTimeMs());
//        submission.setPeakMemoryKb(judgeResult.getPeakMemoryKb());
//        try {
//            submission.setJudgeDetails(objectMapper.writeValueAsString(judgeResult.getDetails()));
//        } catch (Exception ignored) {}
//
//        submissionMapper.updateById(submission);
        // 异步处理判题结果，放入 Redis 队列
        try {
            stringRedisTemplate.opsForList().rightPush(JUDGE_QUEUE_KEY, submission.getId().toString());
            //stringRedisTemplate.opsForList().rightPush("jue", submission.getId().toString());
        } catch (Exception e) {
            throw new BusinessException("提交失败，无法加入判题队列！" + e.getMessage());
        }
        return submission;
    }

    @Override
    public Submission getById(Long id) {
        Submission submission = submissionMapper.selectById(id);
        if (submission == null) {
            throw new BusinessException("提交记录不存在！(ID: " + id + ")");
        }
        return submission;
    }

    @Override
    public PageResult<Submission> listSubmissions(Integer pageNum, Integer pageSize,
                                                  Long userId, Long problemId, String verdict, String username) {
        Page<Submission> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<Submission>()
                .select(Submission::getId, Submission::getUserId, Submission::getProblemId,
                        Submission::getLanguage, Submission::getVerdict, Submission::getMaxTimeMs,
                        Submission::getPeakMemoryKb, Submission::getCreatedAt)
                .eq(userId != null, Submission::getUserId, userId)
                .eq(problemId != null, Submission::getProblemId, problemId)
                .eq(StringUtils.hasText(verdict), Submission::getVerdict, verdict)
                .orderByDesc(Submission::getId);

        // 【新增 1】：如果传了用户名搜索，先搜匹配的用户 ID
        if (StringUtils.hasText(username)) {
            List<User> matchedUsers = userMapper.selectList(
                    new LambdaQueryWrapper<User>().like(User::getUsername, username.trim())
            );
            if (matchedUsers.isEmpty()) {
                // 如果一个匹配的用户都没有，直接返回空结果，避免全表扫描
                return new PageResult<>(0L, Collections.emptyList());
            }
            List<Long> matchedUserIds = matchedUsers.stream().map(User::getId).collect(Collectors.toList());
            wrapper.in(Submission::getUserId, matchedUserIds);
        }

        submissionMapper.selectPage(page, wrapper);
        List<Submission> records = page.getRecords();

        // 【新增 2】：批量查出所有用户名，避免 N+1 查询，极速回显
        if (!records.isEmpty()) {
            Set<Long> uIds = records.stream().map(Submission::getUserId).collect(Collectors.toSet());
            List<User> users = userMapper.selectBatchIds(uIds);
            Map<Long, String> userMap = users.stream().collect(Collectors.toMap(User::getId, User::getUsername));

            records.forEach(sub -> sub.setUsername(userMap.getOrDefault(sub.getUserId(), "用户#" + sub.getUserId())));
        }

        return new PageResult<>(page.getTotal(), records);
    }
}