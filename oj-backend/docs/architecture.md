# Core OJ 后端架构与系统详细设计规格书 (TDD)

## 1. 认证与会话架构 (Authentication & Session)

### 1.1 Token 存储与 Key 命名规范
- **存储介质**：Redis String
- **Key 命名格式**：`login:user:{userId}`（例如 `login:user:1`）
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

| Key 格式 | 类型 | 用途说明 | TTL 策略 |
| :--- | :--- | :--- | :--- |
| `login:user:{userId}` | String | 存放当前活跃用户的 Token 凭据 | 7 天 (滑动续期) |
| `oj:judge:queue` | List | 判题任务待处理 FIFO 队列 (内容为 submissionId) | 永久 (消费即出队) |

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
