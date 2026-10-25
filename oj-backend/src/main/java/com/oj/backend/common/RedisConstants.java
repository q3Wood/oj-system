package com.oj.backend.common;

/**
 * 全局 Redis Key 前缀与过期时间常量池
 */
public final class RedisConstants {

    private RedisConstants() {}

    // ==================== 1. 用户认证模块 ====================
    /** 登录 Token 缓存前缀 -> login:token:{userId} */
    public static final String LOGIN_TOKEN_PREFIX = "login:token:";
    public static final long LOGIN_TOKEN_TTL_DAYS = 7L;

    // ==================== 2. 题目模块 ====================
    /** 题目 ID 自增发号器 Key */
    public static final String PROBLEM_ID_SEQ = "oj:problem:id:seq";

    // ==================== 3. 评测与限流模块 ====================
    /** 判题异步任务消息队列 List Key */
    public static final String JUDGE_QUEUE_KEY = "oj:judge:queue";

    /** 用户提交代码频率限流前缀 -> limit:submit:user:{userId} */
    public static final String RATE_LIMIT_SUBMIT_PREFIX = "limit:submit:user:";
    /** 提交代码冷却时间 (秒) */
    public static final long RATE_LIMIT_SUBMIT_SECONDS = 5L;

    // ==================== 4. 线程命名 ====================
    /** 判题队列监听守护线程名称 */
    public static final String JUDGE_QUEUE_LISTENER_THREAD_NAME = "judge-queue-listener";

    /** 题解点赞用户集合前缀 -> oj:solution:like:{solutionId} */
    public static final String SOLUTION_LIKE_PREFIX = "oj:solution:like:";
}