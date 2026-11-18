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
- **续期阈值**：当检测到 Key 的剩余 TTL 小于 1 天（86400 秒）时，自动触发 `expire` 重置回 7 天。
- **JWT 物理有效期**：设为 30 天，只作防伪签名兜底；真实会话生命周期完全由 Redis 控制。

### 1.3 三级 RBAC 权限与 ROOT 防篡权机制
- **角色梯队**：
  - `USER`：普通选手，仅能使用做题、参赛、题解功能；
  - `ADMIN`：管理人员，负责题目上传与比赛管理，严禁触碰用户权限；
  - `ROOT`：超级根用户，唯一有权管理用户列表、调整人员角色。
- **内核防御铁律 (Anti-Coupe Guard)**：
  1. 任何人不得修改自己的权限角色（防误降级）；
  2. 初始 ROOT 账号（如 ID: 1）拥有绝对代码级豁免权，任何接口调用只要针对 1 号账号发降级或封禁指令，后端无条件抛出 `BusinessException` 原地驳回！

---

## 2. 异步判题队列系统设计 (Asynchronous Judge Queue)

### 2.1 核心设计目标
- **解除 HTTP 线程阻塞**：将 `process.waitFor()` 从 Tomcat 工作线程中剥离，HTTP 请求 10ms 内极速响应。
- **流量削峰填谷**：即使突发并发涌入 1000 个提交，评测任务依然在队列排队，服务器 CPU 不会被打爆。
- **并发受控执行**：控制同时运行的 `core-judge` 进程数不超过物理核心数。

### 2.2 队列架构与数据流转模型

```text
[用户提交代码] ──► Controller (POST /api/submissions)
                       │
                       ├─ 1. 插入 MySQL (tb_submission: verdict="PENDING")
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
│ 2. 调用 JudgeService 执行 C++ 沙盒 ./core-judge                         │
│ 3. 将评测结果 (Accepted/WA/耗时/内存) 最终写回 MySQL                     │
└────────────────────────────────────────────────────────────────────────┘
```

### 2.3 状态机转换图
```text
[创建提交] ──► PENDING (排队中) ──► RUNNING (评测中) ──► 终态 (ACCEPTED / WRONG_ANSWER / TLE / etc.)
```

### 2.4 Redis Key 规范汇总表

| Key 格式                 | 类型 | 用途说明 | TTL 策略 |
|:-----------------------| :--- | :--- | :--- |
| `login:token:{userId}` | String | 存放当前活跃用户的 Token 凭据 | 7 天 (滑动续期) |
| `oj:judge:queue`       | List | 判题任务待处理 FIFO 队列 (内容为 submissionId) | 永久 (消费即出队) |

### 2.5 用户提交频控策略 (Rate Limiting)
- **目标**：防止恶意脚本调用接口刷爆评测机队列。
- **Key 命名格式**：`limit:submit:user:{userId}`
- **规则**：单用户 5 秒内仅允许提交 1 次代码。
- **实现机制**：利用 Redis 原子指令 `SET key value EX 5 NX`。若 Key 已存在，立即熔断并抛出 `BusinessException`。

### 2.6 故障容灾与僵尸任务恢复机制 (Crash Recovery)
- **目标**：防止服务器意外崩溃或重启时，导致内存中的任务永远卡死在 `PENDING` 或 `RUNNING`。
- **执行时机**：Spring Boot 启动初始化阶段 (`@PostConstruct`)。
- **处理逻辑**：
  1. 扫描 MySQL 中状态为 `PENDING` 或 `RUNNING` 的所有历史滞留记录；
  2. 将其状态统一重置为 `PENDING` 并刷回数据库；
  3. 重新向 Redis 队列 `oj:judge:queue` 执行 `RPUSH`，由后台 Worker 线程池无缝接盘继续评测。

## 3. 题目发号器与可见性设计

### 3.1 题目编号防并发发号器 (ID Generator)
- **机制**：采用 Redis 单线程原子递增命令 `INCR oj:problem:id:seq`。
- **并发安全性**：即使多个管理员在同一毫秒点击“发布题目”，Redis 保证发出的题目 ID 严格唯一递增，彻底消除主键冲突和测试点文件目录碰撞风险。

### 3.2 题目可见性与生命周期流转 (Problem Lifecycle)
- `is_public = 1` (公开)：出现在题库大厅 `GET /api/problems`，任何人均可练习。
- `is_public = 0` (保密)：题库大厅物理过滤排除；仅允许被挂载在比赛（Contest）中，并在比赛开始后通过比赛路由访问（映射为 Problem A, B...）。
- **赛后一键公开机制**：比赛结束后，管理员可调用接口一键将本场所有题目更新为 `is_public = 1`，题目自动流入公共题库供赛后补题。

