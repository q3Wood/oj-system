#pragma once

#include <string>
#include <vector>

// 评测最终状态码
enum class Verdict {
    AC,   // Accepted 通过
    WA,   // Wrong Answer 答案错误
    TLE,  // Time Limit Exceeded 超时
    MLE,  // Memory Limit Exceeded 超内存
    OLE,  // Output Limit Exceeded 输出超限
    RE,   // Runtime Error 运行时错误 (段错误/浮点错误等)
    CE,   // Compile Error 编译错误
    SE    // System Error 评测系统内部错误
};

// 工具函数：把枚举转为字符串方便输出展示
inline std::string verdict_to_string(Verdict v) {
    switch (v) {
        case Verdict::AC:  return "Accepted";
        case Verdict::WA:  return "Wrong Answer";
        case Verdict::TLE: return "Time Limit Exceeded";
        case Verdict::MLE: return "Memory Limit Exceeded";
        case Verdict::OLE: return "Output Limit Exceeded";
        case Verdict::RE:  return "Runtime Error";
        case Verdict::CE:  return "Compile Error";
        case Verdict::SE:  return "System Error";
        default:           return "Unknown";
    }
}

// 命令行解析后的全局配置（供各模式引擎共享）
struct CLIConfig {
    std::string mode = "judge";   // "judge" | "gen-verify"
    std::string prob_dir;
    std::string src_path;         // judge 模式使用
    std::string gen_src;          // gen-verify 模式使用
    std::string std_src;          // gen-verify 模式使用
    std::string brute_src;        // gen-verify 模式使用
    std::string plan_path;        // gen-verify 模式使用
    std::string lang = "cpp";
    int time_limit_ms = 1000;
    int memory_limit_mb = 256;
    bool json_output = false;
};

// 单个测试点的运行结果
struct TestCaseResult {
    int id = 0;               // 测试点编号 (1, 2, 3...)
    Verdict verdict = Verdict::SE;
    int time_ms = 0;          // 消耗的 CPU 时间 (毫秒)
    int memory_kb = 0;        // 消耗的常驻物理内存 (KiB)
    int exit_code = 0;        // 退出码
    int signal = 0;           // 终止信号 (如 11 代表 SIGSEGV 段错误)
    std::string message;      // 补充信息
};

// 常规评测任务的最终总报告
struct JudgeReport {
    Verdict final_verdict = Verdict::AC;
    int max_time_ms = 0;
    int peak_memory_kb = 0;
    std::string compile_log;
    std::vector<TestCaseResult> details;
};

// 造题对拍模式：单条测试点生成计划
struct PlanItem {
    int case_id = 0;
    bool check_brute = true;
    bool expect_brute_fail = false;
    std::vector<std::string> gen_args;
};

// 造题对拍模式：最终验题报告
struct GenVerifyReport {
    bool passed = false;
    std::string stage = "INIT";      // COMPILE | GEN_DATA | RUN_STD | STRESS_DIFF | DONE
    std::string failed_target;       // gen | std | brute (通过时为空)
    int failed_case_id = 0;          // 首个失败测试点 (通过或编译失败时为 0)
    Verdict verdict = Verdict::AC;
    std::string message;
    std::string compile_log;
    std::string input_snippet;
    std::string std_output_snippet;
    std::string brute_output_snippet;
    int max_time_ms = 0;
    int peak_memory_kb = 0;
    bool brute_blocked = true;
};