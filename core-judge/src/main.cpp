#include <iostream>
#include <fstream>
#include <vector>
#include <string>
#include <algorithm>
#include <filesystem>
#include <iomanip>
#include <map>
#include <unistd.h>
#include <csignal>
#include <sstream>

#include "common.hpp"
#include "compiler.hpp"
#include "runner.hpp"
#include "checker.hpp"

namespace fs = std::filesystem;

namespace Color {
    const std::string RESET   = "\033[0m";
    const std::string RED     = "\033[31m";
    const std::string GREEN   = "\033[32m";
    const std::string YELLOW  = "\033[33m";
    const std::string BLUE    = "\033[34m";
    const std::string MAGENTA = "\033[35m";
    const std::string CYAN    = "\033[36m";
    const std::string BOLD    = "\033[1m";
}

// 在 main.cpp 顶部补充头文件
#include <sstream>

struct CLIConfig {
    std::string mode = "judge"; // "judge" 或 "gen-verify"
    std::string prob_dir;
    std::string src_path;
    std::string gen_src;        // 【新增】生成器源码路径
    std::string std_src;        // 【新增】标程源码路径
    std::string brute_src;      // 【新增】暴力解源码路径
    std::string plan_path;      // 【新增】测试计划文件路径
    std::string lang = "cpp";
    int time_limit_ms = 1000;
    int memory_limit_mb = 256;
    bool json_output = false;
};

CLIConfig parse_arguments(int argc, char* argv[]) {
    CLIConfig config;
    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if (arg == "--mode" && i + 1 < argc) config.mode = argv[++i];
        else if (arg == "--prob-dir" && i + 1 < argc) config.prob_dir = argv[++i];
        else if (arg == "--src" && i + 1 < argc) config.src_path = argv[++i];
        else if (arg == "--gen-src" && i + 1 < argc) config.gen_src = argv[++i];
        else if (arg == "--std-src" && i + 1 < argc) config.std_src = argv[++i];
        else if (arg == "--brute-src" && i + 1 < argc) config.brute_src = argv[++i];
        else if (arg == "--plan" && i + 1 < argc) config.plan_path = argv[++i];
        else if (arg == "--lang" && i + 1 < argc) config.lang = argv[++i];
        else if (arg == "--time" && i + 1 < argc) config.time_limit_ms = std::stoi(argv[++i]);
        else if (arg == "--mem" && i + 1 < argc) config.memory_limit_mb = std::stoi(argv[++i]);
        else if (arg == "--json") config.json_output = true;
    }
    return config;
}

// 【新增】读取文件前 max_bytes 字节作为反例切片，防止撑爆大模型上下文
std::string read_file_snippet(const fs::path& file_path, size_t max_bytes = 500) {
    std::ifstream ifs(file_path, std::ios::in | std::ios::binary);
    if (!ifs.is_open()) return "";
    std::string buf(max_bytes, '\0');
    ifs.read(&buf[0], static_cast<std::streamsize>(max_bytes));
    buf.resize(static_cast<size_t>(ifs.gcount()));
    if (ifs.peek() != EOF) buf += "\n...[truncated]";
    return buf;
}

// 【新增】通用执行状态判定辅助函数
Verdict judge_execution_status(const ExecutionResult& exec_res, int memory_limit_mb, std::string& out_msg) {
    out_msg = get_signal_description(exec_res.signal, exec_res.is_wall_timeout);
    if (exec_res.is_wall_timeout || exec_res.signal == SIGXCPU) return Verdict::TLE;
    if (exec_res.signal == SIGXFSZ) return Verdict::OLE;
    if (exec_res.signal != 0 || exec_res.exit_code != 0) {
        if (exec_res.signal == 0) out_msg = "Non-zero exit code: " + std::to_string(exec_res.exit_code);
        return Verdict::RE;
    }
    if (exec_res.memory_kb > memory_limit_mb * 1024) {
        out_msg = "Memory limit exceeded";
        return Verdict::MLE;
    }
    return Verdict::AC;
}