---

## 4. 竞赛系统设计与核心 ICPC 罚时算法

### 4.1 题目映射模型
- 比赛内部不暴露真实题目 ID，统一映射为英文字母展示代号（`A`, `B`, `C`, `D`...）。
- 比赛中的代码提交，在 `tb_submission` 中记录唯一的 `contest_id`，与日常练习提交物理隔离。

### 4.2 ICPC 榜单计算数学模型
- **排名规则 1（通过题数优先）**：解题总数（`solvedCount`）多者排名靠前；
- **排名规则 2（罚时少者优先）**：通过题数相同时，总罚时（`totalPenalty`，单位：分钟）少者排名靠前；
- **单题罚时算法**：
  $$\text{Penalty}_{\text{problem}} = T_{\text{AC}} + W \times 20$$
  - $T_{\text{AC}}$：比赛开始到该题第一次 AC 的分钟数（向下取整）；
  - $W$：该题在首次 AC 之前的错误提交次数（WA、TLE、MLE、RE 计入；CE 编译错误不计入罚时）；
  - **未通过的题目不计算任何罚时**（无论错多少次均不计入总罚时）。

---

## 5. 社区讨论与公告联动设计 (预留)
- 比赛创建时，系统自动在 `tb_post` 表生成一条类型为 `ANNOUNCEMENT` 的置顶公告贴，正文使用 Markdown 模板动态渲染比赛规则与参赛链接。


## 6. AI 代码诊断 Agent 系统设计 (AI Code Mentor Architecture)

### 6.1 异构微服务解耦架构
为兼顾高并发工程稳定性与 AI 生态（LangChain / RAG / 向量检索）的灵活性，系统采用 **Spring Boot + Python FastAPI** 双端异构微服务架构：
- **Spring Boot 业务主网关 (`oj-backend`)**：负责身份认证、防作弊红线拦截、Redis 分布式防重锁、每日调用配额管控、上下文原料（题面/源码/报错/官方题解）组装，以及最终诊断报告的 MySQL 持久化（`tb_ai_analysis`）。
- **Python AI 推理微服务 (`oj-agent`)**：基于 FastAPI + LangChain + DeepSeek 构建的无状态（Stateless）异步推理引擎。负责执行领域知识 RAG 检索、动态分层 Prompt 组装、LLM 异步流转（`ainvoke`）及 Pydantic 结构化输出安检。
- **内部通讯协议**：Spring Boot 通过内网 HTTP (`POST /api/v1/analyze`) 调用 Python 微服务，超时阈值设定为 `35s`（对齐 Python 端 LLM `30s` 超时 + 重试开销）。支持通过请求头 `X-Custom-Api-Key` 透传自定义大模型密钥（BYOK 模式预留）。

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
                             ├─ 5. 获取分布式短锁 (Redis: SET lock:ai:analyze:{subId} 1 EX 60 NX)
                             ├─ 6. 组装上下文载荷 (Problem 题面 + Submission 代码/报错 + 精选官方题解)
                             │
                             ▼ (内网 HTTP POST /api/v1/analyze, snake_case 契约)
┌────────────────────────────────────────────────────────────────────────┐
│ Python FastAPI 智能诊断微服务 (oj-agent)                                 │
│ 1. RAG 知识融合：融合传入的 official_editorial 与向量库底层避坑指南        │
│ 2. 分层提示词工程：根据 AC / WA / TLE / RE / CE 动态构建苏格拉底引导提示词  │
│ 3. 异步非阻塞推理：调用 DeepSeek (temperature=0.2, json_object 模式)      │
│ 4. 清洗与安检：正则剥离 Markdown 包裹 + Pydantic (OJCodeAnalysisReport) 校验│
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
                                     ▼ 返回结构化 JSON 报告
