# Core-Judge 评测与沙箱对拍内核架构文档

> **定位**：面向现代 Online Judge 与 AI Coding Agent 场景设计的**零第三方依赖、C++17 原生 Linux 隔离沙箱与双模评测引擎**。

---

## 1. 核心特性与设计哲学

1. **零第三方库依赖**：完全基于 C++17 标准库与 POSIX/Linux 系统调用构建，不依赖 Boost、`nlohmann/json` 等外部重型库，编译极快、二进制体积小，易于嵌入 Alpine/Ubuntu Docker 容器。
2. **双模引擎架构（Dual-Mode Engine）**：
   - **`--mode judge`（常规评测模式）**：面向 C 端学生提交评测，支持自然数测试点排序、Fail-Fast 熔断、彩色终端可视化与标准 JSON 输出。
   - **`--mode gen-verify`（AI 造题对拍验题模式）**：面向 B 端 AI 出题 Agent，在沙箱内一站式安全完成 `gen.cpp` 造数、`std.cpp` 求解、`brute.cpp` 小数据对拍 Diff 与极限数据防暴力校验，并提供定长反例切片（Snippet）驱动大模型自我修复。
3. **纵深沙箱防护**：集成 `setrlimit` 多维资源硬限、禁 Fork 炸弹、容器内自动降权（`nobody`）、环境变量清空及独立看门狗（Watchdog）真实时钟强杀机制。
4. **高并发安全隔离**：每次执行基于进程号（`PID`）在系统临时目录分配独立工作区（`/tmp/core_judge_{pid}` / `/tmp/core_gen_{pid}`），支持多进程并发调用且退出前自动清场，不残留编译产物。

---

## 2. 分层架构与目录结构

系统采用 **「极简入口路由 $\rightarrow$ 业务模式引擎 $\rightarrow$ 底层原子沙箱组件」** 三层解耦架构：

```text
                       [ CLI 参数解析与模式路由 (src/main.cpp) ]
                                          │
                  ┌───────────────────────┴───────────────────────┐
                  ▼                                               ▼
   [ 常规评测引擎 (JudgeEngine) ]                 [ 造题对拍引擎 (GenVerifier) ]
      src/judge_engine.cpp                           src/gen_verifier.cpp
                  │                                               │
                  └───────────────┬───────────────┬───────────────┘
                                  ▼               ▼
                       [ 公共工具层 (include/utils.hpp) ]
                       状态判定 / JSON转义 / 信号翻译 / 定长切片
                                          │
             ┌────────────────────────────┼────────────────────────────┐
             ▼                            ▼                            ▼
  [ 编译原子 (Compiler) ]      [ 沙箱原子 (SandboxRunner) ]    [ 比对原子 (Checker) ]
     src/compiler.cpp               src/runner.cpp               src/checker.cpp
  (fork + pipe + g++/gcc)     (fork + setrlimit + Watchdog)     (严格逐行 Diff)
```

### 目录文件清单

```text
core-judge/
├── CMakeLists.txt              # CMake 构建配置 (C++17, -Wall -Wextra -pthread)
├── Dockerfile                  # 容器化编译环境
├── include/
│   ├── common.hpp              # 核心数据模型 (CLIConfig, Verdict, JudgeReport, PlanItem, GenVerifyReport)
│   ├── utils.hpp               # 纯头文件公共工具库 (统一状态判定、JSON安全转义、自然数排序、定长文件切片)
│   ├── compiler.hpp            # 编译器封装接口
│   ├── runner.hpp              # Linux 进程沙箱接口
│   ├── checker.hpp             # 答案输出比对接口
│   ├── judge_engine.hpp        # 常规评测模式引擎声明
│   └── gen_verifier.hpp        # AI 造题对拍模式引擎声明
└── src/
    ├── main.cpp                # 极简入口：解析 CLI 参数并按 --mode 分发路由
    ├── compiler.cpp            # 隔离编译实现 (防模板递归编译炸弹)
    ├── runner.cpp              # 核心安全沙箱与内核指标采集实现
    ├── checker.cpp             # 跨平台行末空白容错比对实现
    ├── judge_engine.cpp        # 常规评测流水线实现
    └── gen_verifier.cpp        # 造题五阶段 Fail-Fast 流水线实现
```

