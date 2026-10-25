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
## Core OJ 接口文档 (提交模块 & 竞赛模块)

> **全局状态码约定**：
> - `code: 0`：操作成功
> - `code: 1`：通用业务错误（如参数不合法、题目不存在）
> - `code: 401`：未登录或 Token 失效
> - `code: 403`：权限不足（未开赛看题、偷看进行中比赛的代码、非管理员调用后台接口）
---

## 1. 用户模块接口 (User)

> **模块基础路径**：`/api/user`  
> **后台管理子路径**：`/api/user/admin/**`  
> **全局响应结构**：所有接口统一返回 `Result<T>` 包装结构（`code`: `0` 表示成功，`1` 表示业务失败）。

---

### 1.1 用户注册
- **接口路径**：`POST /api/user/register`
- **请求头 (Headers)**：
  - `Content-Type`: `application/json`
- **请求参数说明 (JSON Body - `UserDTO`)**：

| 参数名 | 类型 | 是否必须 | 默认值 | 校验规则与备注 |
| :--- | :--- | :--- | :--- | :--- |
| username | string | 必须 | 无 | 用户名，长度 `4 ~ 16` 位非空字符 |
| password | string | 必须 | 无 | 登录密码，长度 `5 ~ 16` 位非空字符 |

- **请求样例**：
```json
{
  "username": "zhangsan",
  "password": "password123"
}
```

- **响应数据说明 (`Result<Void>`)**：

| 字段名 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应状态码：`0`-成功，`1`-失败 |
| message | string | 必须 | 提示信息 |
| data | null | 必须 | 注册接口无业务数据返回，固定为 `null` |

- **成功响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": null
}
```

- **失败响应样例 (用户名冲突 / 达到上限 / 参数校验不通过)**：
```json
{
  "code": 1,
  "message": "用户名已被注册！",
  "data": null
}
```

---

### 1.2 用户登录
- **接口路径**：`POST /api/user/login`
- **请求头 (Headers)**：
  - `Content-Type`: `application/json`
- **请求参数说明 (JSON Body - `UserDTO`)**：

| 参数名 | 类型 | 是否必须 | 默认值 | 校验规则与备注 |
| :--- | :--- | :--- | :--- | :--- |
| username | string | 必须 | 无 | 用户名，长度 `4 ~ 16` 位非空字符 |
| password | string | 必须 | 无 | 登录密码，长度 `5 ~ 16` 位非空字符 |

- **请求样例**：
```json
{
  "username": "zhangsan",
  "password": "password123"
}
```

- **响应数据说明 (`Result<String>`)**：

| 字段名 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应状态码：`0`-成功，`1`-失败 |
| message | string | 必须 | 提示信息 |
| data | string | 必须 | **JWT Token 字符串**。前端需保存该凭证，并在后续请求的 Header 中携带：`Authorization: Bearer <Token>` |

- **成功响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjIsInVzZXJuYW1lIjoiemhhbmdzYW4iLCJyb2xlIjoiVVNFUiJ9.xxx"
}
```

- **失败响应样例**：
```json
{
  "code": 1,
  "message": "用户名或密码错误！",
  "data": null
}
```

---

### 1.3 获取当前登录用户信息
- **接口路径**：`GET /api/user/info`
- **请求头 (Headers)**：

| Header 名称 | 类型 | 是否必须 | 示例值与说明 |
| :--- | :--- | :--- | :--- |
| Authorization | string | 必须 | `Bearer eyJhbGciOiJIUzI1Ni...` (注意 `Bearer` 后有一个空格) |

- **请求参数说明**：无 (后端直接从 Token 上下文中解析当前用户 ID)
- **响应数据说明 (`Result<UserVO>`)**：

| 字段名 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应状态码：`0`-成功，`1`-失败 |
| message | string | 必须 | 提示信息 |
| data | object | 必须 | **用户信息视图对象 (`UserVO`)**，已剔除密码字段 |
| ├─ id | number | 必须 | 用户唯一主键 ID (`Long`) |
| ├─ username | string | 必须 | 用户名 |
| ├─ role | string | 必须 | 用户角色枚举：`USER` (普通用户) / `ADMIN` (管理员) / `ROOT` (超级管理员) |
| └─ createdAt | string | 必须 | 注册时间，格式：`yyyy-MM-dd HH:mm:ss` (东八区 GMT+8) |

- **成功响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "id": 1,
    "username": "admin_root",
    "role": "ROOT",
    "createdAt": "2026-07-15 14:30:00"
  }
}
```

- **失败响应样例 (Token 缺失或过期)**：
```json
{
  "code": 1,
  "message": "未登录或登录凭证已过期，请重新登录！",
  "data": null
}
```

---

### 1.4 用户退出登录
- **接口路径**：`POST /api/user/logout`
- **请求头 (Headers)**：

| Header 名称 | 类型 | 是否必须 | 示例值与说明 |
| :--- | :--- | :--- | :--- |
| Authorization | string | 必须 | `Bearer eyJhbGciOiJIUzI1Ni...` |

- **请求参数说明**：无 (主动注销当前会话，后端销毁 Redis 中的 `login:token:{userId}`)
- **响应数据说明 (`Result<Void>`)**：

| 字段名 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应状态码：`0`-成功，`1`-失败 |
| message | string | 必须 | 提示信息 |
| data | null | 必须 | 固定为 `null` |

- **成功响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": null
}
```

---

### 1.5 多条件分页获取用户列表 (需 ROOT 权限)
- **接口路径**：`GET /api/user/admin/list`
- **请求头 (Headers)**：

| Header 名称 | 类型 | 是否必须 | 示例值与说明 |
| :--- | :--- | :--- | :--- |
| Authorization | string | 必须 | `Bearer <Token>` (当前登录用户必须拥有 **`ROOT`** 角色) |

- **请求 Query 参数说明 (`UserQueryDTO`)**：