// 【新增】造题对拍验题主逻辑
int run_gen_verify_mode(const CLIConfig& config) {
    if (config.prob_dir.empty() || config.gen_src.empty() || config.std_src.empty() ||
        config.brute_src.empty() || config.plan_path.empty()) {
        std::cerr << "Missing required args for --mode gen-verify\n";
        return 1;
    }

    std::string run_id = std::to_string(getpid());
    fs::path workspace = fs::temp_directory_path() / ("core_gen_" + run_id);
    fs::create_directories(workspace);
    fs::create_directories(config.prob_dir);

    auto print_result_and_clean = [&](bool passed, const std::string& stage, const std::string& target,
                                      int case_id, Verdict v, const std::string& msg,
                                      const std::string& comp_log, const std::string& in_snip,
                                      const std::string& std_snip, const std::string& brute_snip,
                                      int max_t, int peak_m, bool brute_blocked) {
        fs::remove_all(workspace);
        std::cout << "{"
                  << "\"passed\":" << (passed ? "true" : "false") << ","
                  << "\"stage\":\"" << stage << "\","
                  << "\"failed_target\":" << (target.empty() ? "null" : ("\"" + target + "\"")) << ","
                  << "\"failed_case_id\":" << (case_id <= 0 ? "null" : std::to_string(case_id)) << ","
                  << "\"verdict\":\"" << verdict_to_string(v) << "\","
                  << "\"message\":\"" << escape_json(msg) << "\","
                  << "\"compile_log\":" << (comp_log.empty() ? "null" : ("\"" + escape_json(comp_log) + "\"")) << ","
                  << "\"input_snippet\":" << (in_snip.empty() ? "null" : ("\"" + escape_json(in_snip) + "\"")) << ","
                  << "\"std_output_snippet\":" << (std_snip.empty() ? "null" : ("\"" + escape_json(std_snip) + "\"")) << ","
                  << "\"brute_output_snippet\":" << (brute_snip.empty() ? "null" : ("\"" + escape_json(brute_snip) + "\"")) << ","
                  << "\"max_time_ms\":" << max_t << ","
                  << "\"peak_memory_kb\":" << peak_m << ","
                  << "\"brute_blocked\":" << (brute_blocked ? "true" : "false")
                  << "}\n";
        return 0;
    };

    // 1. 编译三件套 (gen, std, brute)
    fs::path gen_bin = workspace / "gen_bin";
    fs::path std_bin = workspace / "std_bin";
    fs::path brute_bin = workspace / "brute_bin";

    struct CompileItem { std::string name; std::string src; fs::path bin; };
    std::vector<CompileItem> items = {
        {"gen", config.gen_src, gen_bin},
        {"std", config.std_src, std_bin},
        {"brute", config.brute_src, brute_bin}
    };

    for (const auto& item : items) {
        CompileResult res = Compiler::build({item.src, item.bin.string(), "cpp", 10});
        if (!res.ok) {
            return print_result_and_clean(false, "COMPILE", item.name, 0, Verdict::CE,
                                          "Compilation failed for " + item.name + ".cpp",
                                          res.log, "", "", "", 0, 0, true);
        }
    }

    // 2. 解析 plan.txt 并逐点造数与对拍
    std::ifstream plan_ifs(config.plan_path);
    std::string line;
    int max_time_ms = 0;
    int peak_memory_kb = 0;
    bool brute_blocked = true;

    while (std::getline(plan_ifs, line)) {
        if (line.empty() || line[0] == '#') continue;
        std::istringstream iss(line);
        int case_id = 0, check_brute = 0, expect_brute_fail = 0;
        if (!(iss >> case_id >> check_brute >> expect_brute_fail)) continue;

        std::vector<std::string> gen_args;
        std::string token;
        while (iss >> token) gen_args.push_back(token);

        fs::path in_file = fs::path(config.prob_dir) / (std::to_string(case_id) + ".in");
        fs::path out_file = fs::path(config.prob_dir) / (std::to_string(case_id) + ".out");
        fs::path brute_out_file = workspace / (std::to_string(case_id) + ".brute.out");

        // 2.1 沙箱运行生成器 gen_bin -> {case_id}.in
        SandboxConfig gen_cfg;
        gen_cfg.exe_path = gen_bin.string();
        gen_cfg.args = gen_args;
        gen_cfg.stdout_file = in_file.string();
        gen_cfg.time_limit_ms = 2000;
        gen_cfg.memory_limit_kb = 512 * 1024;

        ExecutionResult gen_exec = SandboxRunner::run(gen_cfg);
        std::string gen_msg;
        Verdict gen_v = judge_execution_status(gen_exec, 512, gen_msg);
        if (gen_v != Verdict::AC) {
            return print_result_and_clean(false, "GEN_DATA", "gen", case_id, gen_v,
                                          "Generator failed on case #" + std::to_string(case_id) + ": " + gen_msg,
                                          "", "", "", "", max_time_ms, peak_memory_kb, brute_blocked);
        }

        // 2.2 沙箱运行标程 std_bin: {case_id}.in -> {case_id}.out
        SandboxConfig std_cfg;
        std_cfg.exe_path = std_bin.string();
        std_cfg.stdin_file = in_file.string();
        std_cfg.stdout_file = out_file.string();
        std_cfg.time_limit_ms = config.time_limit_ms;
        std_cfg.memory_limit_kb = config.memory_limit_mb * 1024;

        ExecutionResult std_exec = SandboxRunner::run(std_cfg);
        max_time_ms = std::max(max_time_ms, std_exec.cpu_time_ms);
        peak_memory_kb = std::max(peak_memory_kb, std_exec.memory_kb);

        std::string std_msg;
        Verdict std_v = judge_execution_status(std_exec, config.memory_limit_mb, std_msg);
        if (std_v != Verdict::AC) {
            return print_result_and_clean(false, "RUN_STD", "std", case_id, std_v,
                                          "Standard solution failed on case #" + std::to_string(case_id) + ": " + std_msg,
                                          "", read_file_snippet(in_file), read_file_snippet(out_file), "",
                                          max_time_ms, peak_memory_kb, brute_blocked);
        }

        // 2.3 沙箱运行暴力解 brute_bin (对拍校验或极限防暴力校验)
        if (check_brute == 1) {
            SandboxConfig brute_cfg;
            brute_cfg.exe_path = brute_bin.string();
            brute_cfg.stdin_file = in_file.string();
            brute_cfg.stdout_file = brute_out_file.string();
            brute_cfg.time_limit_ms = config.time_limit_ms;
            brute_cfg.memory_limit_kb = config.memory_limit_mb * 1024;

            ExecutionResult brute_exec = SandboxRunner::run(brute_cfg);
            std::string brute_msg;
            Verdict brute_v = judge_execution_status(brute_exec, config.memory_limit_mb, brute_msg);

            if (expect_brute_fail == 0) {
                // 小数据对拍模式：要求暴力解必须正常跑完且与标程输出 100% 一致
                if (brute_v != Verdict::AC) {
                    return print_result_and_clean(false, "STRESS_DIFF", "brute", case_id, brute_v,
                                                  "Brute-force solution crashed/TLE on small case #" + std::to_string(case_id) + ": " + brute_msg,
                                                  "", read_file_snippet(in_file), read_file_snippet(out_file), "",
                                                  max_time_ms, peak_memory_kb, brute_blocked);
                }
                if (Checker::strict_diff(out_file.string(), brute_out_file.string()) != CheckStatus::MATCH) {
                    return print_result_and_clean(false, "STRESS_DIFF", "std", case_id, Verdict::WA,
                                                  "Output mismatch between std and brute on case #" + std::to_string(case_id),
                                                  "", read_file_snippet(in_file), read_file_snippet(out_file),
                                                  read_file_snippet(brute_out_file), max_time_ms, peak_memory_kb, brute_blocked);
                }
            } else {
                // 极限数据防暴力模式：若暴力解居然没超时且答案还对，说明数据没卡住暴力
                if (brute_v == Verdict::AC) {
                    if (Checker::strict_diff(out_file.string(), brute_out_file.string()) != CheckStatus::MATCH) {
                        return print_result_and_clean(false, "STRESS_DIFF", "std", case_id, Verdict::WA,
                                                      "Output mismatch between std and brute on stress case #" + std::to_string(case_id),
                                                      "", read_file_snippet(in_file), read_file_snippet(out_file),
                                                      read_file_snippet(brute_out_file), max_time_ms, peak_memory_kb, brute_blocked);
                    }
                    brute_blocked = false; // 告警：暴力解在极限数据下全身而退
                }
            }
        }
    }

    return print_result_and_clean(true, "DONE", "", 0, Verdict::AC,
                                  "All test cases generated and verified successfully.",
                                  "", "", "", "", max_time_ms, peak_memory_kb, brute_blocked);
}

