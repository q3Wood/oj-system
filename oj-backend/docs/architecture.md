# Core OJ 后端架构与系统详细设计规格书 (TDD)

## 1. 认证与会话架构 (Authentication & Session)

### 1.1 Token 存储与 Key 命名规范
- **存储介质**：Redis String
- **Key 命名格式**：`login:token:{userId}`（例如 `login:token:1`）
- **Value 内容**：签发的完整 JWT 字符串
- **生命周期 (TTL)**：初始 7 天 (604800 秒)
- **多端互踢机制**：单点登录 (SSO)。同一账号再次登录时，直接覆盖该 Key，前序旧设备 Token 即刻失效。

### 1.2 滑动过期与自动续期策略 (Sliding Session)
- **检查时机**：请求经过 `LoginInterceptor` 时。
- **续期阈值**：当检测到 Key 的剩余 TTL 小于 24 小时（1 天）时，自动触发 `expire` 重置回 7 天。
- **JWT 物理有效期**：设为 30 天，只作防伪签名兜底；真实会话生命周期完全由 Redis 控制。
- **线程安全清理**：请求结束时在 `afterCompletion` 中强制执行 `UserContext.remove()` 清理 `ThreadLocal`，杜绝 Tomcat 线程池复用引发的上下文串台与内存泄漏。

### 1.3 三级 RBAC 权限与 ROOT 防篡权机制
- **角色梯队**：
  - `USER`：普通选手，仅能使用做题、参赛、题解功能；
  - `ADMIN`：管理人员，负责题目上传与比赛管理，严禁触碰用户权限与全站更新日志；
  - `ROOT`：超级根用户，唯一有权管理用户列表、调整人员角色及发布系统更新日志。
- **内核防御铁律 (Anti-Coupe Guard)**：
  1. 拦截器统一守卫所有 `/api/**/admin` 及 `/api/**/admin/**` 后台路由；
  2. 任何人不得修改自己的权限角色（防误降级）；
  3. 初始 ROOT 账号（`ID = 1`）拥有绝对代码级豁免权，任何针对 1 号账号的降级或封禁指令均无条件抛出 `BusinessException` 原地驳回。

---

## 2. 异步判题队列与 Redis 高可用组件设计

### 2.1 核心设计目标
- **解除 HTTP 线程阻塞**：将 `process.waitFor()` 从 Tomcat 工作线程中剥离，HTTP 请求 10ms 内极速响应。
- **流量削峰填谷**：即使突发并发涌入 1000 个提交，评测任务依然在队列排队，服务器 CPU 不会被打爆。
- **并发受控执行**：控制同时运行的 `core-judge` 进程数不超过物理核心数，配合短路评测避免死循环算力雪崩。

### 2.2 队列架构与数据流转模型

```text
[用户提交代码] ──► Controller (POST /api/submission)
                       │
                       ├─ 1. 插入 MySQL (tb_submission: verdict="PENDING"，确保事务已落盘提交)
                       ├─ 2. RPUSH 推入 Redis 队列 (oj:judge:queue, payload=submissionId)
                       └─ 3. 立即返回 HTTP 200 {"verdict": "PENDING"} (耗时 ~10ms)
                                     │
                                     ▼ (Redis List: oj:judge:queue)
┌────────────────────────────────────────────────────────────────────────┐
│ 后台常驻监听线程 (JudgeQueueListener)                                    │
│ 循环执行 BLPOP oj:judge:queue 阻塞式获取任务 ID                          │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
                                     ▼ 分发任务
┌────────────────────────────────────────────────────────────────────────┐
│ 评测执行独立线程池 (JudgeThreadPoolExecutor)                             │
│ - 核心线程数: 2 (或等于 CPU 物理核数)                                     │
│ - 最大线程数: 4                                                         │
│                                                                        │
│ 每一个工作线程执行:                                                      │
│ 1. 更新 MySQL 状态为 "RUNNING"                                          │
│ 2. 调用 JudgeService 执行 C++17 沙箱 ./core-judge                       │
│ 3. 将评测结果 (Accepted/WA/耗时/内存) 最终写回 MySQL                     │
└────────────────────────────────────────────────────────────────────────┘
```
- **事务提交与异步消费时序铁律**：严禁在未提交的长事务内部提前执行 `RPUSH`，必须确保 `tb_submission` 记录已在 MySQL 落盘提交后再推入 Redis 队列，彻底消除异步线程因数据库事务隔离级别（MVCC）读不到未提交记录而引发的竞态 Bug。