---

## 3. 底层原子模块原理详解

### 3.1 安全编译模块 (`Compiler`)
- **管道捕获日志**：通过 `pipe()` + `fork()` + `dup2()` 将子进程编译器的 `stdout` 与 `stderr` 合并重定向至管道读端，由父进程实时收集完整报错日志（用于 `CE` 诊断与 AI 编译自修复）。
- **防编译炸弹**：子进程调用 `execvp` 前注入 `RLIMIT_CPU` 限制（默认 `10s`），防止恶意 C++ 模板元编程无限递归拖垮宿主机 CPU。
- **内置编译选项**：
  - `cpp`: `g++ -O2 -std=c++17 -Wno-unused-result -DONLINE_JUDGE <src> -o <bin> -lm`
  - `c`: `gcc -O2 -std=c11 -Wno-unused-result -DONLINE_JUDGE <src> -o <bin> -lm`

### 3.2 Linux 多维隔离沙箱 (`SandboxRunner`)
所有用户代码及 AI 生成代码（含 `gen.cpp`、`std.cpp`、`brute.cpp`）均在 `SandboxRunner::run` 中经历以下 7 重安全隔离与精准度量：

| 防护维度 | Linux 实现机制 | 具体策略与防护目标 |
| :--- | :--- | :--- |
| **I/O 隔离** | `open()` + `dup2()` | 在降权前打开并绑定 `STDIN`/`STDOUT`/`STDERR` 文件描述符，既保证子进程可读写指定文件，又使其降权后无法访问宿主机其他目录。 |
| **CPU 时间硬限** | `setrlimit(RLIMIT_CPU)` | 设为 `(time_limit_ms / 1000) + 1` 秒，超时触发内核 `SIGXCPU` 终止进程。 |
| **防输出炸弹** | `setrlimit(RLIMIT_FSIZE)` | 限制最大输出文件体积（默认 `32 MB`），防止死循环 `cout` 打满磁盘，超限触发 `SIGXFSZ` 判为 `OLE`。 |
| **防递归爆栈** | `setrlimit(RLIMIT_STACK)` | 显式放宽运行栈至 `128 MB`，避免深层 DFS/线段树递归在 Linux 默认 8MB 栈下误触发 `SIGSEGV`。 |
| **禁用核心转储** | `setrlimit(RLIMIT_CORE)` | 设为 `0`，禁止段错误时生成大型 `core dump` 垃圾文件。 |
| **防 Fork 炸弹** | `setrlimit(RLIMIT_NPROC)` | 设为 `1`，禁止目标程序繁衍任何子进程。 |
| **权限降级与环境清空** | `setgid/setuid(65534)` + `envp={nullptr}` | 若以 `root` 运行（如 Docker 容器内），`execve` 前强制降权为 `nobody/nogroup`；同时清空所有环境变量，防止窃取宿主机 API Key 与敏感配置。 |

- **双时钟超时监控（CPU + Watchdog）**：针对 `sleep()`、死锁或阻塞等待输入等不消耗 CPU 时间的假死代码，父进程启动独立看门狗线程（`std::thread` + `std::condition_variable::wait_for`），真实时钟上限设为 $\max(2 \times T,\; T + 1000\text{ ms})$，超时立即发送 `SIGKILL` 强杀。
- **内核级精确采集**：通过 `wait4(pid, &status, 0, &usage)` 读取 `struct rusage`：
  - **CPU 耗时**：`ru_utime + ru_stime`（毫秒级精度）
  - **峰值内存**：`ru_maxrss`（Linux 下单位为 KiB）
- **透传命令行参数**：`SandboxConfig::args` 动态构造以 `nullptr` 结尾的 `argv` 指针数组传给 `execve`，原生支持 `gen.cpp` 接收生成规模与随机种子参数。

### 3.3 跨平台输出比对模块 (`Checker`)
- `Checker::strict_diff` 采用逐行流式读取比对（避免大输出文件一次性加载爆内存）。
- 自动剔除每行末尾的空格、制表符 `\t` 与 Windows 换行符 `\r`（兼容 CRLF/LF 差异），同时忽略文件末尾多余的纯空白行。

---

## 4. 双运行模式与命令行契约规范

