#include "runner.hpp"

#include <unistd.h>
#include <sys/wait.h>
#include <sys/resource.h>
#include <sys/prctl.h>
#include <grp.h>
#include <fcntl.h>
#include <signal.h>

#include <cstdint>
#include <vector>
#include <chrono>
#include <thread>
#include <atomic>
#include <condition_variable>
#include <mutex>
#include <algorithm>
#include <iostream>

ExecutionResult SandboxRunner::run(const SandboxConfig& config) {
    pid_t pid = fork();

    if (pid < 0) {
        return {-1, -1, -1, -1, false};
    }

    if (pid == 0) {
        // ================= 子进程：配置沙箱并运行 =================

        // 1.1 输入输出重定向
        if (!config.stdin_file.empty()) {
            int fd_in = open(config.stdin_file.c_str(), O_RDONLY);
            if (fd_in < 0) _exit(101);
            dup2(fd_in, STDIN_FILENO);
            close(fd_in);
        }

        if (!config.stdout_file.empty()) {
            int fd_out = open(config.stdout_file.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
            if (fd_out < 0) _exit(102);
            dup2(fd_out, STDOUT_FILENO);
            close(fd_out);
        }

        if (!config.stderr_file.empty()) {
            int fd_err = open(config.stderr_file.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
            if (fd_err < 0) _exit(103);
            dup2(fd_err, STDERR_FILENO);
            close(fd_err);
        } else {
            // 【安全加固】未指定 stderr 文件时重定向到 /dev/null，防止用户 cerr 刷爆后端日志
            int fd_null = open("/dev/null", O_WRONLY);
            if (fd_null >= 0) {
                dup2(fd_null, STDERR_FILENO);
                close(fd_null);
            }
        }

        // 1.2 注入系统资源限制 (setrlimit)

        // (a) CPU 时间限制 (秒级软限制)
        struct rlimit rl_cpu;
        rl_cpu.rlim_cur = (config.time_limit_ms / 1000) + 1;
        rl_cpu.rlim_max = rl_cpu.rlim_cur + 1;
        setrlimit(RLIMIT_CPU, &rl_cpu);

        // (b) 【安全加固】虚拟地址空间限制 (防止恶意堆内存炸弹触发宿主机 OOM Killer!)
        // 设为题目内存上限的 2 倍且至少 512MB，保证正常 C++ 程序稳定运行，精准 MLE 仍由 ru_maxrss 判定
        struct rlimit rl_as;
        uint64_t as_limit = std::max<uint64_t>(
            512ULL * 1024 * 1024,
            static_cast<uint64_t>(config.memory_limit_kb) * 1024ULL * 2
        );
        rl_as.rlim_cur = as_limit;
        rl_as.rlim_max = as_limit;
        setrlimit(RLIMIT_AS, &rl_as);

        // (c) 输出文件大小限制 (防止磁盘炸弹，超出触发 SIGXFSZ)
        struct rlimit rl_fsize;
        rl_fsize.rlim_cur = static_cast<uint64_t>(config.fsize_limit_mb) * 1024ULL * 1024;
        rl_fsize.rlim_max = rl_fsize.rlim_cur;
        setrlimit(RLIMIT_FSIZE, &rl_fsize);

        // (d) 运行栈大小限制 (给 128MB)
        struct rlimit rl_stack;
        rl_stack.rlim_cur = 128ULL * 1024 * 1024;
        rl_stack.rlim_max = rl_stack.rlim_cur;
        setrlimit(RLIMIT_STACK, &rl_stack);

        // (e) 禁止生成 core dump 文件
        struct rlimit rl_core;
        rl_core.rlim_cur = 0;
        rl_core.rlim_max = 0;
        setrlimit(RLIMIT_CORE, &rl_core);

        // (f) 最大进程数限制：封死 Fork 炸弹
        struct rlimit rl_nproc;
        rl_nproc.rlim_cur = 1;
        rl_nproc.rlim_max = 1;
        setrlimit(RLIMIT_NPROC, &rl_nproc);

        // (g) 【安全加固】限制最大文件描述符数量，防 FD 耗尽
        struct rlimit rl_nofile;
        rl_nofile.rlim_cur = 64;
        rl_nofile.rlim_max = 64;
        setrlimit(RLIMIT_NOFILE, &rl_nofile);

        // 1.3 安全权限降级 (清空 root 补充组 + 降权 nobody + 禁止任何提权)
        if (getuid() == 0) {
            setgroups(0, nullptr); // 清空 root 补充用户组
            setgid(65534);         // 降为 nogroup
            setuid(65534);         // 降为 nobody
        }
        prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0);

        // 1.4 组装 argv 并清空环境变量执行
        std::vector<char*> argv_ptrs;
        argv_ptrs.push_back(const_cast<char*>(config.exe_path.c_str()));
        for (const auto& arg : config.args) {
            argv_ptrs.push_back(const_cast<char*>(arg.c_str()));
        }
        argv_ptrs.push_back(nullptr);

        char* envp[] = { nullptr };
        execve(config.exe_path.c_str(), argv_ptrs.data(), envp);
        _exit(127);
    } else {
        // ================= 父进程：看门狗监控与资源采集 =================
        std::atomic<bool> is_wall_timeout{false};
        bool child_finished = false;
        std::mutex cv_m;
        std::condition_variable cv;

        int wall_limit_ms = std::max(config.time_limit_ms * 2, config.time_limit_ms + 1000);

        std::thread watchdog([&]() {
            std::unique_lock<std::mutex> lk(cv_m);
            if (!cv.wait_for(lk, std::chrono::milliseconds(wall_limit_ms), [&]() { return child_finished; })) {
                is_wall_timeout.store(true);
                kill(pid, SIGKILL);
            }
        });

        int status;
        struct rusage usage;
        wait4(pid, &status, 0, &usage);

        // 【并发修复】持锁修改状态后再通知，彻底消除 condition_variable 唤醒丢失竞态
        {
            std::lock_guard<std::mutex> lk(cv_m);
            child_finished = true;
        }
        cv.notify_one();
        if (watchdog.joinable()) {
            watchdog.join();
        }

        ExecutionResult res;
        res.cpu_time_ms = (usage.ru_utime.tv_sec + usage.ru_stime.tv_sec) * 1000 +
                          (usage.ru_utime.tv_usec + usage.ru_stime.tv_usec) / 1000;
        res.memory_kb = usage.ru_maxrss;
        res.is_wall_timeout = is_wall_timeout.load();

        if (WIFEXITED(status)) {
            res.exit_code = WEXITSTATUS(status);
            res.signal = 0;
        } else if (WIFSIGNALED(status)) {
            res.exit_code = -1;
            res.signal = WTERMSIG(status);
        }

        return res;
    }
}