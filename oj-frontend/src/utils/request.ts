import axios, { type InternalAxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router/index'
import { useUserStore } from '../stores/user'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000
})

// 统一处理登录失效：清理缓存并携带当前路径跳转登录页
const handleUnauthorized = (msg = '登录状态已失效，请重新登录！') => {
  const userStore = useUserStore()
  userStore.clearUserOptions()
  ElMessage.error(msg)

  const currentPath = router.currentRoute.value.fullPath
  if (currentPath !== '/login') {
    router.push({
      path: '/login',
      query: { redirect: currentPath }
    })
  }
}

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data
    // 业务状态码 0 表示成功
    if (res.code !== 0) {
      // 兼容后端返回 HTTP 200 但业务判定为未登录/凭证过期的情况
      if (res.code === 401 || (res.message && res.message.includes('重新登录'))) {
        handleUnauthorized(res.message)
      } else {
        ElMessage.error(res.message || '系统错误')
      }
      return Promise.reject(new Error(res.message || 'Error'))
    }
    // 直接剥离外层 Result，返回核心 data
    return res.data
  },
  error => {
    // 处理 HTTP 状态码错误 (401, 403, 404, 500 等)
    if (error.response) {
      const status = error.response.status
      const backendMsg = error.response.data?.message

      if (status === 401) {
        handleUnauthorized(backendMsg)
      } else if (status === 403) {
        ElMessage.error(backendMsg || '权限不足，拒绝访问！')
      } else {
        ElMessage.error(backendMsg || `请求失败 (状态码: ${status})`)
      }
    } else {
      ElMessage.error('网络连接超时或后端服务未启动！')
    }
    return Promise.reject(error)
  }
)

export default service