#pragma once

#include "common.hpp"
#include "runner.hpp"

#include <algorithm>
#include <cctype>
#include <csignal>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <string>

namespace Color {
    inline const std::string RESET   = "\033[0m";
    inline const std::string RED     = "\033[31m";
    inline const std::string GREEN   = "\033[32m";
    inline const std::string YELLOW  = "\033[33m";
    inline const std::string BLUE    = "\033[34m";
    inline const std::string MAGENTA = "\033[35m";
    inline const std::string CYAN    = "\033[36m";
    inline const std::string BOLD    = "\033[1m";
}

namespace JudgeUtils {

// 终端彩色打印状态码
inline void print_colored_verdict(Verdict v) {
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
inline std::string get_signal_description(int sig, bool is_wall_timeout) {
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

// 统一收口：将沙箱执行结果映射为 Verdict（常规评测与造题对拍共用）
inline Verdict evaluate_execution(const ExecutionResult& exec_res, int memory_limit_mb, std::string& out_msg) {
    out_msg = get_signal_description(exec_res.signal, exec_res.is_wall_timeout);
    if (exec_res.is_wall_timeout || exec_res.signal == SIGXCPU) {
        return Verdict::TLE;
    }
    if (exec_res.signal == SIGXFSZ) {
        return Verdict::OLE;
    }
    if (exec_res.signal != 0 || exec_res.exit_code != 0) {
        if (exec_res.signal == 0) {
            out_msg = "Non-zero exit code: " + std::to_string(exec_res.exit_code);
        }
        return Verdict::RE;
    }
    if (exec_res.memory_kb > memory_limit_mb * 1024) {
        out_msg = "Memory limit exceeded";
        return Verdict::MLE;
    }
    return Verdict::AC;
}

// JSON 字符串安全转义
inline std::string escape_json(const std::string& input) {
    std::string output;
    output.reserve(input.size());
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
                if (static_cast<unsigned char>(c) >= 32) {
                    output += c;
                }
                break;
        }
    }
    return output;
}

// 自然数优先排序 (1, 2 ... 10)
inline bool natural_case_compare(const std::string& a, const std::string& b) {
    auto is_all_digits = [](const std::string& s) {
        return !s.empty() && std::all_of(s.begin(), s.end(), [](unsigned char c) {
            return std::isdigit(c) != 0;
        });
    };
    if (is_all_digits(a) && is_all_digits(b)) {
        return std::stoll(a) < std::stoll(b);
    }
    return a < b;
}

// 安全截断读取文件前 max_bytes 字节，防止大测试点撑爆 LLM 上下文
inline std::string read_file_snippet(const std::filesystem::path& file_path, size_t max_bytes = 500) {
    std::ifstream ifs(file_path, std::ios::in | std::ios::binary);
    if (!ifs.is_open()) return "";
    std::string buf(max_bytes, '\0');
    ifs.read(&buf[0], static_cast<std::streamsize>(max_bytes));
    buf.resize(static_cast<size_t>(ifs.gcount()));
    if (ifs.peek() != EOF) {
        buf += "\n...[truncated]";
    }
    return buf;
}

} // namespace JudgeUtils