| 参数名 | 类型 | 是否必须 | 默认值 | 校验规则与备注 |
| :--- | :--- | :--- | :--- | :--- |
| pageNum | number | 非必须 | `1` | 当前页码，最小值为 `1` |
| pageSize | number | 非必须 | `10` | 每页显示条数，允许范围 `1 ~ 100` |
| keyword | string | 非必须 | 无 | 用户名搜索关键字 (支持模糊匹配，为空或未传则查全部) |
| role | string | 非必须 | 无 | 按角色精准筛选，可选值：`USER` / `ADMIN` / `ROOT` |

- **请求 URL 样例**：
```http
GET /api/user/admin/list?pageNum=1&pageSize=10&keyword=zhang&role=USER
```

- **响应数据说明 (`Result<PageResult<UserVO>>`)**：

| 字段名 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应状态码：`0`-成功，`1`-失败 |
| message | string | 必须 | 提示信息 |
| data | object | 必须 | **分页结果包装对象 (`PageResult<UserVO>`)** |
| ├─ total | number | 必须 | 满足筛选条件的总记录数 (用于前端分页器计算总页数) |
| └─ records | array | 必须 | 当前页的数据列表 (`List<UserVO>`) |
| &emsp;&emsp;├─ id | number | 必须 | 用户唯一主键 ID (`Long`) |
| &emsp;&emsp;├─ username | string | 必须 | 用户名 |
| &emsp;&emsp;├─ role | string | 必须 | 用户角色：`USER` / `ADMIN` / `ROOT` |
| &emsp;&emsp;└─ createdAt | string | 必须 | 注册时间，格式：`yyyy-MM-dd HH:mm:ss` |

- **成功响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "total": 2,
    "items": [
      {
        "id": 1,
        "username": "root_master",
        "role": "ROOT",
        "createdAt": "2026-07-01 10:00:00"
      },
      {
        "id": 2,
        "username": "zhangsan",
        "role": "USER",
        "createdAt": "2026-07-15 16:20:35"
      }
    ]
  }
}
```

- **失败响应样例 (非 ROOT 用户越权访问)**：
```json
{
  "code": 1,
  "message": "权限不足！只有 ROOT 超级管理员有权查看全员列表。",
  "data": null
}
```

---

### 1.6 修改用户角色权限 (需 ROOT 权限)
- **接口路径**：`PUT /api/user/admin/{id}/role`
- **请求头 (Headers)**：
  - `Authorization`: `Bearer <Token>` (必须拥有 **`ROOT`** 角色)
  - `Content-Type`: `application/json`
- **路径参数说明 (Path Variable)**：

| 参数名 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| id | number | 必须 | 目标用户的唯一主键 ID (如 `/api/user/admin/2/role`) |

- **请求参数说明 (JSON Body - `UserRoleUpdateDTO`)**：

| 参数名 | 类型 | 是否必须 | 默认值 | 校验规则与备注 |
| :--- | :--- | :--- | :--- | :--- |
| role | string | 必须 | 无 | 目标角色，正则限制仅允许传 **`USER`** 或 **`ADMIN`** |

- **业务安全约束**：
  1. 严禁修改当前登录的 `ROOT` 用户自身（防止误操作自降权限）。
  2. 严禁修改 `id = 1` 的系统初始元老账号。
  3. 严禁对其他已处于 `ROOT` 角色的账号进行降权。
  4. **副作用说明**：角色修改成功后，后端会自动销毁目标用户在 Redis 中的 Token，强制其下线重登以刷新权限。

- **请求样例**：
```http
PUT /api/user/admin/2/role
```
```json
{
  "role": "ADMIN"
}
```

- **响应数据说明 (`Result<Void>`)**：

| 字段名 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应状态码：`0`-成功，`1`-失败 |
| message | string | 必须 | 提示信息 |
| data | null | 必须 | 固定为 `null` |

- **成功响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": null
}
```

- **失败响应样例 (触发保护机制)**：
```json
{
  "code": 1,
  "message": "安全拦截：系统初始 1 号 ROOT 账号拥有代码级最高特权，严禁任何降权操作！",
  "data": null
}
```

---

## 2. 题目模块接口 (Problem)

> **模块基础路径**：`/api/problem`  
> **后台管理子路径**：`/api/problem/admin/**`  
> **全局响应结构**：所有接口统一返回 `Result<T>` 包装结构（`code`: `0` 表示成功，`1` 表示业务失败，`401` 表示未登录，`403` 表示无权限）。

---

### 2.1 多条件分页获取题目列表 (前台题库与后台管理合一)
- **接口路径**：`GET /api/problem/list`
- **请求头 (Headers)**：

| Header 名称 | 类型 | 是否必须 | 示例值与说明 |
| :--- | :--- | :--- | :--- |
| Authorization | string | **非必须** | `Bearer <Token>`。<br>1. **未携带 (游客)**：仅返回公开题目 (`isPublic = 1`)，且所有题目的 `userStatus` 固定为 `"NOT_STARTED"`。<br>2. **携带 (普通用户 `USER`)**：仅返回公开题目，并自动填充该用户在每道题上的做题状态 (`SOLVED` / `ATTEMPTED` / `NOT_STARTED`)。<br>3. **携带 (管理员 `ADMIN` / `ROOT`)**：默认返回全量题目（含比赛保密题），并支持通过 `isPublic` 参数筛选公开/保密状态。 |

- **请求 Query 参数说明 (`ProblemQueryDTO`)**：

| 参数名 | 类型 | 是否必须 | 默认值 | 校验规则与备注 |
| :--- | :--- | :--- | :--- | :--- |
| pageNum | number | 非必须 | `1` | 当前页码，最小值为 `1` |
| pageSize | number | 非必须 | `10` | 每页显示条数，允许范围 `1 ~ 100` |
| id | number | 非必须 | 无 | 按题号 ID 精准匹配 (如 `1001`) |
| title | string | 非必须 | 无 | 按题目标题模糊搜索 |
| author | string | 非必须 | 无 | 按出题人模糊搜索 |
| isPublic | number | 非必须 | 无 | 按公开状态筛选 (`1`-公开，`0`-比赛保密)。**注：仅管理员携带 Token 时该筛选才生效，普通用户/游客强制只查 `1`** |

