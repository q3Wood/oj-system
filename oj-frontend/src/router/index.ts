import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '../stores/user'
import { ElMessage } from 'element-plus'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/problems'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/auth/Login.vue')
  
  },
  {
    path: '/problems',
    name: 'Problems',
    component: () => import('../views/problem/ProblemList.vue')
  },
  {
    path: '/problems/:id',
    name: 'ProblemDetail',
    component: () => import('../views/problem/ProblemDetail.vue')
  },
  {
    path: '/submissions',
    name: 'Submissions',
    component: () => import('../views/submission/Submissions.vue')
  },
  {
    path: '/admin/problem/create',
    name: 'AdminProblemCreate',
    component: () => import('../views/problem/AdminProblemCreate.vue')
  },
  {
    path: '/contests',
    name: 'Contests',
    component: () => import('../views/contest/ContestList.vue')
  },
  {
    path: '/contests/:id',
    name: 'ContestDetail',
    component: () => import('../views/contest/ContestDetail.vue')
  },
  // 新增：牛客同款独立全屏比赛做题工作台
  {
    path: '/contests/:id/problems/:displayId',
    name: 'ContestProblemSolve',
    component: () => import('../views/contest/ContestProblemSolve.vue')
  },

  // ================= 后台独立管理系统路由 (受权限保护) =================
  {
    path: '/admin',
    component: () => import('../layout/AdminLayout.vue'),
    redirect: '/admin/problems',
    meta: { requiresAdmin: true },
    children: [
      {
        path: 'problems',
        name: 'AdminProblems',
        component: () => import('../views/admin/AdminProblemList.vue'),
        meta: { requiresAdmin: true }
      },
      {
        path: 'contests',
        name: 'AdminContests',
        component: () => import('../views/admin/AdminContestList.vue'), // 占位，下一步编写竞赛管理
        meta: { requiresAdmin: true }
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('../views/admin/AdminUserList.vue'), // 占位，后续编写用户管理
        meta: { requiresAdmin: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// ================= 【核心安全防线】：全局前置权限路由守卫 =================
router.beforeEach((to, _from, next) => {
  // 如果进入的是需要管理员权限的路径
  if (to.matched.some(record => record.meta.requiresAdmin)) {
    const userStore = useUserStore()

    // 1. 检查是否登录
    if (!userStore.token) {
      ElMessage.warning('请先登录管理员账号！')
      next('/login')
      return
    }

    // 2. 检查角色是否为 ADMIN 或 ROOT
    if (userStore.userInfo?.role !== 'ADMIN' && userStore.userInfo?.role !== 'ROOT') {
      ElMessage.error('权限不足！非管理员严禁进入管理后台！')
      next('/problems') // 踢回题库大厅
      return
    }
  }

  // 其他正常放行
  next()
})

export default router