### 2.3 状态机转换图
```text
[创建提交] ──► PENDING (排队中) ──► RUNNING (评测中) ──► 终态 (Accepted / Wrong Answer / TLE / etc.)
```

### 2.4 全站 Redis Key 规范汇总总表

| Key 格式 | 类型 | 用途说明 | TTL 策略 |
| :--- | :--- | :--- | :--- |
| `login:token:{userId}` | String | 存放当前活跃用户的 JWT Token 凭据 | 7 天 (不足 24h 滑动续期) |
| `oj:judge:queue` | List | 判题任务待处理 FIFO 队列 (`RPUSH` / `BLPOP`) | 永久 (消费即出队) |
| `limit:submit:user:{userId}` | String | 用户提交代码 5 秒冷却频控锁 | 5 秒 (`SET NX EX 5`) |
| `oj:problem:id:seq` | String | 题目编号全局原子递增发号器 (`INCR`) | 永久 |
| `oj:solution:like:{solutionId}` | Set | 题解点赞用户 ID 集合 (`SADD` / `SREM`) | 永久 |
| `lock:ai:analyze:{submissionId}` | String | 单次提交 AI 诊断防双击并发分布式锁 | 60 秒 (Lua 脚本主动安全释放) |
| `limit:ai:daily:{yyyyMMdd}:{userId}` | String | 普通用户自然日 AI 诊断调用次数计数器 | 24 小时 (自然日切换自动重置) |
| `oj:changelog:latest` | String | 系统最新一条更新日志摘要缓存 (含 `"NONE"` 空占位) | 24 小时 + 随机抖动 |

### 2.5 通用缓存与安全分布式锁组件 (`RedisCacheClient`)
为统一治理高并发下的缓存异常与分布式锁安全性，系统在 `utils.RedisCacheClient` 中封装了四大核心机制：
1. **防缓存穿透 (Cache Penetration)**：当数据库查询结果为空（如暂无更新日志或查询不存在的资源）时，向 Redis 写入空值占位符 `"NONE"`（短 TTL 30 分钟），拦截针对数据库的无效重复穿透攻击。
2. **防缓存雪崩 (Cache Avalanche)**：写入缓存时，在基础 TTL 之上自动叠加 `0 ~ 10%` 的随机时间抖动偏移（Random Jitter），打散大批量 Key 的集中过期时间点。
3. **防热点缓存击穿 (Cache Breakdown)**：提供 `queryWithMutex` 高阶函数，针对热点数据失效瞬间采用“互斥锁 (`SET NX`) + Double-Check 二次查缓存”机制，保证同一时刻仅单线程回源 MySQL 重建缓存。
4. **防误删安全分布式锁 (UUID + Lua Atomic Unlock)**：
  - 加锁时生成唯一 `UUID` 作为锁 Value 凭证；
  - 解锁时通过 `DefaultRedisScript` 执行 Lua 脚本，原子比对 `redis.call('get', KEYS[1]) == ARGV[1]` 成功后方才执行 `del`，彻底杜绝因线程停顿或锁超时引发的“误删他人锁”事故。

### 2.6 用户提交频控策略 (Rate Limiting)
- **目标**：防止恶意脚本调用接口刷爆评测机队列。
- **Key 命名格式**：`limit:submit:user:{userId}`
- **实现机制**：利用 Redis 原子指令 `SET key 1 EX 5 NX`。若 Key 已存在，立即熔断并抛出 `BusinessException`（单用户 5 秒内仅允许提交 1 次）。

### 2.7 故障容灾与僵尸任务恢复机制 (Crash Recovery)
- **目标**：防止服务器意外崩溃或重启时，导致进行中的判题任务永久卡死在 `PENDING` 或 `RUNNING`。
- **执行时机**：Spring Boot 启动初始化阶段 (`@PostConstruct`)。
- **处理逻辑**：
  1. 扫描 MySQL 中状态为 `PENDING` 或 `RUNNING` 的所有历史滞留记录；
  2. 将其状态统一重置为 `PENDING` 并刷回数据库；
  3. 重新向 Redis 队列 `oj:judge:queue` 执行 `RPUSH`，由后台 Worker 线程池无缝接盘继续评测，保障判题零丢单。

