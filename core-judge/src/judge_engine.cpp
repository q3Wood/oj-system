#include "judge_engine.hpp"
#include "checker.hpp"
#include "compiler.hpp"
#include "runner.hpp"
#include "utils.hpp"

#include <algorithm>
#include <filesystem>
#include <iomanip>
#include <iostream>
#include <map>
#include <unistd.h>
#include <vector>

namespace fs = std::filesystem;

int JudgeEngine::execute(const CLIConfig& config) {
    if (config.prob_dir.empty() || config.src_path.empty()) {
        std::cerr << "Usage: core-judge --prob-dir <dir> --src <source_file> [--lang cpp] [--time 1000] [--mem 256] [--json]\n";
        return 1;
    }

    std::string run_id = std::to_string(getpid());
    fs::path workspace = fs::temp_directory_path() / ("core_judge_" + run_id);
    fs::create_directories(workspace);

    JudgeReport report;
    report.final_verdict = Verdict::AC;

    // 1. 编译阶段
    fs::path exe_path = workspace / "solution_bin";
    CompileConfig comp_cfg{config.src_path, exe_path.string(), config.lang, 10};

    if (!config.json_output) {
        std::cout << Color::BOLD << ">>> Compiling Source: " << config.src_path
                  << " (" << config.lang << ")..." << Color::RESET << "\n";
    }

    CompileResult comp_res = Compiler::build(comp_cfg);
    if (!comp_res.ok) {
        report.final_verdict = Verdict::CE;
        report.compile_log = comp_res.log;

        if (config.json_output) {
            std::cout << "{\"verdict\":\"Compile Error\",\"compile_log\":\""
                      << JudgeUtils::escape_json(comp_res.log) << "\",\"details\":[]}\n";
        } else {
            JudgeUtils::print_colored_verdict(Verdict::CE);
            std::cout << "\n--- Compiler stderr ---\n" << comp_res.log << "------------------------\n";
        }
        fs::remove_all(workspace);
        return 0;
    }

    if (!config.json_output) {
        std::cout << Color::GREEN << ">>> Compilation Succeeded!" << Color::RESET << "\n\n";
        std::cout << Color::BOLD << ">>> Evaluating Test Cases:" << Color::RESET << "\n";
    }

    // 2. 收集并配对测试用例 (.in 与 .ans/.out)
    std::map<std::string, std::pair<fs::path, fs::path>> raw_cases;
    for (const auto& entry : fs::directory_iterator(config.prob_dir)) {
        if (!entry.is_regular_file()) continue;
        std::string ext = entry.path().extension().string();
        std::string stem = entry.path().stem().string();

        if (ext == ".in") raw_cases[stem].first = entry.path();
        else if (ext == ".ans" || ext == ".out") raw_cases[stem].second = entry.path();
    }

    std::vector<std::pair<std::string, std::pair<fs::path, fs::path>>> sorted_cases(raw_cases.begin(), raw_cases.end());
    std::sort(sorted_cases.begin(), sorted_cases.end(), [](const auto& a, const auto& b) {
        return JudgeUtils::natural_case_compare(a.first, b.first);
    });

    // 3. 逐点沙箱执行与比对
    int case_id = 0;
    for (const auto& [name, files] : sorted_cases) {
        case_id++;
        if (files.first.empty() || files.second.empty()) continue;

        fs::path user_out = workspace / (name + ".user.out");
        SandboxConfig run_cfg;
        run_cfg.exe_path = exe_path.string();
        run_cfg.stdin_file = files.first.string();
        run_cfg.stdout_file = user_out.string();
        run_cfg.time_limit_ms = config.time_limit_ms;
        run_cfg.memory_limit_kb = config.memory_limit_mb * 1024;

        ExecutionResult exec_res = SandboxRunner::run(run_cfg);

        TestCaseResult case_result;
        case_result.id = case_id;
        case_result.time_ms = exec_res.cpu_time_ms;
        case_result.memory_kb = exec_res.memory_kb;
        case_result.exit_code = exec_res.exit_code;
        case_result.signal = exec_res.signal;

        report.max_time_ms = std::max(report.max_time_ms, exec_res.cpu_time_ms);
        report.peak_memory_kb = std::max(report.peak_memory_kb, exec_res.memory_kb);

        // 复用统一的状态判定逻辑
        case_result.verdict = JudgeUtils::evaluate_execution(exec_res, config.memory_limit_mb, case_result.message);
        if (case_result.verdict == Verdict::AC) {
            CheckStatus diff_status = Checker::strict_diff(user_out.string(), files.second.string());
            if (diff_status == CheckStatus::MATCH) {
                case_result.verdict = Verdict::AC;
                case_result.message = "Correct answer";
            } else {
                case_result.verdict = Verdict::WA;
                case_result.message = "Output mismatch";
            }
        }

        report.details.push_back(case_result);

        if (!config.json_output) {
            std::cout << "  Point #" << std::left << std::setw(3) << case_id << " [" << name << "]: ";
            JudgeUtils::print_colored_verdict(case_result.verdict);
            std::cout << " | Time: " << std::setw(4) << case_result.time_ms << "ms"
                      << " | Mem: " << std::setw(6) << case_result.memory_kb << "KB";
            if (case_result.verdict != Verdict::AC) {
                std::cout << " | Reason: " << Color::RED << case_result.message << Color::RESET;
            }
            std::cout << "\n";
        }

        if (case_result.verdict != Verdict::AC) {
            report.final_verdict = case_result.verdict;
            break;
        }
    }

    fs::remove_all(workspace);

    // 4. 输出最终汇总
    if (config.json_output) {
        print_json_report(report);
    } else {
        print_pretty_report(report);
    }
    return 0;
}

void JudgeEngine::print_json_report(const JudgeReport& report) {
    std::cout << "{"
              << "\"verdict\":\"" << verdict_to_string(report.final_verdict) << "\","
              << "\"max_time_ms\":" << report.max_time_ms << ","
              << "\"peak_memory_kb\":" << report.peak_memory_kb << ","
              << "\"details\":[";
    for (size_t i = 0; i < report.details.size(); ++i) {
        const auto& d = report.details[i];
        std::cout << "{"
                  << "\"id\":" << d.id << ","
                  << "\"verdict\":\"" << verdict_to_string(d.verdict) << "\","
                  << "\"time_ms\":" << d.time_ms << ","
                  << "\"memory_kb\":" << d.memory_kb << ","
                  << "\"exit_code\":" << d.exit_code << ","
                  << "\"signal\":" << d.signal << ","
                  << "\"message\":\"" << JudgeUtils::escape_json(d.message) << "\""
                  << "}" << (i + 1 < report.details.size() ? "," : "");
    }
    std::cout << "]}\n";
}

void JudgeEngine::print_pretty_report(const JudgeReport& report) {
    std::cout << "\n" << Color::BOLD << "================ Final Report ================" << Color::RESET << "\n";
    std::cout << "Final Verdict: ";
    JudgeUtils::print_colored_verdict(report.final_verdict);
    std::cout << "\nMax CPU Time : " << report.max_time_ms << " ms\n";
    std::cout << "Peak Memory  : " << report.peak_memory_kb << " KB (" << (report.peak_memory_kb / 1024.0) << " MB)\n";
    std::cout << Color::BOLD << "==============================================" << Color::RESET << "\n";
}