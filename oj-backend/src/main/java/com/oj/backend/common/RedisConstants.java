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

    // ==================== 4. 线程命名与社区模块 ====================
    /** 判题队列监听守护线程名称 */
    public static final String JUDGE_QUEUE_LISTENER_THREAD_NAME = "judge-queue-listener";

    /** 题解点赞用户集合前缀 -> oj:solution:like:{solutionId} */
    public static final String SOLUTION_LIKE_PREFIX = "oj:solution:like:";

    // ==================== 5. AI 代码诊断模块 ====================
    /** 单次提交 AI 诊断防双击分布式锁前缀 -> lock:ai:analyze:{submissionId} */
    public static final String AI_ANALYZE_LOCK_PREFIX = "lock:ai:analyze:";
    public static final long AI_ANALYZE_LOCK_TTL_SECONDS = 60L;

    /** 普通用户每日 AI 诊断配额计数器前缀 -> limit:ai:daily:{yyyyMMdd}:{userId} */
    public static final String AI_DAILY_QUOTA_PREFIX = "limit:ai:daily:";
    public static final int AI_MAX_DAILY_QUOTA = 20;

    // ==================== 6. 系统更新日志与通用缓存治理 ====================
    /** 最新一条系统更新日志摘要缓存 Key */
    public static final String CHANGELOG_LATEST_KEY = "oj:changelog:latest";
    public static final long CHANGELOG_LATEST_TTL_HOURS = 24L;

    /** 防缓存穿透空值占位符 */
    public static final String CACHE_NULL_VALUE = "NONE";
    /** 空值占位符默认过期时间 (分钟) */
    public static final long CACHE_NULL_TTL_MINUTES = 30L;
    /** 热点缓存重建互斥锁前缀 -> lock:cache:rebuild:{key} */
    public static final String CACHE_MUTEX_LOCK_PREFIX = "lock:cache:rebuild:";
}