---

## 3. 题目发号器与可见性设计

### 3.1 题目编号防并发发号器 (ID Generator)
- **机制**：采用 Redis 单线程原子递增命令 `INCR oj:problem:id:seq`（从 `1001` 起步）。
- **并发安全性**：即使多个管理员在同一毫秒点击“发布题目”，Redis 保证发出的题目 ID 严格唯一递增，彻底消除主键冲突和测试点文件目录碰撞风险。

### 3.2 题目可见性与生命周期流转 (Problem Lifecycle)
- `is_public = 1` (公开)：出现在题库大厅 `GET /api/problem/list`，任何人均可练习。
- `is_public = 0` (保密)：题库大厅物理过滤排除；仅允许被挂载在比赛（Contest）中，并在比赛开始后通过比赛路由访问（映射为 Problem A, B...）。
- **赛后一键公开机制**：比赛结束后，管理员可调用接口一键将本场所有题目更新为 `is_public = 1`，题目自动流入公共题库供赛后补题。

---

## 4. 竞赛系统设计与核心 ICPC 罚时算法

### 4.1 题目映射模型
- 比赛内部不暴露真实题目 ID，统一映射为英文字母展示代号（`A`, `B`, `C`, `D`...）。
- 比赛中的代码提交，在 `tb_submission` 中记录唯一的 `contest_id`；若比赛已结束 (`ENDED`)，后端自动将 `contest_id` 剥离置为 `null` 平滑转为日常补题练习，不污染历史比赛榜单。

### 4.2 ICPC 榜单计算数学模型
- **排名规则 1（通过题数优先）**：解题总数（`solvedCount`）多者排名靠前；
- **排名规则 2（罚时少者优先）**：通过题数相同时，总罚时（`totalPenalty`，单位：分钟）少者排名靠前；
- **单题罚时算法**：
  $$\text{Penalty}_{\text{problem}} = T_{\text{AC}} + W \times 20$$
  - $T_{\text{AC}}$：比赛开始到该题第一次 AC 的分钟数（向下取整）；
  - $W$：该题在首次 AC 之前的错误提交次数（WA、TLE、MLE、RE 计入；`Compile Error` 编译错误不计入罚时）；
  - **未通过的题目不计算任何罚时**（无论错多少次均不计入总罚时）。

---

## 5. 题解社区、原子点赞与赛事公告联动设计

### 5.1 基于 Redis Set 的高并发原子点赞
- **去重与幂等切换**：利用 Redis Set (`oj:solution:like:{solutionId}`) 存储点赞用户的 `userId`。
- **操作流转**：用户点击点赞时，通过 `SISMEMBER` 判断状态：未点赞则执行 `SADD` 并递增题解表点赞数（返回 `true`）；已点赞则执行 `SREM` 并递减点赞数（返回 `false`），天然防止同一用户并发刷赞。

### 5.2 赛事官方公告联动
- 比赛创建时，系统自动在 `tb_post` 表生成一条类型为 `ANNOUNCEMENT` 的置顶公告贴，正文使用 Markdown 模板动态渲染比赛规则与参赛链接。

---

## 6. AI 代码诊断 Agent 系统设计 (AI Code Mentor Architecture)

### 6.1 异构微服务解耦架构
为兼顾高并发工程稳定性与 AI 生态（LangChain / RAG / 向量检索）的灵活性，系统采用 **Spring Boot + Python FastAPI** 双端异构微服务架构：
- **Spring Boot 业务主网关 (`oj-backend`)**：负责身份认证、防作弊红线拦截、Redis 分布式防重锁、每日调用配额管控、上下文原料（题面/源码/报错/高赞题解）组装，以及最终诊断报告的 MySQL 持久化（`tb_ai_analysis`）。
- **Python AI 推理微服务 (`oj-agent`)**：基于 FastAPI + LangChain + DeepSeek 构建的无状态（Stateless）异步推理引擎。负责执行 ChromaDB 领域知识 RAG 检索、动态分层 Prompt 组装、LLM 异步流转（`ainvoke`）及 Pydantic 结构化输出安检。
- **内部通讯协议**：Spring Boot 显式指定 `HTTP/1.1` 协议通过内网调用 Python `uvicorn` 微服务 (`POST /api/v1/analyze`)，超时阈值设定为 `35s`（对齐 Python 端 LLM `30s` 超时 + 重试开销）。

