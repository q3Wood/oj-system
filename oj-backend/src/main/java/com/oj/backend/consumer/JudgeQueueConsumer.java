package com.oj.backend.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.backend.dto.JudgeResultDTO;
import com.oj.backend.entity.Problem;
import com.oj.backend.entity.Submission;
import com.oj.backend.mapper.ProblemMapper;
import com.oj.backend.mapper.SubmissionMapper;
import com.oj.backend.service.JudgeService;
import com.oj.backend.service.impl.SubmissionServiceImpl;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jdk.jshell.spi.ExecutionControlProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    // 独立可控的评测工作线程池 (限制最大同时跑 2 个任务，防止打满 CPU)
    private final ExecutorService judgeWorkerPool = Executors.newFixedThreadPool(2);

    // 运行状态标志，支持应用优雅关机
    private volatile boolean running = true;

    @PostConstruct
    public void init() {
        // 开机扫尾未完成的任务
        recoverStaleTasks();

        // 启动一个常驻后台守护线程监听 Redis 队列
        Thread listenerThread = new Thread(this::listenQueue, "judge-queue-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
        System.out.println(">>> [System] Redis 异步判题队列消费者已成功启动！");
    }

    // 扫描数据库中状态为 "RUNNING" 的提交，恢复为 "PENDING" 以便重新评测
    private void recoverStaleTasks() {
        LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<Submission>()
                .in(Submission::getVerdict, "RUNNING", "PENDING")
                .orderByAsc(Submission::getId);
        List<Submission> staleList = submissionMapper.selectList(wrapper);
        if (staleList == null || staleList.isEmpty()) return;

        System.out.println(">>> [Recover] 检测到 RUNNING 或 PENDING 状态的提交共 " + staleList.size() + " 条，开始自动补入队列 ...");

        for (Submission sub : staleList) {
            sub.setVerdict("PENDING");
            sub.setUpdatedAt(LocalDateTime.now());
            submissionMapper.updateById(sub);

            // 补入 Redis 队列
            stringRedisTemplate.opsForList().rightPush(
                    SubmissionServiceImpl.JUDGE_QUEUE_KEY,
                    String.valueOf(sub.getId())
            );
            System.out.println(">>> [Recover] 补入队列的提交 ID: " + sub.getId());
        }
    }

    private void listenQueue() {
        while (running) {
            try {
                // 使用阻塞式出队 (BLPOP): 队列为空时最多挂起 2 秒，避免死循环吃满 CPU
                String submissionIdStr = stringRedisTemplate.opsForList()
                        .leftPop(SubmissionServiceImpl.JUDGE_QUEUE_KEY, 2, TimeUnit.SECONDS);

                if (submissionIdStr != null && !submissionIdStr.trim().isEmpty()) {
                    Long submissionId = Long.valueOf(submissionIdStr.trim());
                    // 派发给工作线程池异步执行评测
                    judgeWorkerPool.submit(() -> processTask(submissionId));
                }
            } catch (Exception e) {
                // 如果 Redis 短暂断连，等待 1 秒重试
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
            // 1. 将状态更新为 "RUNNING" (评测中)
            submission.setVerdict("RUNNING");
            submission.setUpdatedAt(LocalDateTime.now());
            submissionMapper.updateById(submission);

            Problem problem = problemMapper.selectById(submission.getProblemId());
            if (problem == null) {
                submission.setVerdict("System Error");
                submissionMapper.updateById(submission);
                return;
            }

            // 2. 调用 C++ core-judge 底层执行评测
            JudgeResultDTO judgeResult = judgeService.executeJudge(
                    problem.getId(),
                    submission.getLanguage(),
                    submission.getCode(),
                    problem.getTimeLimitMs(),
                    problem.getMemoryLimitMb()
            );

            // 3. 将评测结果更新回 MySQL
            submission.setVerdict(judgeResult.getVerdict());
            submission.setMaxTimeMs(judgeResult.getMaxTimeMs());
            submission.setPeakMemoryKb(judgeResult.getPeakMemoryKb());

            // 区分保存报错日志
            if ("Compile Error".equals(judgeResult.getVerdict())) {
                submission.setJudgeDetails(objectMapper.writeValueAsString(judgeResult.getCompileLog()));
            } else {
                submission.setJudgeDetails(objectMapper.writeValueAsString(judgeResult.getDetails()));
            }
//            try {
//                submission.setJudgeDetails(objectMapper.writeValueAsString(judgeResult.getDetails()));
//            } catch (Exception ignored) {}
            submission.setUpdatedAt(LocalDateTime.now());

            submissionMapper.updateById(submission);
            System.out.println(">>> [Queue] 提交 #" + submissionId + " 评测完成，最终状态: " + judgeResult.getVerdict());

        } catch (Exception e) {
            submission.setVerdict("System Error");
            try {
                submission.setJudgeDetails(objectMapper.writeValueAsString("系统内部错误" + e.getMessage()));
            } catch (Exception ignored) {}
            submission.setUpdatedAt(LocalDateTime.now());
            submissionMapper.updateById(submission);
            e.printStackTrace();
        }
    }

    @PreDestroy
    public void destroy() {
        running = false;
        judgeWorkerPool.shutdown();
    }
}