### 4.1 全局命令行参数表 (`CLIConfig`)

| 参数 | 类型 | 默认值 | 适用模式 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `--mode` | string | `judge` | 通用 | 运行模式：`judge`（常规评测）或 `gen-verify`（造题对拍） |
| `--prob-dir` | string | 必填 | 通用 | 评测题目录（`judge` 模式读取 `.in/.out`；`gen-verify` 模式写入草稿 `.in/.out`） |
| `--src` | string | `judge` 必填 | `judge` | 用户提交的源代码路径 |
| `--lang` | string | `cpp` | `judge` | 编程语言：`cpp` / `c` |
| `--gen-src` | string | `gen-verify` 必填 | `gen-verify` | 数据生成器源码路径 (`gen.cpp`) |
| `--std-src` | string | `gen-verify` 必填 | `gen-verify` | 最优标程源码路径 (`std.cpp`) |
| `--brute-src` | string | `gen-verify` 必填 | `gen-verify` | 暴力对拍源码路径 (`brute.cpp`) |
| `--plan` | string | `gen-verify` 必填 | `gen-verify` | 纯文本行协议测试计划文件路径 (`plan.txt`) |
| `--time` | int | `1000` | 通用 | 单测试点 CPU 时间限制（毫秒） |
| `--mem` | int | `256` | 通用 | 单测试点峰值物理内存限制（MB） |
| `--json` | flag | `false` | `judge` | 以标准 JSON 格式输出结果（供上层后端解析；`gen-verify` 模式默认输出 JSON） |

---

### 4.2 模式一：常规评测模式 (`--mode judge`)

#### 调用示例
```bash
# 终端可视化调试模式
./core-judge --prob-dir /data/problems/1001 --src /tmp/sub.cpp --lang cpp --time 1000 --mem 256

# 后端集成 JSON 模式
./core-judge --prob-dir /data/problems/1001 --src /tmp/sub.cpp --lang cpp --time 1000 --mem 256 --json
```

#### 核心流程
1. 编译 `--src`，失败直接返回 `Compile Error` 与编译日志。
2. 扫描 `--prob-dir` 下所有 `.in` 与 `.out`/`.ans` 文件，使用 `natural_case_compare` 按自然数序排列（确保 `2.in` 排在 `10.in` 之前）。
3. 依次沙箱运行并 `strict_diff` 比对，采用 **Fail-Fast** 策略：遇到首个非 `AC` 测试点立即终止后续测试并汇总报告。

#### JSON 输出契约
```json
{
  "verdict": "Accepted",
  "max_time_ms": 18,
  "peak_memory_kb": 3456,
  "details": [
    {
      "id": 1,
      "verdict": "Accepted",
      "time_ms": 12,
      "memory_kb": 3400,
      "exit_code": 0,
      "signal": 0,
      "message": "Correct answer"
    }
  ]
}
```
*(注：当编译失败时输出 `{"verdict":"Compile Error","compile_log":"...","details":[]}`)*

---

### 4.3 模式二：AI 造题与沙箱对拍模式 (`--mode gen-verify`)

#### 零依赖测试计划文件 (`plan.txt`) 协议
每行代表一个待生成的测试点（忽略空行与 `#` 注释行），字段以空格分隔：
```text
# 格式: <case_id> <check_brute:0|1> <expect_brute_fail:0|1> [gen_arg_1] [gen_arg_2] ...
1 1 0 5 101
2 1 0 10 102
10 1 1 100000 110
```
- `check_brute=1, expect_brute_fail=0`：**小数据对拍校验**（要求 `brute` 正常结束且输出与 `std` 100% 一致）。
- `check_brute=1, expect_brute_fail=1`：**极限数据防暴力校验**（期望 `brute` 触发 `TLE/MLE`；若 `brute` 依然 `AC`，则标记 `brute_blocked=false` 告警数据强度不足）。

#### 调用示例
```bash
./core-judge --mode gen-verify \
             --prob-dir /data/problems/_drafts/draft_001 \
             --gen-src /tmp/gen.cpp \
             --std-src /tmp/std.cpp \
             --brute-src /tmp/brute.cpp \
             --plan /tmp/plan.txt \
             --time 1000 --mem 256 --json
```

