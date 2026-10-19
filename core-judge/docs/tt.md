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

---

## 2. 题目模块接口 (Problem)

### 2.1 获取题目列表
- **接口路径**：`GET /api/problems`
- **说明**：题库大厅浏览所有题目简略信息。
- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码 |
| data | array | 必须 | 题目简要信息列表 |
| \|- id | number | 必须 | 题目 ID (如 1001) |
| \|- title | string | 必须 | 题目标题 |
| \|- timeLimitMs | number | 必须 | 时限 (ms) |
| \|- memoryLimitMb | number | 必须 | 内存限额 (MB) |

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

## 3. 评测与提交接口 (Submission)

### 3.1 提交代码评测 (核心)
- **接口路径**：`POST /api/submissions`
- **请求头**：`Authorization: Bearer <Token>` (必须登录后提交)
- **请求参数说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| problemId | number | 必须 | 目标题目 ID (如 1001) |
| language | string | 必须 | 语言类型: "cpp" / "c" |
| code | string | 必须 | 用户提交的完整源码文本 |

- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码 |
| message | string | 必须 | 提示信息 |
| data | object | 必须 | 提交与评测总报告 |
| \|- submissionId | number | 必须 | 本次提交生成的自增唯一记录 ID |
| \|- verdict | string | 必须 | 判定结果 (Accepted / Wrong Answer / ...) |
| \|- maxTimeMs | number | 必须 | CPU 耗时 (ms) |
| \|- peakMemoryKb | number | 必须 | 常驻物理内存 (KB) |
| \|- details | array | 必须 | 各测试点细分列表 (支持前端展开查看) |

- **响应样例**：
```json
{
  "code": 0,
  "message": "评测完成",
  "data": {
    "submissionId": 10086,
    "verdict": "Accepted",
    "maxTimeMs": 2,
    "peakMemoryKb": 3416,
    "details": [
      {
        "id": 1,
        "verdict": "Accepted",
        "time_ms": 2,
        "memory_kb": 3416,
        "message": "Correct answer"
      }
    ]
  }
}
```

---

#### 3.2 查询单次提交记录详情
- **接口路径**：`GET /api/submissions/{id}`
- **说明**：通过提交记录 ID（路径参数）回溯查看源码与评测详细报告。
- **请求路径参数说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| id | number | 必须 | 提交记录的唯一 ID（如 10086） |

- **响应数据说明**：

| 名称 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应码：0-成功，1-失败 |
| message | string | 必须 | 提示信息 |
| data | object | 必须 | 提交详情完整对象 |
| \|- id | number | 必须 | 提交记录 ID |
| \|- userId | number | 必须 | 提交者用户 ID |
| \|- problemId | number | 必须 | 对应题目 ID |
| \|- language | string | 必须 | 语言（cpp） |
| \|- code | string | 必须 | 当时提交的完整代码文本 |
| \|- verdict | string | 必须 | 评测状态码（Accepted, Wrong Answer, etc.） |
| \|- maxTimeMs | number | 必须 | 耗时（ms） |
| \|- peakMemoryKb | number | 必须 | 峰值常驻内存（KB） |
| \|- judgeDetails | string/array | 必须 | 详细测试点报告（JSON 结构） |
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
### 3.3 分页查询评测提交记录列表
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