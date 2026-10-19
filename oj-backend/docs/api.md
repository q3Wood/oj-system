# OJ 在线判题系统接口文档 - V1.0

## 通用约定
- **基础路径**：`/api`
- **数据格式**：`application/json`
- **通用响应结构**：
  ```json
  {
    "code": 0,          // 业务状态码：0-成功，1-失败
    "message": "操作成功", // 提示文本
    "data": { ... }     // 真实业务载荷
  }
  ```

---

## 1. 用户模块接口 (User)

### 1.1 用户注册
- **接口路径**：`POST /api/user/register`
- **请求参数说明**：

| 名称 | 类型 | 是否必须 | 默认值 | 备注 |
| :--- | :--- | :--- | :--- | :--- |
| username | string | 必须 | 无 | 用户名 (4~16位非空字符) |
| password | string | 必须 | 无 | 密码 (5~16位非空字符) |

- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码: 0-成功, 1-失败 |
| message | string | 非必须 | 提示信息 (如 "用户名已存在") |
| data | null | 必须 | 返回数据为空 |

---

### 1.2 用户登录
- **接口路径**：`POST /api/user/login`
- **请求参数说明**：同注册 (username, password)
- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码: 0-成功, 1-失败 |
| message | string | 非必须 | 提示信息 |
| data | string | 必须 | **JWT Token 凭证** (用于后续鉴权) |

