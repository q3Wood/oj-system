#include "gen_verifier.hpp"
#include "checker.hpp"
#include "compiler.hpp"
#include "runner.hpp"
#include "utils.hpp"

#include <algorithm>
#include <fstream>
#include <iostream>
#include <sstream>
#include <unistd.h>

namespace fs = std::filesystem;

std::vector<PlanItem> GenVerifier::parse_plan_file(const std::string& plan_path) {
    std::vector<PlanItem> items;
    std::ifstream ifs(plan_path);
    std::string line;

    while (std::getline(ifs, line)) {
        if (line.empty() || line[0] == '#') continue;
        std::istringstream iss(line);

        PlanItem item;
        int check_brute_flag = 0;
        int expect_fail_flag = 0;
        if (!(iss >> item.case_id >> check_brute_flag >> expect_fail_flag)) {
            continue;
        }
        item.check_brute = (check_brute_flag == 1);
        item.expect_brute_fail = (expect_fail_flag == 1);

        std::string token;
        while (iss >> token) {
            item.gen_args.push_back(token);
        }
        items.push_back(item);
    }
    return items;
}

void GenVerifier::print_json_report(const GenVerifyReport& r) {
    auto json_str_or_null = [](const std::string& s) -> std::string {
        return s.empty() ? "null" : ("\"" + JudgeUtils::escape_json(s) + "\"");
    };

    std::cout << "{"
              << "\"passed\":" << (r.passed ? "true" : "false") << ","
              << "\"stage\":\"" << r.stage << "\","
              << "\"failed_target\":" << json_str_or_null(r.failed_target) << ","
              << "\"failed_case_id\":" << (r.failed_case_id <= 0 ? "null" : std::to_string(r.failed_case_id)) << ","
              << "\"verdict\":\"" << verdict_to_string(r.verdict) << "\","
              << "\"message\":\"" << JudgeUtils::escape_json(r.message) << "\","
              << "\"compile_log\":" << json_str_or_null(r.compile_log) << ","
              << "\"input_snippet\":" << json_str_or_null(r.input_snippet) << ","
              << "\"std_output_snippet\":" << json_str_or_null(r.std_output_snippet) << ","
              << "\"brute_output_snippet\":" << json_str_or_null(r.brute_output_snippet) << ","
              << "\"max_time_ms\":" << r.max_time_ms << ","
              << "\"peak_memory_kb\":" << r.peak_memory_kb << ","
              << "\"brute_blocked\":" << (r.brute_blocked ? "true" : "false")
              << "}\n";
}

