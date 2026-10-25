package com.oj.backend.common;

/**
 * 评测状态码与判题系统全局常量
 */
public final class JudgeConstants {

    private JudgeConstants() {}

    // ==================== 评测生命周期状态 ====================
    /** 排队中 / 等待评测 */
    public static final String PENDING = "PENDING";
    /** 正在执行评测中 */
    public static final String RUNNING = "RUNNING";

    // ==================== 终态评测结果 (Verdict) ====================
    /** 答案正确 (通过全部测试点) */
    public static final String ACCEPTED = "Accepted";
    public static final String AC = "AC";

    /** 答案错误 */
    public static final String WRONG_ANSWER = "Wrong Answer";
    public static final String WA = "WA";

    /** 时间超限 */
    public static final String TIME_LIMIT_EXCEEDED = "Time Limit Exceeded";
    public static final String TLE = "TLE";

    /** 内存超限 */
    public static final String MEMORY_LIMIT_EXCEEDED = "Memory Limit Exceeded";
    public static final String MLE = "MLE";

    /** 运行错误 (段错误、除以零等) */
    public static final String RUNTIME_ERROR = "Runtime Error";
    public static final String RE = "RE";

    /** 编译错误 (不计入罚时) */
    public static final String COMPILE_ERROR = "Compile Error";
    public static final String CE = "CE";

    /** 评测系统内部错误 (如沙箱崩溃、题目数据缺失) */
    public static final String SYSTEM_ERROR = "System Error";
    public static final String SE = "SE";

    // ==================== 比赛规则常量 ====================
    /** ICPC 赛制每道题首次 AC 前的单次非 CE 错误罚时 (分钟) */
    public static final long ICPC_PENALTY_MINUTES_PER_WRONG = 20L;
}