### 6.2 诊断请求全链路流转模型

```text
[用户点击“AI 诊断”] ──► Controller (POST /api/submission/{id}/ai-analyze)
                             │
                             ├─ 1. 权限与防作弊校验 (仅限本人/管理员；拦截 RUNNING 比赛与保密题)
                             ├─ 2. 评测状态校验 (拦截 PENDING / RUNNING，必须处于评测终态)
                             ├─ 3. 幂等查询 MySQL (tb_ai_analysis)
                             │     └─ 若已存在报告 ──► 直接反序列化 raw_report 极速返回 (耗时 ~5ms)
                             │
                             ├─ 4. 校验每日配额 (Redis: limit:ai:daily:{yyyyMMdd}:{userId} < 20)
                             ├─ 5. 获取分布式短锁 (RedisCacheClient.tryLock: UUID 凭证, EX 60 NX)
                             ├─ 6. 组装上下文载荷 (Problem 题面 + Submission 代码/报错 + 最高赞题解)
                             │
                             ▼ (内网 HTTP/1.1 POST /api/v1/analyze, snake_case 契约)
┌────────────────────────────────────────────────────────────────────────┐
│ Python FastAPI 智能诊断微服务 (oj-agent)                                 │
│ 1. RAG 知识融合：融合传入的 official_editorial 与 ChromaDB 底层避坑指南   │
│ 2. 分层提示词工程：结合 Few-Shot 与评测状态构建苏格拉底引导提示词          │
│ 3. 异步非阻塞推理：调用 DeepSeek (temperature=0.2, json_object 模式)      │
│ 4. 清洗与安检：正则剥离 Markdown 包裹 + Pydantic (OJCodeAnalysisReport) 校验│
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
                                     ▼ 返回结构化 JSON 报告 (含独立时间/空间复杂度)
┌────────────────────────────────────────────────────────────────────────┐
│ Spring Boot 后置持久化与响应处理 (AiCoachServiceImpl)                    │
│ 1. Jackson (@JsonAlias) 将下划线 JSON 反序列化为驼峰 AiAnalysisReportVO  │
│ 2. 写入 MySQL (tb_ai_analysis) 归档分块 JSON 与 raw_report               │
│ 3. 递增用户当日配额计数器 (INCR limit:ai:daily:{yyyyMMdd}:{userId})       │
│ 4. Lua 脚本原子核验 UUID 并释放分布式锁 (finally 兜底)，向前端返回小驼峰 VO│
└────────────────────────────────────────────────────────────────────────┘
```

### 6.3 防作弊红线与前置拦截铁律 (Anti-Cheating Guard)
1. **触发所有权校验**：仅允许 `submission.userId == currentUserId`（提交者本人）或 `ADMIN`/`ROOT` 触发诊断，杜绝恶意消耗他人或系统算力。
2. **赛中绝对隔离**：若 `submission.contestId != null`，实时联查比赛状态。一旦处于 `RUNNING`（进行中），非管理员无条件抛出 `403 Forbidden`；待比赛进入 `ENDED` 状态后自动解禁以支持赛后复盘。
3. **保密题隔离**：若关联题目 `is_public == 0` 且不处于已结束比赛的公开上下文，非管理员无条件拦截。
4. **评测终态约束**：当 `submission.verdict` 为 `PENDING` 或 `RUNNING` 时拒绝调用。

### 6.4 幂等控制、防并发锁与配额限流策略
1. **单提交单次分析（永久幂等）**：`tb_ai_analysis.submission_id` 建立 `UNIQUE` 唯一索引。命中历史记录直接读取 `raw_report` 反序列化返回，不加锁、不扣配额、不请求 Python 端。
2. **分布式防重短锁（防双击穿透）**：借助 `RedisCacheClient.tryLock` 加 60 秒分布式锁，并在 `finally` 中使用 Lua 脚本比对 UUID 安全解锁。
3. **自然日调用配额（Daily Quota）**：普通用户每日上限 **20 次**（管理员豁免）。采用“**先校验、成功后扣减**”原则，Key 命名携带当日日期 `yyyyMMdd` 实现每日 0 点精准切换重置，并附带 24h TTL 自动回收历史过期 Key。