- **请求 URL 样例**：
```http
GET /api/problem/list?pageNum=1&pageSize=10&title=动态规划&isPublic=1
```

- **响应数据说明 (`Result<PageResult<ProblemVO>>`)**：

| 字段名 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应状态码：`0`-成功，`1`-失败 |
| message | string | 必须 | 提示信息 |
| data | object | 必须 | **分页结果包装对象 (`PageResult<ProblemVO>`)** |
| ├─ total | number | 必须 | 符合筛选条件的总题目数 |
| └─ items | array | 必须 | 当前页题目列表 (`List<ProblemVO>`) |
| &emsp;&emsp;├─ id | number | 必须 | 题目唯一 ID (从 `1001` 起步) |
| &emsp;&emsp;├─ title | string | 必须 | 题目标题 |
| &emsp;&emsp;├─ description | null | 必须 | 列表查询为节省带宽，固定不查大文本，值为 `null` |
| &emsp;&emsp;├─ timeLimitMs | number | 必须 | 时间限制 (毫秒 ms) |
| &emsp;&emsp;├─ memoryLimitMb | number | 必须 | 内存限制 (兆字节 MB) |
| &emsp;&emsp;├─ author | string | 必须 | 出题人用户名 |
| &emsp;&emsp;├─ isPublic | number | 必须 | 公开状态：`1`-公开题库，`0`-比赛保密 |
| &emsp;&emsp;├─ userStatus | string | 必须 | 当前用户做题状态枚举：<br>- `NOT_STARTED`：未开始/未提交<br>- `ATTEMPTED`：尝试过但尚未通过<br>- `SOLVED`：已通过 (Accepted) |
| &emsp;&emsp;└─ createdAt | string | 必须 | 创建时间，格式：`yyyy-MM-dd HH:mm:ss` |

- **成功响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "total": 2,
    "items": [
      {
        "id": 1001,
        "title": "A + B Problem",
        "description": null,
        "timeLimitMs": 1000,
        "memoryLimitMb": 256,
        "author": "admin_root",
        "isPublic": 1,
        "userStatus": "SOLVED",
        "createdAt": "2026-07-16 10:00:00"
      },
      {
        "id": 1002,
        "title": "最长上升子序列 (LIS)",
        "description": null,
        "timeLimitMs": 2000,
        "memoryLimitMb": 256,
        "author": "admin_root",
        "isPublic": 1,
        "userStatus": "ATTEMPTED",
        "createdAt": "2026-07-16 11:30:00"
      }
    ]
  }
}
```

---

### 2.2 获取指定题目详细内容 (含 Markdown 题面)
- **接口路径**：`GET /api/problem/{id}`
- **请求头 (Headers)**：
  - `Authorization`: `Bearer <Token>` (**非必须**。若题目为 `isPublic = 0` 的保密题，则必须携带 `ADMIN` 或 `ROOT` 的 Token 才允许访问)
- **路径参数说明 (Path Variable)**：

| 参数名 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| id | number | 必须 | 题目唯一主键 ID (如 `/api/problem/1001`) |

- **响应数据说明 (`Result<ProblemVO>`)**：

| 字段名 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应状态码：`0`-成功，`1`-失败，`403`-保密题越权访问 |
| message | string | 必须 | 提示信息 |
| data | object | 必须 | **题目完整视图对象 (`ProblemVO`)** |
| ├─ id | number | 必须 | 题目唯一 ID |
| ├─ title | string | 必须 | 题目标题 |
| ├─ description | string | 必须 | **完整的 Markdown 格式题面描述** |
| ├─ timeLimitMs | number | 必须 | 时间限制 (ms) |
| ├─ memoryLimitMb | number | 必须 | 内存限制 (MB) |
| ├─ author | string | 必须 | 出题人用户名 |
| ├─ isPublic | number | 必须 | 是否公开：`1`-公开，`0`-比赛保密 |
| ├─ userStatus | string | 必须 | 当前用户做题状态：`NOT_STARTED` / `ATTEMPTED` / `SOLVED` |
| └─ createdAt | string | 必须 | 创建时间 (`yyyy-MM-dd HH:mm:ss`) |

- **成功响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "id": 1001,
    "title": "A + B Problem",
    "description": "### 题目描述\n给定两个整数 $A$ 和 $B$，请计算并输出 $A + B$ 的值。\n\n### 输入格式\n一行两个整数 $A, B$。\n\n### 输出格式\n一个整数表示答案。",
    "timeLimitMs": 1000,
    "memoryLimitMb": 256,
    "author": "admin_root",
    "isPublic": 1,
    "userStatus": "SOLVED",
    "createdAt": "2026-07-16 10:00:00"
  }
}
```

- **失败响应样例 (普通用户直接通过 URL 访问保密题)**：
```json
{
  "code": 403,
  "message": "该题目属于比赛保密题，暂未对公众开放！",
  "data": null
}
```

---

### 2.3 管理员创建题目与上传测试用例 (需 ADMIN / ROOT 权限)
- **接口路径**：`POST /api/problem/admin`
- **请求头 (Headers)**：
  - `Authorization`: `Bearer <Token>` (必须拥有 `ADMIN` 或 `ROOT` 角色)
  - `Content-Type`: `multipart/form-data`
- **功能说明**：上传题目元数据与测试用例 `.zip` 压缩包。压缩包内需包含成对的测试数据（如 `1.in`, `1.ans`/`1.out` 等），后端由 Redis 发号器自动分配题号（从 `1001` 起步）并安全解压至判题机工作目录。
- **请求表单参数说明 (`FormData` - `ProblemCreateDTO`)**：