int GenVerifier::execute(const CLIConfig& config) {
    if (config.prob_dir.empty() || config.gen_src.empty() || config.std_src.empty() ||
        config.brute_src.empty() || config.plan_path.empty()) {
        std::cerr << "Missing required args for --mode gen-verify\n";
        return 1;
    }

    std::string run_id = std::to_string(getpid());
    fs::path workspace = fs::temp_directory_path() / ("core_gen_" + run_id);
    fs::create_directories(workspace);
    fs::create_directories(config.prob_dir);

    GenVerifyReport report;
    auto finish = [&]() -> int {
        fs::remove_all(workspace);
        print_json_report(report);
        return 0;
    };

    // 1. 编译三件套 (gen.cpp, std.cpp, brute.cpp)
    fs::path gen_bin = workspace / "gen_bin";
    fs::path std_bin = workspace / "std_bin";
    fs::path brute_bin = workspace / "brute_bin";

    struct CompileTarget {
        std::string name;
        std::string src;
        fs::path bin;
    };
    std::vector<CompileTarget> targets = {
        {"gen", config.gen_src, gen_bin},
        {"std", config.std_src, std_bin},
        {"brute", config.brute_src, brute_bin}
    };

    for (const auto& t : targets) {
        CompileResult res = Compiler::build({t.src, t.bin.string(), "cpp", 10});
        if (!res.ok) {
            report.passed = false;
            report.stage = "COMPILE";
            report.failed_target = t.name;
            report.verdict = Verdict::CE;
            report.message = "Compilation failed for " + t.name + ".cpp";
            report.compile_log = res.log;
            return finish();
        }
    }

    // 2. 解析测试计划文件
    std::vector<PlanItem> plan = parse_plan_file(config.plan_path);
    if (plan.empty()) {
        report.passed = false;
        report.stage = "GEN_DATA";
        report.verdict = Verdict::SE;
        report.message = "Test plan is empty or unreadable.";
        return finish();
    }

    // 3. 逐点沙箱造数、求解与对拍
    for (const auto& item : plan) {
        fs::path in_file = fs::path(config.prob_dir) / (std::to_string(item.case_id) + ".in");
        fs::path out_file = fs::path(config.prob_dir) / (std::to_string(item.case_id) + ".out");
        fs::path brute_out_file = workspace / (std::to_string(item.case_id) + ".brute.out");

        // 3.1 运行生成器 gen_bin -> {case_id}.in
        SandboxConfig gen_cfg;
        gen_cfg.exe_path = gen_bin.string();
        gen_cfg.args = item.gen_args;
        gen_cfg.stdout_file = in_file.string();
        gen_cfg.time_limit_ms = 2000;
        gen_cfg.memory_limit_kb = 512 * 1024;

        ExecutionResult gen_exec = SandboxRunner::run(gen_cfg);
        std::string gen_msg;
        Verdict gen_v = JudgeUtils::evaluate_execution(gen_exec, 512, gen_msg);
        if (gen_v != Verdict::AC) {
            report.passed = false;
            report.stage = "GEN_DATA";
            report.failed_target = "gen";
            report.failed_case_id = item.case_id;
            report.verdict = gen_v;
            report.message = "Generator failed on case #" + std::to_string(item.case_id) + ": " + gen_msg;
            return finish();
        }

        // 3.2 运行标程 std_bin: {case_id}.in -> {case_id}.out
        SandboxConfig std_cfg;
        std_cfg.exe_path = std_bin.string();
        std_cfg.stdin_file = in_file.string();
        std_cfg.stdout_file = out_file.string();
        std_cfg.time_limit_ms = config.time_limit_ms;
        std_cfg.memory_limit_kb = config.memory_limit_mb * 1024;

        ExecutionResult std_exec = SandboxRunner::run(std_cfg);
        report.max_time_ms = std::max(report.max_time_ms, std_exec.cpu_time_ms);
        report.peak_memory_kb = std::max(report.peak_memory_kb, std_exec.memory_kb);

        std::string std_msg;
        Verdict std_v = JudgeUtils::evaluate_execution(std_exec, config.memory_limit_mb, std_msg);
        if (std_v != Verdict::AC) {
            report.passed = false;
            report.stage = "RUN_STD";
            report.failed_target = "std";
            report.failed_case_id = item.case_id;
            report.verdict = std_v;
            report.message = "Standard solution failed on case #" + std::to_string(item.case_id) + ": " + std_msg;
            report.input_snippet = JudgeUtils::read_file_snippet(in_file);
            report.std_output_snippet = JudgeUtils::read_file_snippet(out_file);
            return finish();
        }

        // 3.3 运行暴力解 brute_bin (对拍校验或极限防暴力校验)
        if (item.check_brute) {
            SandboxConfig brute_cfg;
            brute_cfg.exe_path = brute_bin.string();
            brute_cfg.stdin_file = in_file.string();
            brute_cfg.stdout_file = brute_out_file.string();
            brute_cfg.time_limit_ms = config.time_limit_ms;
            brute_cfg.memory_limit_kb = config.memory_limit_mb * 1024;

            ExecutionResult brute_exec = SandboxRunner::run(brute_cfg);
            std::string brute_msg;
            Verdict brute_v = JudgeUtils::evaluate_execution(brute_exec, config.memory_limit_mb, brute_msg);

            if (!item.expect_brute_fail) {
                // 小数据对拍模式：暴力解必须正常跑完且与标程严格一致
                if (brute_v != Verdict::AC) {
                    report.passed = false;
                    report.stage = "STRESS_DIFF";
                    report.failed_target = "brute";
                    report.failed_case_id = item.case_id;
                    report.verdict = brute_v;
                    report.message = "Brute-force solution failed on small case #" + std::to_string(item.case_id) + ": " + brute_msg;
                    report.input_snippet = JudgeUtils::read_file_snippet(in_file);
                    report.std_output_snippet = JudgeUtils::read_file_snippet(out_file);
                    return finish();
                }
                if (Checker::strict_diff(out_file.string(), brute_out_file.string()) != CheckStatus::MATCH) {
                    report.passed = false;
                    report.stage = "STRESS_DIFF";
                    report.failed_target = "std";
                    report.failed_case_id = item.case_id;
                    report.verdict = Verdict::WA;
                    report.message = "Output mismatch between std and brute on case #" + std::to_string(item.case_id);
                    report.input_snippet = JudgeUtils::read_file_snippet(in_file);
                    report.std_output_snippet = JudgeUtils::read_file_snippet(out_file);
                    report.brute_output_snippet = JudgeUtils::read_file_snippet(brute_out_file);
                    return finish();
                }
            } else {
                // 极限数据防暴力模式：若暴力解没超时，检查答案是否一致并标记未拦住暴力
                if (brute_v == Verdict::AC) {
                    if (Checker::strict_diff(out_file.string(), brute_out_file.string()) != CheckStatus::MATCH) {
                        report.passed = false;
                        report.stage = "STRESS_DIFF";
                        report.failed_target = "std";
                        report.failed_case_id = item.case_id;
                        report.verdict = Verdict::WA;
                        report.message = "Output mismatch between std and brute on stress case #" + std::to_string(item.case_id);
                        report.input_snippet = JudgeUtils::read_file_snippet(in_file);
                        report.std_output_snippet = JudgeUtils::read_file_snippet(out_file);
                        report.brute_output_snippet = JudgeUtils::read_file_snippet(brute_out_file);
                        return finish();
                    }
                    report.brute_blocked = false;
                }
            }
        }
    }

    report.passed = true;
    report.stage = "DONE";
    report.verdict = Verdict::AC;
    report.message = "All " + std::to_string(plan.size()) + " test cases generated and verified successfully.";
    return finish();
}