- **响应样例**：
```json
{
  "code": 0,
  "message": "登录成功",
  "data": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

---

### 1.3 获取当前登录用户信息
- **接口路径**：`GET /api/user/info`
- **请求头**：`Authorization: Bearer <Token>`
- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码 |
| message | string | 非必须 | 提示信息 |
| data | object | 必须 | 用户核心对象 |
| \|- id | number | 必须 | 用户唯一主键 ID |
| \|- username | string | 必须 | 用户名 |
| \|- role | string | 必须 | 用户角色 (USER / ADMIN) |
| \|- createdAt | string | 必须 | 注册时间 |

### 1.4 用户退出登录
- **接口路径**：`POST /api/user/logout`
- **请求头**：`Authorization: Bearer <Token>`
- **说明**：主动注销当前会话，后端将物理销毁 Redis 中对应的 Token 凭证。
- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码：0-成功 |
| message | string | 必须 | 提示信息 ("退出成功") |
| data | null | 必须 | 空 |

### 1.5 获取全量用户列表 (需 ROOT 权限)
- **接口路径**：`GET /api/admin/users`
- **请求头**：`Authorization: Bearer <Token>` (必须拥有 ROOT 角色)
- **请求 Query 参数说明**：`pageNum` (默认1), `pageSize` (默认10), `keyword` (可选，搜索用户名)
- **响应数据说明**：返回包含用户 `id`, `username`, `role`, `createdAt` 的分页列表。

---

### 1.6 修改用户角色权限 (需 ROOT 权限)
- **接口路径**：`PUT /api/admin/users/{id}/role`
- **请求头**：`Authorization: Bearer <Token>` (必须拥有 ROOT 角色)
- **请求参数说明 (JSON Body)**：

| 名称 | 类型 | 是否必须 | 默认值 | 备注 |
| :--- | :--- | :--- | :--- | :--- |
| role | string | 必须 | 无 | 目标角色: USER / ADMIN |

- **说明**：将指定用户提权或降权。严禁修改 ROOT 自身或 1 号元老账号。
---

## 2. 题目模块接口 (Problem)

### 2.1 获取题目列表 (支持分页与标题搜索)
- **接口路径**：`GET /api/problems`
- **请求 Query 参数说明**：

| 名称 | 类型 | 是否必须 | 默认值 | 备注 |
| :--- | :--- | :--- | :--- | :--- |
| pageNum | number | 非必须 | 1 | 当前页码 |
| pageSize | number | 非必须 | 10 | 每页展示条数 |
| keyword | string | 非必须 | 无 | 按题目标题模糊搜索 (可选) |

- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码：0-成功 |
| data | object | 必须 | 分页结果 |
| \|- total | number | 必须 | 符合条件的总题目数 |
| \|- items | array | 必须 | 当前页题目简略列表 (不含 description 大文本) |

---

### 2.2 获取题目详细内容 (题面)
- **接口路径**：`GET /api/problems/{id}`
- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码 |
| data | object | 必须 | 题目完整详情 |
| \|- id | number | 必须 | 题目 ID |
| \|- title | string | 必须 | 题目标题 |
| \|- description | string | 必须 | 完整 Markdown 题面 |
| \|- timeLimitMs | number | 必须 | 时限 |
| \|- memoryLimitMb | number | 必须 | 内存限额 |

---

### 2.3 管理员创建题目与上传测试用例 (带 ZIP 自动解压)
- **接口路径**：`POST /api/problems`
- **请求头**：`Authorization: Bearer <Token>` (需管理员权限，目前任意登录用户可用)
- **请求数据类型**：`multipart/form-data`
- **说明**：上传题面信息与测试用例压缩包。压缩包内需包含成对的 `1.in`, `1.ans`, `2.in`, `2.ans` 等文件，后端将自动解压到评测机题目目录。

- **请求表单参数说明 (Form Data)**：

| 名称            | 类型     | 是否必须 | 默认值  | 备注                    |
|:--------------|:-------|:-----|:-----|:----------------------|
| title         | string | 必须   | 无    | 题目标题                  |
| description   | string | 必须   | 无    | Markdown 格式的完整题面描述    |
| timeLimitMs   | number | 必须   | 1000 | 时间限制 (ms)             |
| memoryLimitMb | number | 必须   | 256  | 内存限制 (MB)             |
| isPublic      | number | 非必须  | 1    | 是否公开：1-公开到题库，0-比赛保密新题 |
| file          | file   | 必须   | 无    | 测试用例压缩包 (.zip 文件)     |

- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码：0-成功，1-失败 |
| message | string | 必须 | 提示信息 (如 "题目创建成功，解压测试点完成") |
| data | object | 必须 | 创建成功的题目元数据实体 |

### 2.4 管理员获取全量题目列表 (含保密题)
- **接口路径**：`GET /api/admin/problems`
- **请求头**：`Authorization: Bearer <Token>` (需 ADMIN 或 ROOT)
- **请求 Query 参数说明**：`pageNum` (默认1), `pageSize` (默认10), `keyword` (可选，搜索标题)
- **响应数据说明**：返回所有题目的分页列表，包含 `is_public` 状态（1-公开，0-保密）。

---

### 2.5 管理员一键切换题目可见性 (公开/保密)
- **接口路径**：`PUT /api/admin/problems/{id}/visibility`
- **请求头**：`Authorization: Bearer <Token>` (需 ADMIN 或 ROOT)
- **请求 Query 参数说明**：

| 名称 | 类型 | 是否必须 | 默认值 | 备注 |
| :--- | :--- | :--- | :--- | :--- |
| isPublic | number | 必须 | 无 | 目标状态：1-公开到题库，0-设为比赛保密 |

- **响应数据说明**：`code: 0, message: "操作成功"`

---

### 2.6 管理员编辑修改题目基本信息
- **接口路径**：`PUT /api/admin/problems/{id}`
- **请求头**：`Authorization: Bearer <Token>` (需 ADMIN 或 ROOT)
- **请求参数说明 (JSON Body)**：

| 名称 | 类型 | 是否必须 | 默认值 | 备注 |
| :--- | :--- | :--- | :--- | :--- |
| title | string | 必须 | 无 | 修改后的标题 |
| description | string | 必须 | 无 | 修改后的 Markdown 题面 |
| timeLimitMs | number | 必须 | 1000 | 时间限制 (ms) |
| memoryLimitMb | number | 必须 | 256 | 内存限制 (MB) |

## 3. 评测与提交接口 (Submission)

### 3.1 提交代码评测 (异步任务分发)
- **接口路径**：`POST /api/submissions`
- **请求头**：`Authorization: Bearer <Token>`
- **请求参数说明 (JSON)**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| problemId | number | 必须 | 目标题目 ID (如 1001) |
| language | string | 必须 | 语言类型: "cpp" / "c" |
| code | string | 必须 | 源代码 |

- **响应数据说明 (提交瞬间返回，仅含排队状态与任务 ID)**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码：0-成功 |
| message | string | 必须 | 提示信息 ("提交成功，正在排队评测") |
| data | object | 必须 | 提交任务初始实体 |
| \|- id | number | 必须 | 统一主键 ID (即提交编号，用于后续轮询) |
| \|- problemId | number | 必须 | 题目 ID |
| \|- verdict | string | 必须 | 状态：**`PENDING`** |
| \|- createdAt | string | 必须 | 提交时间 |

- **频控限制**：同一个账号 **5 秒内仅允许提交一次**，超出将返回 `code: 1, message: "提交过于频繁，请等待 5 秒后再试！"`
---

### 3.2 查询单次提交记录详情 (评测最终报告在这里！)
- **接口路径**：`GET /api/submissions/{id}`
- **说明**：轮询或查看某次提交的完整结果（包含代码、最终时空占用、各测试点明细）。
- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码 |
| data | object | 必须 | 完整提交评测报告 |
| \|- id | number | 必须 | 提交记录 ID |
| \|- userId | number | 必须 | 提交者用户 ID |
| \|- problemId | number | 必须 | 题目 ID |
| \|- language | string | 必须 | 语言 |
| \|- code | string | 必须 | 源码 |
| \|- verdict | string | 必须 | 状态 (PENDING / RUNNING / Accepted / ...) |
| \|- maxTimeMs | number | 必须 | 评测完成后有值，未完成为 0 |
| \|- peakMemoryKb | number | 必须 | 评测完成后有值，未完成为 0 |
| \|- judgeDetails | array | 必须 | 评测完成后有值 (各测试点详细明细)，未完成为 [] |
| \|- createdAt | string | 必须 | 提交时间 |
- **响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "id": 10086,
    "userId": 1,
    "problemId": 1001,
    "language": "cpp",
    "code": "#include <iostream>\nint main() { ... }",
    "verdict": "Accepted",
    "maxTimeMs": 2,
    "peakMemoryKb": 3416,
    "judgeDetails": [
      {
        "id": 1,
        "verdict": "Accepted",
        "time_ms": 2,
        "memory_kb": 3416,
        "message": "Correct answer"
      }
    ],
    "createdAt": "2026-10-10 18:30:00"
  }
}
```
#### 3.3 分页查询评测提交记录列表
- **接口路径**：`GET /api/submissions`
- **说明**：综合多条件分页查询评测记录。所有筛选参数均为可选，不传则默认查询全网最新记录；传入 `userId` 可查询某用户的提交，传入 `problemId` 可查询某题目的提交。
- **请求 Query 参数说明**：