| 参数名 | 类型 | 是否必须 | 默认值 | 校验规则与备注 |
| :--- | :--- | :--- | :--- | :--- |
| title | string | 必须 | 无 | 题目标题 (非空) |
| description | string | 必须 | 无 | Markdown 格式的完整题面描述 (非空) |
| timeLimitMs | number | 非必须 | `1000` | 时间限制 (ms)，最小值为 `100` |
| memoryLimitMb | number | 非必须 | `256` | 内存限制 (MB)，最小值为 `16` |
| isPublic | number | 非必须 | `1` | 是否公开：`1`-公开到题库，`0`-设为比赛保密题 |
| file | File | 必须 | 无 | 测试用例压缩包 (仅限 `.zip` 格式，内部不可为空) |

- **响应数据说明 (`Result<ProblemVO>`)**：返回创建成功的题目完整信息对象 (`ProblemVO`)。
- **成功响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "id": 1003,
    "title": "线段树区间求和",
    "description": "### 题目描述\n维护一个长度为 $N$ 的序列...",
    "timeLimitMs": 1000,
    "memoryLimitMb": 256,
    "author": "admin_root",
    "isPublic": 1,
    "userStatus": "NOT_STARTED",
    "createdAt": "2026-07-17 09:15:20"
  }
}
```

- **失败响应样例 (压缩包格式错误或为空)**：
```json
{
  "code": 1,
  "message": "测试用例必须是 .zip 压缩包格式！",
  "data": null
}
```

---

### 2.4 管理员编辑修改题目 (支持更新信息、切换公开状态及重传测试点)
- **接口路径**：`PUT /api/problem/admin/{id}`
- **请求头 (Headers)**：
  - `Authorization`: `Bearer <Token>` (必须拥有 `ADMIN` 或 `ROOT` 角色)
  - `Content-Type`: `multipart/form-data`
- **路径参数说明 (Path Variable)**：
  - `id` (number, 必须)：目标题目的唯一主键 ID (如 `/api/problem/admin/1003`)
- **请求表单参数说明 (`FormData` - `ProblemUpdateDTO`)**：

| 参数名 | 类型 | 是否必须 | 默认值 | 校验规则与备注 |
| :--- | :--- | :--- | :--- | :--- |
| title | string | 必须 | 无 | 修改后的题目标题 (非空) |
| description | string | 必须 | 无 | 修改后的 Markdown 题面描述 (非空) |
| timeLimitMs | number | 必须 | 无 | 时间限制 (ms)，最小值为 `100` |
| memoryLimitMb | number | 必须 | 无 | 内存限制 (MB)，最小值为 `16` |
| isPublic | number | 非必须 | 无 | 公开状态：`1`-公开，`0`-比赛保密 (不传则保持原状态不变) |
| file | File | **非必须** | 无 | **可选上传**：若上传新的 `.zip` 压缩包，后端将自动清空该题原有的旧测试点文件并解压新文件；若不传则保留原测试点不变 |

- **响应数据说明 (`Result<ProblemVO>`)**：返回更新后的题目完整视图对象 (`ProblemVO`)。
- **成功响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "id": 1003,
    "title": "线段树区间修改与求和",
    "description": "### 题目描述\n(已修正数据范围)...",
    "timeLimitMs": 1500,
    "memoryLimitMb": 256,
    "author": "admin_root",
    "isPublic": 0,
    "userStatus": "NOT_STARTED",
    "createdAt": "2026-07-17 09:15:20"
  }
}
```

---

### 2.5 管理员删除题目 (逻辑软删除，需 ADMIN / ROOT 权限)
- **接口路径**：`DELETE /api/problem/admin/{id}`
- **请求头 (Headers)**：
  - `Authorization`: `Bearer <Token>` (必须拥有 `ADMIN` 或 `ROOT` 角色)
- **路径参数说明 (Path Variable)**：

| 参数名 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| id | number | 必须 | 要删除的题目唯一主键 ID (如 `/api/problem/admin/1003`) |

- **功能说明**：执行逻辑软删除（将数据库的 `is_deleted` 置为 `1`）。删除后该题目将不再出现在前台题库与后台列表中，但保留底层的历史提交记录关联完整性。
- **响应数据说明 (`Result<Void>`)**：

| 字段名 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应状态码：`0`-成功，`1`-失败 |
| message | string | 必须 | 提示信息 |
| data | null | 必须 | 固定为 `null` |

- **成功响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": null
}
```

- **失败响应样例**：
```json
{
  "code": 1,
  "message": "题目不存在或已被删除！(ID: 1003)",
  "data": null
}
```

---

### 2.6 管理员快捷切换题目可见性
- **接口路径**：`PUT /api/problem/admin/{id}/visibility?isPublic=1`
- **请求头（Headers）**:
  - `Authorization`: `Bearer <Token>` (必须拥有 `ADMIN` 或 `ROOT` 角色)
- **路径参数说明 (Path Variable)**：

| 参数名 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| id | number | 必须 | 题目唯一主键 ID (如 `/api/problem/admin/1003/visibility`) |
|isPublic | number | 必须 | 公开状态切换：`1`-公开，`0`-比赛保密 |

- **功能说明**：修改题目公开状态，管理员可快速切换题目是否对普通用户可见。
- **响应数据说明 (`Result<Void>`)**：

| 字段名 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 响应状态码：`0`-成功，`1`-失败 |
| message | string | 必须 | 提示信息 |
| data | null | 必须 | 固定为 `null` |

- **成功响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": null
}
```

- **失败响应样例**：
```json
{
  "code": 1,
  "message": "题目不存在或已被删除！(ID: 1003)",
  "data": null
}
```

## 3. 提交与评测模块接口 (Submission)

> **基础路径**：`/api/submission`

### 3.1 统一代码提交评测
- **接口路径**：`POST /api/submission`
- **请求头 (Headers)**：
  - `Authorization`: `Bearer <Token>` (**必须登录**)
  - `Content-Type`: `application/json`