### 6.5 跨语言契约转换规范 (Snake Case $\leftrightarrow$ Camel Case)
- **Spring Boot $\rightarrow$ Python (`AgentAnalyzeRequestDTO`)**：使用 `@JsonProperty("snake_case")` 序列化为下划线格式。
- **Python $\rightarrow$ Spring Boot $\rightarrow$ 前端 (`AiAnalysisReportVO`)**：采用 `@JsonAlias("snake_case")` 修饰标准小驼峰属性（包含 `currentTimeComplexity`、`currentSpaceComplexity`、`spaceOptimizationAdvice` 等），实现反序列化兼容 Python 下划线输出、序列化给前端保持全站统一小驼峰格式。

### 6.6 AI 智能造题与沙箱对拍引擎设计 (V2.0 Autonomous Problem Maker)

#### 6.6.1 核心痛点与沙箱内建对拍 (In-Sandbox Stress Testing)
传统大模型出题普遍存在“**标程存在隐蔽边界 Bug**”、“**测试数据强度不足（暴力解可水过）**”以及“**生成器死循环拖垮宿主机**”三大工程隐患。
为此，系统在 V2.0 引入 **“生成-编译-造数-对拍-自修复”** 闭环架构，并将代码执行全部收拢至 C++ `core-judge` 隔离沙箱内：
1. **全链路沙箱托管**：无论是数据生成器（`gen.cpp`）、最优标程（`std.cpp`）还是暴力对拍程序（`brute.cpp`），均通过 `SandboxRunner` 在 `nobody (65534)` 降权、禁绝子进程派生（`RLIMIT_NPROC=1`）及独立看门狗（Watchdog）监控下执行，杜绝 AI 生成恶意或死循环代码威胁 2C4G 宿主机安全。
2. **零大文件跨网传输**：测试点文件（`.in` / `.out`）直接在共享存储的草稿隔离区（`{problemsDir}/_drafts/{taskId}/`）落盘，微服务间仅传递轻量级校验摘要与截断后的反例切片（Snippet $\le 512\text{ Bytes}$），避免百万级测试数据序列化引发 JVM / Python OOM。
3. **双重质量门禁（Correctness & Anti-Brute）**：
  - **小数据正确性对拍**：在前 $K$ 组中小规模/边界测试点上，强制比对 `std.cpp` 与 `brute.cpp` 的输出（`Checker::strict_diff`），确保标程逻辑零瑕疵。
  - **极限数据区分度压测**：在满载极限测试点上，校验 `std.cpp` 耗时处于安全水位（$\le 70\% \text{ TimeLimit}$），同时可选校验 `brute.cpp` 是否如期触发 `TLE/MLE`，确保造出的测试数据具备足够强度拦截次优解。

#### 6.6.2 C++ 评测内核扩展规范 (`core-judge --mode gen-verify`)
在不影响原有常规评测模式（默认 `--mode judge`）的前提下，`core-judge` 扩展 `--mode gen-verify` 造题验题模式：
- **命令行调用契约**：
  ```bash
  core-judge --mode gen-verify \
             --prob-dir /path/to/problems/_drafts/{taskId} \
             --gen-src /tmp/gen_xxx.cpp \
             --std-src /tmp/std_xxx.cpp \
             --brute-src /tmp/brute_xxx.cpp \
             --plan /tmp/plan_xxx.json \
             --time 1000 --mem 256 --json
  ```
- **测试计划描述文件 (`--plan`) 零依赖行协议**：
  为保持 C++ 评测内核零第三方库依赖，Java 端将测试计划序列化为按行分隔的纯文本指令文件 `plan.txt`，每行代表一个测试点：
  `{case_id} {check_brute:0|1} {expect_brute_fail:0|1} {arg_1} {arg_2} ...`
  例如：`10 1 1 100000 110` 表示第 10 号测试点开启对拍且期望拦截暴力解（触发 TLE/MLE），向 `./gen_bin` 注入命令行参数 `100000 110`。