void print_colored_verdict(Verdict v) {
    switch (v) {
        case Verdict::AC:  std::cout << Color::GREEN << "[Accepted] " << Color::RESET; break;
        case Verdict::WA:  std::cout << Color::RED << "[Wrong Answer] " << Color::RESET; break;
        case Verdict::TLE: std::cout << Color::YELLOW << "[Time Limit Exceeded] " << Color::RESET; break;
        case Verdict::MLE: std::cout << Color::MAGENTA << "[Memory Limit Exceeded] " << Color::RESET; break;
        case Verdict::OLE: std::cout << Color::YELLOW << "[Output Limit Exceeded] " << Color::RESET; break;
        case Verdict::RE:  std::cout << Color::RED << "[Runtime Error] " << Color::RESET; break;
        case Verdict::CE:  std::cout << Color::CYAN << "[Compile Error] " << Color::RESET; break;
        default:           std::cout << Color::RED << "[System Error] " << Color::RESET; break;
    }
}

// 信号转具体可读原因
std::string get_signal_description(int sig, bool is_wall_timeout) {
    if (is_wall_timeout) return "Terminated by Watchdog (Wall Clock Timeout)";
    switch (sig) {
        case SIGSEGV: return "Segmentation fault (Invalid memory access / Null pointer)";
        case SIGFPE:  return "Floating point exception (Division by zero)";
        case SIGXCPU: return "CPU time limit exceeded";
        case SIGXFSZ: return "File size limit exceeded (Output bomb)";
        case SIGABRT: return "Aborted (Assertion failed or std::terminate)";
        case SIGKILL: return "Process killed (SIGKILL)";
        default:      return sig == 0 ? "Normal exit" : ("Killed by signal " + std::to_string(sig));
    }
}