- **说明**：全站代码提交的唯一入口。
  1. **日常练习提交**：`contestId` 传 `null` 或不传。若题目为比赛保密题 (`isPublic = 0`) 且非管理员，直接拦截拒绝提交。
  2. **比赛内提交**：传入对应的 `contestId`。若比赛正在进行中 (`RUNNING`)，将校验报名状态并绑定比赛；**若比赛已结束 (`ENDED`)，后端自动将 `contestId` 置为 `null` 平滑转为日常补题练习，不计入比赛榜单**。
  3. **频控限制**：同一用户 5 秒内仅允许提交一次（Redis 锁）。
- **请求参数说明 (JSON Body - `SubmitDTO`)**：

| 参数名 | 类型 | 是否必须 | 默认值 | 校验规则与备注 |
| :--- | :--- | :--- | :--- | :--- |
| problemId | number | 必须 | 无 | 题目唯一 ID |
| contestId | number | 非必须 | 无 | 比赛 ID (日常提交不传，比赛内提交传入) |
| language | string | 必须 | 无 | 编程语言，可选：`cpp` / `c` |
| code | string | 必须 | 无 | 源代码正文 (非空) |

- **请求样例**：
```json
{
  "problemId": 1001,
  "contestId": 1,
  "language": "cpp",
  "code": "#include <iostream>\nusing namespace std;\nint main() {\n    int a, b;\n    while (cin >> a >> b) cout << (a + b) << endl;\n    return 0;\n}"
}
```

- **响应数据说明 (`Result<SubmissionVO>`)**：

| 字段名 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 状态码：`0`-成功 |
| message | string | 必须 | 提示信息 |
| data | object | 必须 | 提交记录视图对象 (`SubmissionVO`) |
| ├─ id | number | 必须 | 本次提交唯一 ID |
| ├─ userId | number | 必须 | 提交者用户 ID |
| ├─ username | string | 必须 | 提交者用户名 |
| ├─ problemId | number | 必须 | 题目 ID |
| ├─ problemTitle | string | 必须 | 题目标题 |
| ├─ contestId | number | 非必须 | 最终绑定的比赛 ID (赛后转补题时为 `null`) |
| ├─ language | string | 必须 | 编程语言 |
| ├─ verdict | string | 必须 | 初始状态固定为 `"PENDING"` (等待评测) |
| ├─ maxTimeMs | number | 必须 | 初始为 `0` |
| ├─ peakMemoryKb | number | 必须 | 初始为 `0` |
| └─ createdAt | string | 必须 | 提交时间 (`yyyy-MM-dd HH:mm:ss`) |

- **成功响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "id": 501,
    "userId": 2,
    "username": "zhangsan",
    "problemId": 1001,
    "problemTitle": "A + B Problem",
    "contestId": 1,
    "language": "cpp",
    "verdict": "PENDING",
    "maxTimeMs": 0,
    "peakMemoryKb": 0,
    "createdAt": "2026-10-24 19:30:00"
  }
}
```

- **失败响应样例 (频率过高)**：
```json
{
  "code": 1,
  "message": "提交过于频繁，请等待 5 秒后再试！",
  "data": null
}
```

---

### 3.2 获取单次提交详情 (含代码与评测报告)
- **接口路径**：`GET /api/submission/{id}`
- **请求头 (Headers)**：
  - `Authorization`: `Bearer <Token>` (**必须登录**)
- **防作弊安全红线**：
  1. 提交者本人与管理员 (`ADMIN`/`ROOT`) 可无条件查看任何时期的完整代码与测试点明细。
  2. **其他用户查看时**：若该提交属于**进行中的比赛**或**未公开的保密题**，后端直接返回 `403` 拒绝访问！日常公开题或已结束比赛的提交允许自由查阅学习。
- **响应数据说明 (`Result<SubmissionVO>`)**：

| 字段名 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 状态码：`0`-成功，`403`-防作弊拦截 |
| data | object | 必须 | 完整提交视图对象 |
| ├─ id | number | 必须 | 提交 ID |
| ├─ userId | number | 必须 | 用户 ID |
| ├─ username | string | 必须 | 用户名 |
| ├─ problemId | number | 必须 | 题目 ID |
| ├─ problemTitle | string | 必须 | 题目标题 |
| ├─ language | string | 必须 | 编程语言 |
| ├─ verdict | string | 必须 | 评测状态：`Accepted` / `Wrong Answer` / `Compile Error` 等 |
| ├─ maxTimeMs | number | 必须 | 最大耗时 (ms) |
| ├─ peakMemoryKb | number | 必须 | 峰值内存 (KB) |
| ├─ code | string | 必须 | 源代码正文 |
| ├─ judgeDetails | string | 必须 | 测试点通过情况明细 (JSON 字符串) 或编译日志 |
| └─ createdAt | string | 必须 | 提交时间 |

- **成功响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "id": 501,
    "userId": 2,
    "username": "zhangsan",
    "problemId": 1001,
    "problemTitle": "A + B Problem",
    "contestId": null,
    "language": "cpp",
    "verdict": "Accepted",
    "maxTimeMs": 12,
    "peakMemoryKb": 1024,
    "code": "#include <iostream>...",
    "judgeDetails": "[{\"caseId\":1,\"verdict\":\"Accepted\",\"timeMs\":2,\"memoryKb\":1024}]",
    "createdAt": "2026-10-24 19:30:00"
  }
}
```

- **失败响应样例 (赛中偷看他人代码)**：
```json
{
  "code": 403,
  "message": "比赛正在进行中，严禁查看其他选手的提交代码与明细！",
  "data": null
}
```

---

### 3.3 分页多条件查询提交记录大厅
- **接口路径**：`GET /api/submission/list`
- **请求头 (Headers)**：
  - `Authorization`: `Bearer <Token>` (**非必须，游客可自由查看大厅列表**)
- **请求 Query 参数说明 (`SubmissionQueryDTO`)**：

