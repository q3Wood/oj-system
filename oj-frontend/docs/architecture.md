# Core OJ 前端架构设计规格书

## 1. 技术栈选型
- **构建工具**：Vite 5 (极速 HMR)
- **核心框架**：Vue 3 (Composition API, `<script setup lang="ts">`)
- **类型系统**：TypeScript 5
- **UI 组件库**：Element Plus (全套深色/浅色企业级组件)
- **状态管理**：Pinia 2 + `pinia-plugin-persistedstate` (自动同步 localStorage)
- **路由管理**：Vue Router 4 (HTML5 History 模式)
- **网络通信**：Axios (封装全局请求/响应拦截器与动态 Token 注入)
- **文本排版**：Marked (支持 GitHub 风格的 Markdown 渲染)

## 2. 路由与权限设计矩阵 (Route Matrix)

| 路由路径 | 对应组件 | 页面名称 | 访问权限 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `/` | - | 重定向 | 公开 | 默认跳转到 `/problems` |
| `/login` | `Login.vue` | 登录/注册 | 公开 | 支持一键切换模式与表单实时校验 |
| `/problems` | `ProblemList.vue` | 题库大厅 | 公开 | 支持分页、题目标题模糊搜索 |
| `/problems/:id` | `ProblemDetail.vue` | 做题工作台 | 公开浏览 | 双栏布局：Markdown题面 + 深色代码编辑器 |
| `/submissions` | `Submissions.vue` | 评测状态大厅 | 公开 | 全网动态流，支持状态/题号筛选与源码查看 |
| `/admin/problem/create` | `AdminProblemCreate.vue` | 出题管理台 | **需登录** | 表单录入题面，支持拖拽上传 ZIP 测试点 |

## 2. 路由与权限设计矩阵 (Route Matrix) - 增加管理后台

| 路由路径 | 对应组件 / 布局 | 说明 | 访问权限限制 |
| :--- | :--- | :--- | :--- |
| `/admin` | `AdminLayout.vue` | **管理后台总骨架** (左侧深色侧边栏 + 顶部栏) | **仅 ADMIN 角色** |
| `/admin/problems` | `AdminProblemList.vue` | 题目全量管理 (出题、公开/保密状态切换) | **仅 ADMIN 角色** |
| `/admin/contests` | `AdminContestList.vue` | 竞赛全量管理 (办赛、编辑时间、赛后一键公开) | **仅 ADMIN 角色** |
| `/admin/users` | `AdminUserList.vue` | 用户管理 (查看全员、授权提权) | **仅 ADMIN 角色** |

### 2.1 全局前置路由守卫 (Global Navigation Guard)
- **守卫机制**：在 `router.beforeEach` 中检查路由元信息 `to.meta.requiresAdmin`。
- **判定规则**：
  1. 检查当前 Pinia `userStore.token` 是否存在；
  2. 检查 `userStore.userInfo.role === 'ADMIN'`；
  3. 若未满足条件，立即终止路由跳转，弹出 Element Plus 警告并重定向回 `/problems` 题库大厅。
## 3. 全局状态存储 (Pinia Store - user.ts)
- `token`: JWT 鉴权凭证，每次发请求前注入 `Authorization: Bearer ${token}`。
- `userInfo`: 当前登录用户的详细对象（ID、用户名、角色）。
- `persist: true`: 持久化到浏览器的 `localStorage`，页面刷新数据不丢失。