| 名称 | 类型 | 是否必须 | 默认值 | 备注 |
| :--- | :--- | :--- | :--- | :--- |
| pageNum | number | 非必须 | 1 | 当前页码 |
| pageSize | number | 非必须 | 10 | 每页展示条数 (最大建议 50) |
| userId | number | 非必须 | 无 | 按提交用户 ID 筛选 |
| problemId | number | 非必须 | 无 | 按题目 ID 筛选 (如 1001) |
| verdict | string | 非必须 | 无 | 按判定结果筛选 (如 "Accepted") |

- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码：0-成功，1-失败 |
| message | string | 必须 | 提示信息 |
| data | object | 必须 | 分页包装对象 |
| \|- total | number | 必须 | 满足条件的总记录数 |
| \|- items | array | 必须 | 当前页的记录列表 (不含大体积的 code 和 details，节省带宽) |
| \|-- id | number | 必须 | 提交记录 ID |
| \|-- userId | number | 必须 | 提交用户 ID |
| \|-- problemId | number | 必须 | 题目 ID |
| \|-- language | string | 必须 | 编程语言 |
| \|-- verdict | string | 必须 | 评测状态 (Accepted, Wrong Answer 等) |
| \|-- maxTimeMs | number | 必须 | 耗时 (ms) |
| \|-- peakMemoryKb | number | 必须 | 峰值内存 (KB) |
| \|-- createdAt | string | 必须 | 提交时间 |