- **五阶段快速失败（Fail-Fast）流水线**：
  1. **`COMPILE`（三件套编译）**：依次编译 `gen.cpp`、`std.cpp`、`brute.cpp`（均开启 `-O2`）。任一编译失败立即中断，返回 `"stage": "COMPILE"`、`target`（指明哪份源码报错）及 `compile_log`，供 Agent 定向修复语法错误。
  2. **`GEN_DATA`（沙箱造数）**：按 `plan` 遍历，通过 `SandboxRunner` 执行 `./gen_bin <args...>`，标准输出直接重定向至 `{prob_dir}/{id}.in`（固定赋予生成器 `2000ms` 时限与 `512MB` 内存）。若生成器崩溃或超时，立即返回 `"stage": "GEN_DATA"` 及异常测试点编号。
  3. **`RUN_STD`（标程求解与落盘）**：以 `{prob_dir}/{id}.in` 为输入运行 `./std_bin`，输出落盘至 `{prob_dir}/{id}.out`。若标程非 `AC`（如 `TLE`/`RE`/`MLE`），立即返回 `"stage": "RUN_STD"`，并附带 `{id}.in` 的前 500 字符切片（`input_snippet`）。
  4. **`STRESS_DIFF`（小数据对拍校验）**：当 `check_brute == true` 且 `expect_brute_fail == false` 时，运行 `./brute_bin` 并与 `{id}.out` 执行严格比对。若不一致（`WA`）或暴力解崩溃，立即返回 `"stage": "STRESS_DIFF"`，同时返回 `input_snippet`、`std_output_snippet` 与 `brute_output_snippet`（各截断至 500 字符），为 Agent 提供确凿的 Hack 反例。
  5. **`ANTI_BRUTE`（极限区分度校验）**：当 `check_brute == true` 且 `expect_brute_fail == true` 时，运行 `./brute_bin`。若暴力解竟在时限内 `AC` 且输出一致，在汇总报告中标记 `"brute_blocked": false`（告警数据强度不足或暴力复杂度估算有误）。


---

## 7. 系统更新日志与未读红点设计 (Changelog & New Badge)

### 7.1 读写隔离与缓存策略 (Cache-Aside)
- **高频读场景**：全站顶部导航栏初始化加载时均会请求 `GET /api/changelog/latest` 检测是否存在近期更新。
- **Redis 缓存加速**：
  - 依托 `RedisCacheClient.queryWithPassThrough` 缓存至 `oj:changelog:latest`（String 类型）；
  - 若全站暂无有效日志，自动缓存 `"NONE"` 空占位符防止缓存穿透；写入有效数据时自动附加随机 TTL 抖动防止缓存雪崩；
  - 当 `ROOT` 执行发布（`POST`）、修改（`PUT`）或逻辑删除（`DELETE`）时，落库后立即主动删除 `oj:changelog:latest` 保证强一致性。

### 7.2 导航栏 NEW 角标智能判定算法（时间窗 + 本地已读消隐）
1. 前端顶栏挂载时调用 `GET /api/changelog/latest` 获取 `{ id, releaseDate }`。
2. 同时满足以下两个条件时，在导航栏「更新日志」标签右上角渲染 **`NEW`** 呼吸徽章：
  - **时效性条件**：`当前时间 - releaseDate <= 7 天`；
  - **未读条件**：`latest.id > Number(localStorage.getItem('oj_read_changelog_id') || 0)`。
3. **自动消隐机制**：一旦用户点进「更新日志」页面，前端立即将 `latest.id` 写入 `localStorage` 并派发 `CustomEvent('oj-changelog-read')` 实时熄灭顶栏 `NEW` 角标；直至 `ROOT` 发布更高 `id` 的新版本后自动重新点亮。

### 7.3 权限红线与复合索引优化
- **绝对 ROOT 独占写权**：后端严格校验 `currentUser.role == "ROOT"`，拦截所有 `USER` 及 `ADMIN` 写请求返回 `403`。
- **复合索引防文件排序**：在 `tb_changelog` 上建立 `(is_deleted, release_date DESC, id DESC)` 复合索引，命中 MySQL 最左前缀原则，消除时间轴倒序分页的 `Using filesort`。