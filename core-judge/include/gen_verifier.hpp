#pragma once

#include "common.hpp"

#include <filesystem>
#include <string>
#include <vector>

class GenVerifier {
public:
    // 执行 AI 造题与沙箱对拍验题模式 (--mode gen-verify)
    static int execute(const CLIConfig& config);

private:
    static std::vector<PlanItem> parse_plan_file(const std::string& plan_path);
    static void print_json_report(const GenVerifyReport& report);
};