- **响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "total": 42,
    "items": [
      {
        "id": 10086,
        "userId": 1,
        "problemId": 1001,
        "language": "cpp",
        "verdict": "Accepted",
        "maxTimeMs": 2,
        "peakMemoryKb": 3416,
        "createdAt": "2026-10-10 18:30:00"
      },
      {
        "id": 10085,
        "userId": 1,
        "problemId": 1001,
        "language": "cpp",
        "verdict": "Wrong Answer",
        "maxTimeMs": 1,
        "peakMemoryKb": 3416,
        "createdAt": "2026-10-10 18:25:00"
      }
    ]
  }
}
```

## 4. 题解模块接口 (Solution)

### 4.1 获取指定题目的题解列表
- **接口路径**：`GET /api/solutions`
- **请求 Query 参数说明**：

| 名称 | 类型 | 是否必须 | 默认值 | 备注 |
| :--- | :--- | :--- | :--- | :--- |
| problemId | number | 必须 | 无 | 目标题目 ID (如 1001) |
| pageNum | number | 非必须 | 1 | 页码 |
| pageSize | number | 非必须 | 10 | 每页数量 |

- **响应数据说明**：返回当前题目的题解列表（不含大文本 content，带作者用户名与点赞数）。

---

### 4.2 获取题解详情 (正文阅读)
- **接口路径**：`GET /api/solutions/{id}`
- **说明**：查看某篇题解的完整 Markdown 内容。
- **响应数据说明**：包含 `id`, `problemId`, `title`, `content`, `author`, `likes`, `createdAt`。

---

### 4.3 发布新题解
- **接口路径**：`POST /api/solutions`
- **请求头**：`Authorization: Bearer <Token>` (需登录)
- **请求参数说明 (JSON)**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| problemId | number | 必须 | 所属题目 ID |
| title | string | 必须 | 题解标题 (4~64字符) |
| content | string | 必须 | Markdown 格式的正文内容 |

- **响应数据说明**：返回创建成功的题解对象。

### 4.4 修改/编辑题解
- **接口路径**：`PUT /api/solutions/{id}`
- **请求头**：`Authorization: Bearer <Token>` (需登录，且仅允许作者本人或 ADMIN 修改)
- **请求参数说明 (JSON)**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| title | string | 必须 | 修改后的标题 (4~64字符) |
| content | string | 必须 | 修改后的 Markdown 正文 |

- **响应数据说明**：返回修改后的题解对象。

---

### 4.5 删除题解 (逻辑软删除)
- **接口路径**：`DELETE /api/solutions/{id}`
- **请求头**：`Authorization: Bearer <Token>` (需登录，且仅允许作者本人或 ADMIN 删除)
- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码: 0-成功 |
| message | string | 必须 | 提示信息 ("题解已删除") |
| data | null | 必须 | 空 |

## 5. 竞赛模块接口 (Contest)

### 5.1 获取公开比赛列表 (分页)
- **接口路径**：`GET /api/contests`
- **请求头**：无 (公开接口)
- **请求数据类型**：无 (Query 参数)
- **说明**：分页获取比赛列表大厅数据，返回比赛基本信息，并根据系统当前时间自动计算当前比赛的状态（`NOT_STARTED` 未开始、`RUNNING` 进行中、`ENDED` 已结束）。

- **请求 Query 参数说明**：

| 名称 | 类型 | 是否必须 | 默认值 | 备注 |
| :--- | :--- | :--- | :--- | :--- |
| pageNum | number | 非必须 | 1 | 当前页码 |
| pageSize | number | 非必须 | 10 | 每页展示数量 |

- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码：0-成功，1-失败 |
| message | string | 必须 | 提示信息 |
| data | object | 必须 | 分页包装对象 |
| \|- total | number | 必须 | 总比赛场次 |
| \|- items | array | 必须 | 比赛简要信息列表 |
| \|-- id | number | 必须 | 比赛唯一 ID |
| \|-- title | string | 必须 | 比赛标题 |
| \|-- startTime | string | 必须 | 比赛开始时间 (yyyy-MM-dd HH:mm:ss) |
| \|-- endTime | string | 必须 | 比赛结束时间 (yyyy-MM-dd HH:mm:ss) |
| \|-- status | string | 必须 | 当前状态: NOT_STARTED / RUNNING / ENDED |
| \|-- createdAt | string | 必须 | 比赛创建时间 |

- **响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "total": 1,
    "items": [
      {
        "id": 1,
        "title": "2026 第一届 Core OJ 算法新手对抗赛",
        "startTime": "2026-10-20 19:00:00",
        "endTime": "2026-10-20 21:00:00",
        "status": "NOT_STARTED",
        "createdAt": "2026-10-15 12:00:00"
      }
    ]
  }
}
```

