#include "compiler.hpp"
#include <unistd.h>
#include <sys/wait.h>
#include <sys/resource.h>
#include <fcntl.h>
#include <cstdlib>
#include <cstdint>
#include <vector>
#include <array>
#include <iostream>

CompileResult Compiler::build(const CompileConfig& config) {
    // 1. 准备管道 (Pipe)，用于将编译器的 stderr/stdout 流式读回父进程
    int pipe_fd[2];
    if (pipe(pipe_fd) < 0) {
        return {false, "System Error: Failed to create pipe for compiler."};
    }

    pid_t pid = fork();
    if (pid < 0) {
        close(pipe_fd[0]);
        close(pipe_fd[1]);
        return {false, "System Error: Failed to fork compiler process."};
    }

    if (pid == 0) {
        // ================= 子进程：执行编译器命令 =================
        close(pipe_fd[0]);

        dup2(pipe_fd[1], STDOUT_FILENO);
        dup2(pipe_fd[1], STDERR_FILENO);
        close(pipe_fd[1]);

        // 【安全加固 1】彻底清空继承自父容器的敏感环境变量 (防止 #include "/proc/self/environ" 泄露数据库密码!)
        clearenv();
        setenv("PATH", "/usr/bin:/bin", 1);
        setenv("TMPDIR", "/tmp", 1);

        // 【安全加固 2】限制编译 CPU 时间 (防止模板无限递归卡死)
        struct rlimit rl_cpu;
        rl_cpu.rlim_cur = config.time_limit_sec;
        rl_cpu.rlim_max = config.time_limit_sec + 1;
        setrlimit(RLIMIT_CPU, &rl_cpu);

        // 【安全加固 3】限制编译阶段虚拟内存 (1.5GB，足够 -O2 编译 bits/stdc++.h，同时防内存炸弹)
        struct rlimit rl_as;
        rl_as.rlim_cur = 1536ULL * 1024 * 1024;
        rl_as.rlim_max = rl_as.rlim_cur;
        setrlimit(RLIMIT_AS, &rl_as);

        // 【安全加固 4】限制编译产物文件大小 (32MB，防止 int a[100000000]={1} 写爆磁盘)
        struct rlimit rl_fsize;
        rl_fsize.rlim_cur = 32ULL * 1024 * 1024;
        rl_fsize.rlim_max = rl_fsize.rlim_cur;
        setrlimit(RLIMIT_FSIZE, &rl_fsize);

        // 构造编译参数 (增加 -fmax-errors=5 防止模板报错炸弹)
        std::vector<std::string> args_str;
        if (config.lang == "cpp") {
            args_str = {
                "g++", "-O2", "-std=c++17", "-fmax-errors=5", "-Wno-unused-result", "-DONLINE_JUDGE",
                config.src_path, "-o", config.dest_exe_path, "-lm"
            };
        } else if (config.lang == "c") {
            args_str = {
                "gcc", "-O2", "-std=c11", "-fmax-errors=5", "-Wno-unused-result", "-DONLINE_JUDGE",
                config.src_path, "-o", config.dest_exe_path, "-lm"
            };
        } else {
            std::cerr << "Unsupported language: " << config.lang << "\n";
            _exit(1);
        }

        std::vector<char*> args;
        for (const auto& s : args_str) {
            args.push_back(const_cast<char*>(s.c_str()));
        }
        args.push_back(nullptr);

        execvp(args[0], args.data());
        std::cerr << "Failed to execute compiler binary.\n";
        _exit(1);
    } else {
        // ================= 父进程：流式读取并限长截断编译日志 =================
        close(pipe_fd[1]);

        constexpr size_t MAX_LOG_BYTES = 16384; // 最多保留 16KB 编译日志，防 OOM
        std::string compile_log;
        bool truncated = false;
        std::array<char, 1024> buffer;
        ssize_t bytes_read;
        while ((bytes_read = read(pipe_fd[0], buffer.data(), buffer.size())) > 0) {
            if (compile_log.size() < MAX_LOG_BYTES) {
                size_t to_copy = std::min(static_cast<size_t>(bytes_read), MAX_LOG_BYTES - compile_log.size());
                compile_log.append(buffer.data(), to_copy);
                if (to_copy < static_cast<size_t>(bytes_read)) {
                    truncated = true;
                }
            } else {
                truncated = true; // 继续排空管道，避免编译器阻塞，但不再占用内存
            }
        }
        close(pipe_fd[0]);
        if (truncated) {
            compile_log.append("\n... (compile log truncated at 16KB)");
        }

        int status;
        waitpid(pid, &status, 0);

        if (WIFEXITED(status)) {
            int exit_code = WEXITSTATUS(status);
            if (exit_code == 0) {
                return {true, compile_log};
            } else {
                return {false, compile_log};
            }
        } else if (WIFSIGNALED(status)) {
            return {false, "Compiler killed by signal (possibly compile timeout or memory/size limit exceeded)."};
        }

        return {false, "Unknown compiler error."};
    }
}