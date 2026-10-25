package com.oj.backend.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.backend.common.JudgeConstants;
import com.oj.backend.common.RedisConstants;
import com.oj.backend.dto.JudgeResultDTO;
import com.oj.backend.entity.Problem;
import com.oj.backend.entity.Submission;
import com.oj.backend.mapper.ProblemMapper;
import com.oj.backend.mapper.SubmissionMapper;
import com.oj.backend.service.JudgeService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class JudgeQueueConsumer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SubmissionMapper submissionMapper;

    @Autowired
    private ProblemMapper problemMapper;

    @Autowired
    private JudgeService judgeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 限制最大同时跑 2 个评测，保护 CPU
    private final ExecutorService judgeWorkerPool = Executors.newFixedThreadPool(2);

    private volatile boolean running = true;

    @PostConstruct
    public void init() {
        recoverStaleTasks();

        Thread listenerThread = new Thread(this::listenQueue, RedisConstants.JUDGE_QUEUE_LISTENER_THREAD_NAME);
        listenerThread.setDaemon(true);
        listenerThread.start();
        log.info(">>> [System] Redis 异步判题队列消费者已成功启动！");
    }

    /**
     * 开机扫尾：清空 Redis 旧列表，重新捞取数据库中未结单任务入队
     */
    private void recoverStaleTasks() {
        try {
            LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<Submission>()
                    .in(Submission::getVerdict, JudgeConstants.RUNNING, JudgeConstants.PENDING)
                    .orderByAsc(Submission::getId);
            List<Submission> staleList = submissionMapper.selectList(wrapper);
            if (staleList == null || staleList.isEmpty()) return;

            // 先清空 Redis 旧队列，彻底避免二次入队重复评测
            stringRedisTemplate.delete(RedisConstants.JUDGE_QUEUE_KEY);
            log.info(">>> [Recover] 检测到 RUNNING 或 PENDING 提交 {} 条，已重置队列并按序重新推入...", staleList.size());

            for (Submission sub : staleList) {
                sub.setVerdict(JudgeConstants.PENDING);
                sub.setUpdatedAt(LocalDateTime.now());
                submissionMapper.updateById(sub);

                stringRedisTemplate.opsForList().rightPush(
                        RedisConstants.JUDGE_QUEUE_KEY,
                        String.valueOf(sub.getId())
                );
            }
        } catch (Exception e) {
            log.error("开机恢复判题任务发生异常: ", e);
        }
    }

    private void listenQueue() {
        while (running) {
            try {
                String submissionIdStr = stringRedisTemplate.opsForList()
                        .leftPop(RedisConstants.JUDGE_QUEUE_KEY, 2, TimeUnit.SECONDS);

                if (submissionIdStr != null && !submissionIdStr.trim().isEmpty()) {
                    Long submissionId = Long.valueOf(submissionIdStr.trim());
                    judgeWorkerPool.submit(() -> processTask(submissionId));
                }
            } catch (Exception e) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ignored) {}
            }
        }
    }

    private void processTask(Long submissionId) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) return;

        try {
            submission.setVerdict(JudgeConstants.RUNNING);
            submission.setUpdatedAt(LocalDateTime.now());
            submissionMapper.updateById(submission);

            Problem problem = problemMapper.selectById(submission.getProblemId());
            if (problem == null) {
                submission.setVerdict(JudgeConstants.SYSTEM_ERROR);
                submission.setJudgeDetails(objectMapper.writeValueAsString("底层题目数据丢失"));
                submissionMapper.updateById(submission);
                return;
            }

            JudgeResultDTO judgeResult = judgeService.executeJudge(
                    problem.getId(),
                    submission.getLanguage(),
                    submission.getCode(),
                    problem.getTimeLimitMs(),
                    problem.getMemoryLimitMb()
            );

            submission.setVerdict(judgeResult.getVerdict());
            submission.setMaxTimeMs(judgeResult.getMaxTimeMs());
            submission.setPeakMemoryKb(judgeResult.getPeakMemoryKb());

            if (JudgeConstants.COMPILE_ERROR.equalsIgnoreCase(judgeResult.getVerdict())) {
                submission.setJudgeDetails(objectMapper.writeValueAsString(judgeResult.getCompileLog()));
            } else {
                submission.setJudgeDetails(objectMapper.writeValueAsString(judgeResult.getDetails()));
            }
            submission.setUpdatedAt(LocalDateTime.now());
            submissionMapper.updateById(submission);
            log.info(">>> [Queue] 提交 #{} 评测完成，最终状态: {}", submissionId, judgeResult.getVerdict());

        } catch (Exception e) {
            log.error("评测任务执行失败, submissionId={}", submissionId, e);
            submission.setVerdict(JudgeConstants.SYSTEM_ERROR);
            try {
                submission.setJudgeDetails(objectMapper.writeValueAsString("评测机系统内部错误: " + e.getMessage()));
            } catch (Exception ignored) {}
            submission.setUpdatedAt(LocalDateTime.now());
            submissionMapper.updateById(submission);
        }
    }

    @PreDestroy
    public void destroy() {
        running = false;
        judgeWorkerPool.shutdown();
    }
}