// 辅助函数：将字符串中的特殊字符转义为合法 JSON 字符串
std::string escape_json(const std::string& input) {
    std::string output;
    for (char c : input) {
        switch (c) {
            case '"':  output += "\\\""; break;
            case '\\': output += "\\\\"; break;
            case '\b': output += "\\b"; break;
            case '\f': output += "\\f"; break;
            case '\n': output += "\\n"; break;
            case '\r': output += "\\r"; break;
            case '\t': output += "\\t"; break;
            default:
                if (static_cast<unsigned char>(c) < 32) {
                    // 忽略其他不可见的控制字符
                } else {
                    output += c;
                }
        }
    }
    return output;
}

// 辅助排序：优先按纯数字排序 (1, 2 ... 10)，非纯数字按字母序
bool natural_case_compare(const std::string& a, const std::string& b) {
    bool a_is_num = !a.empty() && std::all_of(a.begin(), a.end(), ::isdigit);
    bool b_is_num = !b.empty() && std::all_of(b.begin(), b.end(), ::isdigit);
    if (a_is_num && b_is_num) {
        return std::stoll(a) < std::stoll(b);
    }
    return a < b;
}

int main(int argc, char* argv[]) {
    CLIConfig config = parse_arguments(argc, argv);

    if (config.mode == "gen-verify") {
        return run_gen_verify_mode(config);
    }

    if (config.prob_dir.empty() || config.src_path.empty()) {
        std::cerr << "Usage: " << argv[0] << " --prob-dir <dir> --src <source_file> [--lang cpp] [--time 1000] [--mem 256] [--json]\n";
        return 1;
    }

    std::string run_id = std::to_string(getpid());
    fs::path workspace = fs::temp_directory_path() / ("core_judge_" + run_id);
    fs::create_directories(workspace);

    JudgeReport report;
    report.final_verdict = Verdict::AC;
    report.max_time_ms = 0;
    report.peak_memory_kb = 0;

    // 1. 编译阶段
    fs::path exe_path = workspace / "solution_bin";
    CompileConfig comp_cfg{config.src_path, exe_path.string(), config.lang, 10};

    if (!config.json_output) {
        std::cout << Color::BOLD << ">>> Compiling Source: " << config.src_path << " (" << config.lang << ")..." << Color::RESET << "\n";
    }

    CompileResult comp_res = Compiler::build(comp_cfg);
    if (!comp_res.ok) {
        report.final_verdict = Verdict::CE;
        report.compile_log = comp_res.log;

        if (config.json_output) {
            std::cout << "{\"verdict\":\"Compile Error\",\"compile_log\":\"" << escape_json(comp_res.log) << "\",\"details\":[]}\n";
        } else {
            print_colored_verdict(Verdict::CE);
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

    // 转为按自然数排序的列表
    std::vector<std::pair<std::string, std::pair<fs::path, fs::path>>> sorted_cases(raw_cases.begin(), raw_cases.end());
    std::sort(sorted_cases.begin(), sorted_cases.end(), [](const auto& a, const auto& b) {
        return natural_case_compare(a.first, b.first);
    });

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
        case_result.message = get_signal_description(exec_res.signal, exec_res.is_wall_timeout);

        report.max_time_ms = std::max(report.max_time_ms, exec_res.cpu_time_ms);
        report.peak_memory_kb = std::max(report.peak_memory_kb, exec_res.memory_kb);

        // 状态判定
        if (exec_res.is_wall_timeout || exec_res.signal == SIGXCPU) {
            case_result.verdict = Verdict::TLE;
        } else if (exec_res.signal == SIGXFSZ) {
            case_result.verdict = Verdict::OLE;
        } else if (exec_res.signal != 0 || exec_res.exit_code != 0) {
            case_result.verdict = Verdict::RE;
            if (exec_res.signal == 0) case_result.message = "Non-zero exit code: " + std::to_string(exec_res.exit_code);
        } else if (exec_res.memory_kb > config.memory_limit_mb * 1024) {
            case_result.verdict = Verdict::MLE;
            case_result.message = "Memory limit exceeded";
        } else {
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

        // 终端可视化展示
        if (!config.json_output) {
            std::cout << "  Point #" << std::left << std::setw(3) << case_id << " [" << name << "]: ";
            print_colored_verdict(case_result.verdict);
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

    // 3. 最终汇总输出
    if (config.json_output) {
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
                      << "\"message\":\"" << d.message << "\""
                      << "}" << (i + 1 < report.details.size() ? "," : "");
        }
        std::cout << "]}\n";
    } else {
        std::cout << "\n" << Color::BOLD << "================ Final Report ================" << Color::RESET << "\n";
        std::cout << "Final Verdict: ";
        print_colored_verdict(report.final_verdict);
        std::cout << "\nMax CPU Time : " << report.max_time_ms << " ms\n";
        std::cout << "Peak Memory  : " << report.peak_memory_kb << " KB (" << (report.peak_memory_kb / 1024.0) << " MB)\n";
        std::cout << Color::BOLD << "==============================================" << Color::RESET << "\n";
    }
    
    return 0;
}