---

### 5.2 获取比赛详情
- **接口路径**：`GET /api/contests/{id}`
- **请求头**：无 (公开接口)
- **请求数据类型**：无 (Path 路径参数)
- **说明**：查看单场比赛的基本说明、规程制度、起止时间与当前比赛状态。

- **请求路径参数说明**：

| 名称 | 类型 | 是否必须 | 默认值 | 备注 |
| :--- | :--- | :--- | :--- | :--- |
| id | number | 必须 | 无 | 目标比赛的唯一 ID |

- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码：0-成功，1-失败 |
| message | string | 必须 | 提示信息 |
| data | object | 必须 | 比赛详细对象 |
| \|- id | number | 必须 | 比赛唯一 ID |
| \|- title | string | 必须 | 比赛标题 |
| \|- description | string | 必须 | 比赛规则与详细规程 (Markdown) |
| \|- startTime | string | 必须 | 开始时间 |
| \|- endTime | string | 必须 | 结束时间 |
| \|- status | string | 必须 | 当前计算状态: NOT_STARTED / RUNNING / ENDED |
| \|- createdAt | string | 必须 | 创建时间 |

- **响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "id": 1,
    "title": "2026 第一届 Core OJ 算法新手对抗赛",
    "description": "### 比赛规则\n1. 采用标准 **ACM/ICPC** 赛制...\n2. 严禁作弊与代码雷同...",
    "startTime": "2026-10-20 19:00:00",
    "endTime": "2026-10-20 21:00:00",
    "status": "NOT_STARTED",
    "createdAt": "2026-10-15 12:00:00"
  }
}
```

---

### 5.3 获取比赛题目列表
- **接口路径**：`GET /api/contests/{id}/problems`
- **请求头**：无
- **请求数据类型**：无 (Path 路径参数)
- **说明**：获取比赛中的题目映射列表（A 题、B 题、C 题...）。**核心安全拦截**：若比赛处于 `NOT_STARTED`（未开始）状态且非管理员访问，后端直接拦截报错 `比赛未开始，题目保密中`。

- **请求路径参数说明**：

| 名称 | 类型 | 是否必须 | 默认值 | 备注 |
| :--- | :--- | :--- | :--- | :--- |
| id | number | 必须 | 无 | 比赛唯一 ID |

- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码：0-成功，1-失败 |
| message | string | 必须 | 提示信息 |
| data | array | 必须 | 比赛内题目映射列表 |
| \|- displayId | string | 必须 | 比赛展示代号 (如 A, B, C...) |
| \|- displayTitle | string | 必须 | 比赛内展示标题 |
| \|- problemId | number | 必须 | 底层题库对应的真实题目 ID |

- **响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": [
    {
      "displayId": "A",
      "displayTitle": "A + B 基础练习",
      "problemId": 1001
    },
    {
      "displayId": "B",
      "displayTitle": "取大数值算法实操",
      "problemId": 1002
    }
  ]
}
```