┌────────────────────────────────────────────────────────────────────────┐
│ Spring Boot 后置持久化与响应处理 (AiCoachService)                        │
│ 1. Jackson (@JsonAlias) 将下划线 JSON 反序列化为驼峰 AiAnalysisReportVO  │
│ 2. 写入 MySQL (tb_ai_analysis) 归档分块 JSON 与 raw_report               │
│ 3. 递增用户当日配额计数器 (INCR limit:ai:daily:{yyyyMMdd}:{userId})       │
│ 4. 释放 Redis 分布式锁 (finally 块兜底) 并向前端返回标准小驼峰 VO         │
└────────────────────────────────────────────────────────────────────────┘
```

### 6.3 防作弊红线与前置拦截铁律 (Anti-Cheating Guard)
1. **触发所有权校验**：仅允许 `submission.userId == currentUserId`（提交者本人）或 `ADMIN`/`ROOT` 触发诊断，杜绝恶意消耗他人或系统算力。
2. **赛中绝对隔离**：若 `submission.contestId != null`，必须实时联查比赛状态。一旦处于 `RUNNING`（进行中），非管理员无条件抛出 `403 Forbidden`（*“比赛正在进行中，严禁使用 AI 代码诊断功能！”*）；待比赛进入 `ENDED` 状态后自动解禁以支持赛后复盘。
3. **保密题隔离**：若关联题目 `is_public == 0` 且不处于已结束比赛的公开上下文，非管理员无条件拦截。
4. **评测终态约束**：当 `submission.verdict` 为 `PENDING` 或 `RUNNING` 时拒绝调用，确保输入给 Agent 的 `judge_status` 为有效终态。

### 6.4 幂等控制、防并发锁与配额限流设计

#### 6.4.1 Redis Key 规范扩展表 (AI 模块)

| Key 格式 | 类型 | 用途说明 | TTL 策略 |
| :--- | :--- | :--- | :--- |
| `lock:ai:analyze:{submissionId}` | String | 单次提交 AI 诊断防双击并发分布式锁 | 60 秒 (执行完毕主动释放) |
| `limit:ai:daily:{yyyyMMdd}:{userId}` | String | 普通用户每日 AI 诊断成功调用次数计数器 | 24 小时 (86400 秒) |

#### 6.4.2 核心控制策略
1. **单提交单次分析（永久幂等）**：
  - 数据库层：`tb_ai_analysis.submission_id` 建立 `UNIQUE` 唯一索引。
  - 应用层：请求到达后优先查询 `tb_ai_analysis`。若命中历史记录，直接读取 `raw_report` 字段反序列化为 `AiAnalysisReportVO` 返回，不触发锁、不扣减配额、不请求 Python 端。
2. **分布式防重短锁（防双击穿透）**：
  - 针对未生成报告的 `submissionId`，执行 `SET lock:ai:analyze:{submissionId} 1 EX 60 NX`。
  - 获取失败说明已有协程正在请求大模型，立即熔断返回提示：*“AI 正在全力诊断该份代码中，请勿重复点击！”*。
  - 无论 Python 端推理成功或抛出异常，均在 `finally` 块中执行 `DEL` 释放锁。
3. **每日调用配额（Daily Quota）**：
  - 普通用户 (`USER`) 每日上限 **20 次**（管理员 `ADMIN`/`ROOT` 豁免）。
  - **扣减时机**：采用“**先校验、成功后扣减**”原则。请求前校验当前计数 `< 20`；仅当 Python 微服务成功返回且落库后，方可执行 `INCR`（首次创建时设置 `EXPIRE 86400`），避免因大模型网络超时或异常导致用户配额被误扣。

### 6.5 跨语言契约转换规范 (Snake Case $\leftrightarrow$ Camel Case)
1. **Spring Boot $\rightarrow$ Python (`AgentAnalyzeRequestDTO`)**：
  - 遵循 Python PEP8 规范，统一使用 `@JsonProperty("snake_case")` 序列化为下划线格式（注：移除冗余的 `user_id` 字段，保持推理引擎无状态化）。
  - 若评测结果为 `Wrong Answer` / `Compile Error` / `Runtime Error`，由 Java 端解析 `tb_submission.judge_details` 提取首个失败测试点或编译报错文本，填入 `error_message` 或 `failed_test_case`。
2. **Python $\rightarrow$ Spring Boot $\rightarrow$ 前端 (`AiAnalysisReportVO`)**：
  - Python 端 `OJCodeAnalysisReport` 吐出下划线格式 JSON，Java 端将其原样存入 `tb_ai_analysis.raw_report` 留档。
  - `AiAnalysisReportVO` 内部属性采用 `@JsonAlias("snake_case")` 注解修饰标准小驼峰字段（如 `currentMethod`）。实现**反序列化时兼容 Python 下划线输出，序列化响应给前端时保持全站统一的小驼峰格式**。

### 6.6 模块扩展演进规范 (Future Proofing)
- **当前阶段 (V1.0)**：AI 诊断作为单次提交记录（`Submission`）的聚合子资源，路由挂载于 `SubmissionController` (`POST /api/submission/{id}/ai-analyze`)，并在详情接口 (`GET /api/submission/{id}`) 中内嵌回显 `aiReport`。
- **后续演进阶段 (V2.0+)**：若后续扩展独立于单次提交的 AI 能力（如：多轮苏格拉底对话助手、编辑器实时代码补全/提示、管理员 AI 辅助造题、用户自定义 API Key 管理等），将在后端新建独立的 **`AiAgentController` (`/api/ai/**`)**，并在本文档 `6.7+` 小节持续追加详细设计。

---