| 参数名 | 类型 | 是否必须 | 默认值 | 校验规则与备注 |
| :--- | :--- | :--- | :--- | :--- |
| pageNum | number | 非必须 | `1` | 当前页码 |
| pageSize | number | 非必须 | `10` | 每页条数 (1 ~ 100) |
| userId | number | 非必须 | 无 | 指定用户 ID 筛选 |
| username | string | 非必须 | 无 | 按提交者用户名模糊搜索 |
| problemId | number | 非必须 | 无 | 指定题目 ID 筛选 |
| contestId | number | 非必须 | 无 | 指定比赛 ID 筛选 (不传查日常练习提交) |
| verdict | string | 非必须 | 无 | 按状态筛选 (如 `Accepted`) |

- **响应数据说明 (`Result<PageResult<SubmissionVO>>`)**：

| 字段名 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 状态码：`0`-成功 |
| data | object | 必须 | 分页结果对象 |
| ├─ total | number | 必须 | 总提交记录数 |
| └─ items | array | 必须 | 列表数据 (大厅列表中为了节省带宽，`code` 和 `judgeDetails` 为 `null`) |

- **成功响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "total": 1,
    "items": [
      {
        "id": 501,
        "userId": 2,
        "username": "zhangsan",
        "problemId": 1001,
        "problemTitle": "A + B Problem",
        "contestId": null,
        "language": "cpp",
        "verdict": "Accepted",
        "maxTimeMs": 12,
        "peakMemoryKb": 1024,
        "code": null,
        "judgeDetails": null,
        "createdAt": "2026-10-24 19:30:00"
      }
    ]
  }
}
```

---

## 4. 竞赛模块接口 (Contest)

> **模块基础路径**：`/api/contest`  
> **后台管理子路径**：`/api/contest/admin/**`

### 4.1 分页获取比赛大厅列表 (前后台合一)
- **接口路径**：`GET /api/contest/list`
- **请求头 (Headers)**：
  - `Authorization`: `Bearer <Token>` (**非必须**。游客可看；登录用户自动回显 `isRegistered` 报名状态；管理员额外回显 `isAllPublic`)
- **请求 Query 参数说明 (`ContestQueryDTO`)**：

| 参数名 | 类型 | 是否必须 | 默认值 | 校验规则与备注 |
| :--- | :--- | :--- | :--- | :--- |
| pageNum | number | 非必须 | `1` | 当前页码 |
| pageSize | number | 非必须 | `10` | 每页显示条数 |
| keyword | string | 非必须 | 无 | 按比赛标题模糊搜索 |

- **响应数据说明 (`Result<PageResult<ContestVO>>`)**：

| 字段名 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 状态码：`0`-成功 |
| data | object | 必须 | 分页包装对象 |
| ├─ total | number | 必须 | 总比赛场数 |
| └─ items | array | 必须 | 当前页比赛列表 |
| &emsp;&emsp;├─ id | number | 必须 | 比赛唯一 ID |
| &emsp;&emsp;├─ title | string | 必须 | 比赛标题 |
| &emsp;&emsp;├─ startTime | string | 必须 | 开赛时间 (`yyyy-MM-dd HH:mm:ss`) |
| &emsp;&emsp;├─ endTime | string | 必须 | 结束时间 (`yyyy-MM-dd HH:mm:ss`) |
| &emsp;&emsp;├─ status | string | 必须 | 比赛状态：`NOT_STARTED`(未开赛) / `RUNNING`(进行中) / `ENDED`(已结束) |
| &emsp;&emsp;├─ problemCount | number | 必须 | 包含的题目数量 |
| &emsp;&emsp;├─ registeredCount | number | 必须 | 已报名选手数 |
| &emsp;&emsp;├─ isRegistered | boolean | 必须 | 当前登录用户是否已报名 (游客为 `false`) |
| &emsp;&emsp;└─ isAllPublic | boolean | 必须 | 题目是否已全量公开到公共题库 (管理员关注) |

- **成功响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "total": 1,
    "items": [
      {
        "id": 1,
        "title": "2026年秋季算法迎新赛",
        "description": "标准 ACM/ICPC 赛制...",
        "startTime": "2026-10-25 14:00:00",
        "endTime": "2026-10-25 18:00:00",
        "status": "NOT_STARTED",
        "problemCount": 5,
        "registeredCount": 42,
        "isRegistered": true,
        "isAllPublic": false,
        "createdAt": "2026-10-20 10:00:00"
      }
    ]
  }
}
```

---

### 4.2 获取单场比赛基本详情
- **接口路径**：`GET /api/contest/{id}`
- **请求头 (Headers)**：
  - `Authorization`: `Bearer <Token>` (**非必须**)
- **响应数据说明 (`Result<ContestVO>`)**：返回单场比赛的完整视图对象（字段同 4.1）。

---

### 4.3 获取比赛题目列表 (需开赛且已报名)
- **接口路径**：`GET /api/contest/{id}/problems`
- **请求头 (Headers)**：
  - `Authorization`: `Bearer <Token>` (赛中必须携带已报名账号的 Token；管理员免报名且未开赛亦可查看)
- **业务访问规则**：
  1. **未开赛 (`NOT_STARTED`)**：非管理员直接报错拦截：`"比赛尚未开始，题目保密中！"`。
  2. **进行中 (`RUNNING`)**：非管理员必须已报名，否则拦截：`"您尚未报名本场比赛，请先报名再查看题目！"`。
  3. **已结束 (`ENDED`)**：全网完全自由公开，不设任何拦截。
- **响应数据说明 (`Result<List<ContestProblemVO>>`)**：

| 字段名 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 状态码：`0`-成功 |
| data | array | 必须 | 题目映射列表 |
| ├─ displayId | string | 必须 | 比赛展示题号 (如 `"A"`, `"B"`, `"C"`) |
| ├─ displayTitle | string | 必须 | 比赛中的展示标题 |
| └─ problemId | number | 必须 | 底层真实题目 ID |

- **成功响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": [
    {
      "displayId": "A",
      "displayTitle": "签到求和",
      "problemId": 1001,
      "description": null,
      "timeLimitMs": null,
      "memoryLimitMb": null
    },
    {
      "displayId": "B",
      "displayTitle": "排队取水",
      "problemId": 1002,
      "description": null,
      "timeLimitMs": null,
      "memoryLimitMb": null
    }
  ]
}
```

---

### 4.4 获取比赛单题题面详情
- **接口路径**：`GET /api/contest/{id}/problems/{displayId}`
- **路径参数**：
  - `id` (number, 必须)：比赛 ID
  - `displayId` (string, 必须)：比赛展示题号（如 `A`）
- **权限规则**：同 4.3 题表权限，赛中未报名不可查看。
- **响应数据说明 (`Result<ContestProblemVO>`)**：返回包含完整 `description` Markdown 题面、`timeLimitMs` 时限与 `memoryLimitMb` 内存限制的单题对象。

- **成功响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "displayId": "A",
    "displayTitle": "签到求和",
    "problemId": 1001,
    "description": "### 题目描述\n计算两个数相加...",
    "timeLimitMs": 1000,
    "memoryLimitMb": 256
  }
}
```

---

### 4.5 获取比赛实时 ICPC 榜单 (滚榜数据)
- **接口路径**：`GET /api/contest/{id}/ranklist`
- **请求头 (Headers)**：公开接口，无需 Token。
- **榜单计算引擎规则**：
  1. 仅统计比赛起止时间内的提交记录。
  2. 解题数 (`solvedCount`) 降序排第一关键字，总罚时 (`totalPenalty`) 升序排第二关键字。
  3. **编译错误 (`Compile Error`) 不计入罚时**；每道题首次 AC 之前的每次非 CE 错误提交累加 **20 分钟罚时**；单题一旦 AC 后，后续重复提交自动忽略。
- **响应数据说明 (`Result<List<ContestRankVO>>`)**：

| 字段名 | 类型 | 是否必须 | 备注 |
| :--- | :--- | :--- | :--- |
| code | number | 必须 | 状态码：`0`-成功 |
| data | array | 必须 | 榜单排名列表 |
| ├─ rank | number | 必须 | 实时名次 (1, 2, 3...) |
| ├─ userId | number | 必须 | 选手用户 ID |
| ├─ username | string | 必须 | 选手用户名 |
| ├─ solvedCount | number | 必须 | 总 AC 题数 |
| ├─ totalPenalty | number | 必须 | 累计总罚时 (分钟) |
| └─ problemResults | object | 必须 | 各题作答明细 (Map 结构，Key 为 `"A"`, `"B"` 等) |
| &emsp;&emsp;├─ solved | boolean | 必须 | 是否已 AC |
| &emsp;&emsp;├─ timeMin | number | 必须 | 开赛到首次 AC 的分钟数 |
| &emsp;&emsp;└─ wrongTries | number | 必须 | 首次 AC 前的错误尝试次数 |

- **成功响应样例**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": [
    {
      "rank": 1,
      "userId": 2,
      "username": "zhangsan",
      "solvedCount": 2,
      "totalPenalty": 75,
      "problemResults": {
        "A": {
          "solved": true,
          "timeMin": 15,
          "wrongTries": 0
        },
        "B": {
          "solved": true,
          "timeMin": 40,
          "wrongTries": 1
        }
      }
    }
  ]
}
```

---

### 4.6 用户报名参加比赛
- **接口路径**：`POST /api/contest/{id}/register`
- **请求头 (Headers)**：
  - `Authorization`: `Bearer <Token>` (**必须登录**)
- **说明**：比赛已结束 (`ENDED`) 时禁止报名，提示可直接自由练习；不能重复报名。
- **响应数据说明 (`Result<Void>`)**：`code: 0, message: "操作成功"`。

---

### 4.7 管理员创建比赛 (需 ADMIN / ROOT 权限)
- **接口路径**：`POST /api/contest/admin`
- **请求头 (Headers)**：
  - `Authorization`: `Bearer <Token>` (需 `ADMIN` 或 `ROOT`)
  - `Content-Type`: `application/json`
- **功能特性**：创建比赛主表，批量将选中的题目绑定为 A, B, C, D...，并在社区表 (`tb_post`) 中**自动发布并置顶官方赛事公告贴**！
- **请求参数说明 (JSON Body - `ContestCreateDTO`)**：

| 参数名 | 类型 | 是否必须 | 默认值 | 校验规则与备注 |
| :--- | :--- | :--- | :--- | :--- |
| title | string | 必须 | 无 | 比赛标题 (非空) |
| description | string | 必须 | 无 | 赛事规程与须知 (非空 Markdown) |
| startTime | string | 必须 | 无 | 开赛时间 (`yyyy-MM-dd HH:mm:ss`) |
| endTime | string | 必须 | 无 | 结束时间 (必须晚于开赛时间) |
| problems | array | 必须 | 无 | 绑定题目数组 (至少包含 1 道题) |
| ├─ problemId | number | 必须 | 无 | 底层题目 ID |
| └─ displayTitle | string | 必须 | 无 | 比赛中展示的题目标题 |

- **请求样例**：
```json
{
  "title": "2026年第十届程序设计新生赛",
  "description": "### 竞赛规程\n标准 ACM 赛制...",
  "startTime": "2026-11-01 13:00:00",
  "endTime": "2026-11-01 17:00:00",
  "problems": [
    { "problemId": 1001, "displayTitle": "A + B 进阶版" },
    { "problemId": 1002, "displayTitle": "贪心排队问题" }
  ]
}
```

- **响应数据说明 (`Result<ContestVO>`)**：返回创建成功的比赛信息。

---

### 4.8 管理员编辑修改比赛 (需 ADMIN / ROOT 权限)
- **接口路径**：`PUT /api/contest/admin/{id}`
- **请求头 (Headers)**：
  - `Authorization`: `Bearer <Token>` (需 `ADMIN` 或 `ROOT`)
  - `Content-Type`: `application/json`
- **防作弊业务红线**：
  1. 比赛进行中 (`RUNNING`)：**严禁修改开始时间**（防止榜单罚时错乱），且**只允许延长结束时间**（不可提前截断）。
  2. 比赛已结束 (`ENDED`)：**禁止修改历史起止时间**。
  3. 仅未开赛 (`NOT_STARTED`) 时允许重新编排 A/B/C 题目。
- **请求参数说明 (JSON Body - `ContestUpdateDTO`)**：字段同 4.7 创建比赛。
- **响应数据说明 (`Result<Void>`)**：`code: 0, message: "操作成功"`。

---

### 4.9 管理员删除比赛 (需 ADMIN / ROOT 权限)
- **接口路径**：`DELETE /api/contest/admin/{id}`
- **请求头 (Headers)**：
  - `Authorization`: `Bearer <Token>` (需 `ADMIN` 或 `ROOT`)
- **功能说明**：物理删除比赛主表、级联删除题目映射关系与报名记录。**底层的 `tb_problem` 题目元数据与测试用例毫发无损！**
- **响应数据说明 (`Result<Void>`)**：`code: 0, message: "操作成功"`。

---

### 4.10 赛后一键公开题目到公共题库 (需 ADMIN / ROOT 权限)
- **接口路径**：`POST /api/contest/admin/{id}/publish-problems`
- **请求头 (Headers)**：
  - `Authorization`: `Bearer <Token>` (需 `ADMIN` 或 `ROOT`)
- **功能说明**：一条 SQL 批量将本场比赛绑定的所有题目的 `is_public` 翻转为 `1`，正式下放到题库大厅供全站用户做日常练习与补题。
- **响应数据说明 (`Result<Void>`)**：`code: 0, message: "操作成功"`。

## 5. 题解模块接口 (Solution)

> **基础路径**：`/api/solution`

### 5.1 获取某题目的题解列表 (分页)
- **接口路径**：`GET /api/solution/list`
- **请求头 (Headers)**：
  - `Authorization`: `Bearer <Token>` (**非必须**。游客可看；登录用户自动回显 `hasLiked` 点赞状态)
- **保密题规则**：若所属题目为比赛保密题 (`isPublic = 0`)，非管理员访问返回 `403`。
- **请求 Query 参数说明 (`SolutionQueryDTO`)**：

| 参数名 | 类型 | 是否必须 | 默认值 | 校验规则与备注 |
| :--- | :--- | :--- | :--- | :--- |
| problemId | number | **必须** | 无 | 题目唯一 ID |
| pageNum | number | 非必须 | `1` | 当前页码 |
| pageSize | number | 非必须 | `10` | 每页显示条数 |
| keyword | string | 非必须 | 无 | 题解标题搜索词 |
| userId | number | 非必须 | 无 | 查看特定用户的题解 |

- **成功响应样例 (`Result<PageResult<SolutionVO>>`)**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "total": 1,
    "items": [
      {
        "id": 101,
        "problemId": 1001,
        "userId": 2,
        "authorName": "zhangsan",
        "title": "通俗易懂的位运算求解思路",
        "content": null,
        "likes": 12,
        "hasLiked": true,
        "createdAt": "2026-10-24 16:30:00"
      }
    ]
  }
}
```

---

### 5.2 获取题解详情 (Markdown 正文)
- **接口路径**：`GET /api/solution/{id}`
- **请求头 (Headers)**：
  - `Authorization`: `Bearer <Token>` (**非必须**)
- **保密题规则**：若所属题目为保密题且非管理员，返回 `403`。
- **成功响应样例 (`Result<SolutionVO>`)**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "id": 101,
    "problemId": 1001,
    "userId": 2,
    "authorName": "zhangsan",
    "title": "通俗易懂的位运算求解思路",
    "content": "### 解题思路\n本题通过位运算加法器原理模拟...",
    "likes": 12,
    "hasLiked": true,
    "createdAt": "2026-10-24 16:30:00"
  }
}
```

---

### 5.3 发布新题解
- **接口路径**：`POST /api/solution`
- **请求头 (Headers)**：
  - `Authorization`: `Bearer <Token>` (**必须登录**)
  - `Content-Type`: `application/json`
- **业务约束**：若题目为比赛保密题且非管理员，直接拦截拒绝发布。
- **请求体 (`SolutionCreateDTO`)**：
```json
{
  "problemId": 1001,
  "title": "通俗易懂的位运算求解思路",
  "content": "### 解题思路\n详细解析步骤..."
}
```
- **成功响应样例 (`Result<SolutionVO>`)**：返回包含生成的主键 `id`、作者与发布时间的完整数据。

---

### 5.4 修改题解
- **接口路径**：`PUT /api/solution/{id}`
- **请求头 (Headers)**：
  - `Authorization`: `Bearer <Token>` (必须是题解作者本人或 `ADMIN`/`ROOT`)
- **请求体 (`SolutionUpdateDTO`)**：
```json
{
  "title": "修改后的题解标题",
  "content": "修改后的题解 Markdown 正文..."
}
```
- **成功响应样例 (`Result<SolutionVO>`)**：`code: 0, message: "操作成功"`。

---

### 5.5 删除题解 (逻辑软删除)
- **接口路径**：`DELETE /api/solution/{id}`
- **请求头 (Headers)**：
  - `Authorization`: `Bearer <Token>` (必须是题解作者本人或 `ADMIN`/`ROOT`)
- **成功响应样例 (`Result<Void>`)**：`code: 0, message: "操作成功", data: null`。

---

### 5.6 题解点赞 / 取消点赞切换
- **接口路径**：`POST /api/solution/{id}/like`
- **请求头 (Headers)**：
  - `Authorization`: `Bearer <Token>` (**必须登录**)
- **功能特性**：幂等切换（未点赞则增加点赞并返回 `true`，已点赞则取消并返回 `false`）。
- **成功响应样例 (`Result<Boolean>`)**：
```json
{
  "code": 0,
  "message": "操作成功",
  "data": true
}
```