#pragma once

#include "common.hpp"

class JudgeEngine {
public:
    // 执行常规题目评测模式 (--mode judge)
    static int execute(const CLIConfig& config);

private:
    static void print_json_report(const JudgeReport& report);
    static void print_pretty_report(const JudgeReport& report);
};