---

### 5.4 获取比赛单题详情 (题面做题页)
- **接口路径**：`GET /api/contests/{id}/problems/{displayId}`
- **请求头**：无
- **请求数据类型**：无 (Path 路径参数)
- **说明**：比赛开始后，选手通过比赛专属路径阅读某道题的 Markdown 描述与时空限制。

- **请求路径参数说明**：

| 名称 | 类型 | 是否必须 | 默认值 | 备注 |
| :--- | :--- | :--- | :--- | :--- |
| id | number | 必须 | 无 | 比赛 ID |
| displayId | string | 必须 | 无 | 比赛内题号 (如 "A", "B") |

- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码：0-成功，1-失败 |
| message | string | 必须 | 提示信息 |
| data | object | 必须 | 比赛题目详情 |
| \|- displayId | string | 必须 | 比赛题号代号 ("A") |
| \|- displayTitle | string | 必须 | 比赛展示标题 |
| \|- problemId | number | 必须 | 底层真实题目 ID |
| \|- description | string | 必须 | 题目完整 Markdown 描述 |
| \|- timeLimitMs | number | 必须 | 时限 (ms) |
| \|- memoryLimitMb | number | 必须 | 内存限额 (MB) |

- **响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "displayId": "A",
    "displayTitle": "A + B 基础练习",
    "problemId": 1001,
    "description": "### 题目描述\n输入两个整数 a 和 b...",
    "timeLimitMs": 1000,
    "memoryLimitMb": 256
  }
}
```

---

### 5.5 比赛内提交代码评测
- **接口路径**：`POST /api/contests/{id}/submissions`
- **请求头**：`Authorization: Bearer <Token>` (必须登录后提交)
- **请求数据类型**：`application/json`
- **说明**：在比赛中提交代码。后端将强制校验比赛是否在 `RUNNING` 期间（未开始或已结束均拒绝提交），提交记录将自动绑定 `contest_id` 并推入 Redis 异步队列。

- **请求参数说明 (JSON Body)**：

| 名称 | 类型 | 是否必须 | 默认值 | 备注 |
| :--- | :--- | :--- | :--- | :--- |
| problemId | number | 必须 | 无 | 当前提交的底层真实题目 ID |
| language | string | 必须 | 无 | 语言类型 ("cpp" / "c") |
| code | string | 必须 | 无 | 用户提交的源代码 |

- **请求样例**：
```json
{
  "problemId": 1001,
  "language": "cpp",
  "code": "#include <iostream>\nusing namespace std;\nint main() { int a, b; while(cin >> a >> b) cout << a + b << endl; return 0; }"
}
```

- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码：0-成功，1-失败 |
| message | string | 必须 | 提示信息 ("比赛提交成功，正在排队评测") |
| data | object | 必须 | 提交任务初始实体 |
| \|- id | number | 必须 | 本次生成的提交记录 ID |
| \|- problemId | number | 必须 | 题目 ID |
| \|- contestId | number | 必须 | 绑定的所属比赛 ID |
| \|- verdict | string | 必须 | 初始状态：`PENDING` |
| \|- createdAt | string | 必须 | 提交时间 |

- **响应样例**：
```json
{
  "code": 0,
  "message": "比赛提交成功，正在排队评测",
  "data": {
    "id": 10090,
    "problemId": 1001,
    "contestId": 1,
    "verdict": "PENDING",
    "createdAt": "2026-10-20 19:15:30"
  }
}
```

---

### 5.6 获取比赛实时 ICPC 榜单 (滚榜核心)
- **接口路径**：`GET /api/contests/{id}/ranklist`
- **请求头**：无 (公开接口)
- **请求数据类型**：无 (Path 路径参数)
- **说明**：计算并获取当前比赛的 ICPC 实时榜单。严格执行“解题数多优先、罚时少优先、单题首 AC 罚时=时间+(错题数*20)、未过题不计罚时”的国际标准。

- **请求路径参数说明**：

| 名称 | 类型 | 是否必须 | 默认值 | 备注 |
| :--- | :--- | :--- | :--- | :--- |
| id | number | 必须 | 无 | 目标比赛的唯一 ID |

- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码：0-成功，1-失败 |
| message | string | 必须 | 提示信息 |
| data | array | 必须 | 排名选手列表 (已按 ICPC 规则排好序) |
| \|- rank | number | 必须 | 当前排名名次 (1, 2, 3...) |
| \|- userId | number | 必须 | 选手用户 ID |
| \|- username | string | 必须 | 选手用户名 |
| \|- solvedCount | number | 必须 | 该选手累计通过题数 (AC 题数) |
| \|- totalPenalty | number | 必须 | 该选手累计总罚时 (分钟) |
| \|- problemResults | object | 必须 | 各题目的作答情况字典 (Key 为 "A", "B" 等) |
| \|-- {displayId}.solved | boolean | 必须 | 该题是否已经通过 (AC) |
| \|-- {displayId}.timeMin | number | 必须 | 该题首 AC 距离开赛的分钟数 (未过为 0) |
| \|-- {displayId}.wrongTries | number | 必须 | 该题在首 AC 前的错误提交次数 (WA/TLE等) |

- **响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": [
    {
      "rank": 1,
      "userId": 2,
      "username": "coder111",
      "solvedCount": 2,
      "totalPenalty": 85,
      "problemResults": {
        "A": {
          "solved": true,
          "timeMin": 15,
          "wrongTries": 0
        },
        "B": {
          "solved": true,
          "timeMin": 30,
          "wrongTries": 2
        }
      }
    },
    {
      "rank": 2,
      "userId": 5,
      "username": "student_tom",
      "solvedCount": 1,
      "totalPenalty": 25,
      "problemResults": {
        "A": {
          "solved": true,
          "timeMin": 25,
          "wrongTries": 0
        },
        "B": {
          "solved": false,
          "timeMin": 0,
          "wrongTries": 3
        }
      }
    }
  ]
}
```