#### 五阶段 Fail-Fast 流水线
1. **`COMPILE`**：依次编译 `gen.cpp`、`std.cpp`、`brute.cpp`。任一失败立即中断并返回 `failed_target` 与 `compile_log`。
2. **`GEN_DATA`**：在沙箱内（限 `2000ms / 512MB`）运行 `./gen_bin <args...>`，标准输出直接落盘为 `{prob_dir}/{case_id}.in`。
3. **`RUN_STD`**：以 `{case_id}.in` 为输入运行 `./std_bin`，输出落盘为 `{case_id}.out`。若标程非 `AC`，截取前 500 字节输入切片并中断。
4. **`STRESS_DIFF`**：运行 `./brute_bin` 并与 `{case_id}.out` 执行 `strict_diff`。若输出不一致，同时返回 `input_snippet`、`std_output_snippet` 与 `brute_output_snippet`（各截断至 500 字节防 Token 爆炸）。
5. **`DONE`**：所有测试点生成与对拍全部通过，汇总标程最大耗时、峰值内存与 `brute_blocked` 标志。

#### JSON 输出契约
```json
{
  "passed": false,
  "stage": "STRESS_DIFF",
  "failed_target": "std",
  "failed_case_id": 2,
  "verdict": "Wrong Answer",
  "message": "Output mismatch between std and brute on case #2",
  "compile_log": null,
  "input_snippet": "10\n",
  "std_output_snippet": "999\n",
  "brute_output_snippet": "20\n",
  "max_time_ms": 2,
  "peak_memory_kb": 3240,
  "brute_blocked": true
}
```

---

## 5. 状态码 (`Verdict`) 与 Linux 信号映射规范

由 `JudgeUtils::evaluate_execution` 统一收口判定，确保双模式判定口径 100% 一致：

| Verdict 枚举 | JSON 字符串 | 触发判定条件 |
| :--- | :--- | :--- |
| `AC` | `Accepted` | 进程正常退出（`exit_code == 0, signal == 0`）、未超内存且 `strict_diff == MATCH` |
| `WA` | `Wrong Answer` | 进程正常退出但输出内容不匹配 |
| `TLE` | `Time Limit Exceeded` | 触发内核 `SIGXCPU` 或看门狗真实时钟超时 (`is_wall_timeout == true`) |
| `MLE` | `Memory Limit Exceeded` | 内核采集峰值物理内存 `ru_maxrss > memory_limit_mb * 1024` |
| `OLE` | `Output Limit Exceeded` | 输出文件超过 `fsize_limit_mb` 触发 `SIGXFSZ` |
| `RE` | `Runtime Error` | 非零退出码，或被 `SIGSEGV`（段错误）、`SIGFPE`（除零）、`SIGABRT`、`SIGKILL` 等终止 |
| `CE` | `Compile Error` | `g++` / `gcc` 编译退出码非 0 或编译超时 |
| `SE` | `System Error` | `fork()` 失败、测试计划文件为空或沙箱系统级异常 |

---

## 6. 编译构建与部署说明

### 环境依赖
- Linux (推荐 Ubuntu 20.04+ / WSL2)
- `g++` >= 9.0 (支持 C++17 与 `<filesystem>`)
- `cmake` >= 3.16

### 本地构建命令
```bash
cd core-judge
mkdir -p build && cd build
cmake ..
make -j$(nproc)
```
构建完成后将在 `build/` 目录生成独立的 **`core-judge`** 可执行文件，确保 `oj-backend` 的 `application.yml` 中 `oj.judge.binary-path` 指向该二进制路径即可。

---

## 7. 后续安全与能力演进规划 (Roadmap)

- **Seccomp-BPF 系统调用白名单**：在 `runner.cpp` 降权后注入 BPF 规则，仅放行 `read/write/brk/mmap/exit_group` 等必要系统调用，从内核态彻底阻断 `socket` 联网与非法文件探测。
- **Special Judge (SPJ) 支持**：针对多解/浮点误差类算法题，在 `Checker` 层扩展自定义校验程序支持。
- **自定义测试运行模式 (`--mode custom-run`)**：复用于前端代码编辑器的在线“自测运行”功能与 C 端 AI Debug Agent 的反例验证闭环。