---

### 5.7 管理员创建比赛 (关联题目并自动发布公告贴)
- **接口路径**：`POST /api/admin/contests`
- **请求头**：`Authorization: Bearer <Token>` (必须拥有 ADMIN 权限)
- **请求数据类型**：`application/json`
- **说明**：创建一场新比赛，并批量绑定题目与展示题号（A, B, C...）。创建成功后，系统将自动在 `tb_post` 讨论表中插入一条关联的置顶赛事公告贴。

- **请求参数说明 (JSON Body)**：

| 名称 | 类型 | 是否必须 | 默认值 | 备注 |
| :--- | :--- | :--- | :--- | :--- |
| title | string | 必须 | 无 | 比赛标题 (4~64字符) |
| description | string | 必须 | 无 | 比赛详细说明规程 (Markdown) |
| startTime | string | 必须 | 无 | 开始时间 (yyyy-MM-dd HH:mm:ss) |
| endTime | string | 必须 | 无 | 结束时间 (必须晚于开始时间) |
| problems | array | 必须 | 无 | 包含的题目列表 (按数组顺序自动赋予 A, B, C...) |
| \|- problemId | number | 必须 | 无 | 底层真实题目 ID |
| \|- displayTitle | string | 必须 | 无 | 比赛内展示的题目标题 |

- **请求样例**：
```json
{
  "title": "2026 第一届 Core OJ 新星选拔赛",
  "description": "### 赛事规程\n本次比赛面向大一新生...",
  "startTime": "2026-10-25 19:00:00",
  "endTime": "2026-10-25 21:00:00",
  "problems": [
    {
      "problemId": 1001,
      "displayTitle": "两数相加进阶题"
    },
    {
      "problemId": 1002,
      "displayTitle": "数值极值对比实操"
    }
  ]
}
```

- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码：0-成功，1-失败 |
| message | string | 必须 | 提示信息 ("比赛创建成功，赛事公告已自动发布") |
| data | object | 必须 | 创建成功的比赛基本信息实体 |

- **响应样例**：
```json
{
  "code": 0,
  "message": "比赛创建成功，赛事公告已自动发布",
  "data": {
    "id": 2,
    "title": "2026 第一届 Core OJ 新星选拔赛",
    "startTime": "2026-10-25 19:00:00",
    "endTime": "2026-10-25 21:00:00",
    "createdAt": "2026-10-16 10:00:00"
  }
}
```

---

### 5.8 管理员赛后一键公开题目至公共题库
- **接口路径**：`POST /api/admin/contests/{id}/publish-problems`
- **请求头**：`Authorization: Bearer <Token>` (必须拥有 ADMIN 权限)
- **请求数据类型**：无 (Path 路径参数)
- **说明**：比赛结束后，管理员一键将该场比赛关联的所有保密题目（`is_public = 0`）一次性更新为公开状态（`is_public = 1`），正式流入题库大厅供赛后自由补题。

- **请求路径参数说明**：

| 名称 | 类型 | 是否必须 | 默认值 | 备注 |
| :--- | :--- | :--- | :--- | :--- |
| id | number | 必须 | 无 | 待归档解封的比赛 ID |

- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码：0-成功，1-失败 |
| message | string | 必须 | 提示信息 ("已成功将本场比赛的 2 道题目开放至公共题库！") |
| data | null | 必须 | 空 |

- **响应样例**：
```json
{
  "code": 0,
  "message": "已成功将本场比赛的 2 道题目开放至公共题库！",
  "data": null
}
```
### 5.9 用户报名参赛
- **接口路径**：`POST /api/contests/{id}/register`
- **请求头**：`Authorization: Bearer <Token>` (必须登录)
- **说明**：用户报名参加指定比赛。比赛结束前均可报名。
- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码: 0-成功, 1-失败 (如 "您已报名过本场比赛") |
| message | string | 必须 | 提示信息 ("报名成功！") |
| data | null | 必须 | 空 |

---

### 5.10 查询当前用户是否已报名比赛
- **接口路径**：`GET /api/contests/{id}/register-status`
- **请求头**：`Authorization: Bearer <Token>` (可选，未登录返回未报名)
- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码 |
| data | boolean | 必须 | 是否已报名 (true / false) |

### 5.11 管理员编辑修改比赛
- **接口路径**：`PUT /api/admin/contests/{id}`
- **请求头**：`Authorization: Bearer <Token>` (需 ADMIN 或 ROOT)
- **请求数据类型**：`application/json`
- **业务安全红线拦截**：
    1. `NOT_STARTED` (未开始)：允许自由修改标题、说明、起止时间与题目列表；
    2. `RUNNING` (进行中)：**严禁修改开始时间与删除题目**，只允许延长结束时间与修改文本说明；
    3. `ENDED` (已结束)：比赛已封存，禁止修改起止时间。

- **请求参数说明 (JSON Body)**：

| 名称 | 类型 | 是否必须 | 默认值 | 备注 |
| :--- | :--- | :--- | :--- | :--- |
| title | string | 必须 | 无 | 比赛标题 |
| description | string | 必须 | 无 | 规程说明 (Markdown) |
| startTime | string | 必须 | 无 | 开始时间 (yyyy-MM-dd HH:mm:ss) |
| endTime | string | 必须 | 无 | 结束时间 (必须晚于开始时间) |
| problems | array | 必须 | 无 | 题目列表 (自动重新排序 A, B, C...) |

---

### 5.12 管理员删除比赛
- **接口路径**：`DELETE /api/admin/contests/{id}`
- **请求头**：`Authorization: Bearer <Token>` (需 ADMIN 或 ROOT)
- **说明**：级联删除比赛及 `tb_contest_problem` 题目映射记录，**绝不连带删除底层的题目资产与测试点**。
- **响应数据说明**：`code: 0, message: "比赛删除成功"`
