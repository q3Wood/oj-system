import request from '../utils/request'
import type { User, PageResult, UserAuthDTO, UserQueryParams } from '../types'

// 1.1 用户注册 -> POST /api/user/register
export const userRegisterApi = (data: UserAuthDTO) => {
  return request.post<any, void>('/user/register', data)
}

// 1.2 用户登录 (返回 Token 字符串) -> POST /api/user/login
export const userLoginApi = (data: UserAuthDTO) => {
  return request.post<any, string>('/user/login', data)
}

// 1.3 获取当前登录用户信息 -> GET /api/user/info
export const getUserInfoApi = () => {
  return request.get<any, User>('/user/info')
}

// 1.4 用户登出 -> POST /api/user/logout
export const userLogoutApi = () => {
  return request.post<any, void>('/user/logout')
}

// ==================== 后台管理接口 (/user/admin/**) ====================

// 1.5 ROOT 多条件分页获取用户列表 -> GET /api/user/admin/list
export const getAdminUserListApi = (params: UserQueryParams) => {
  // 注意：依然使用 { params }，Axios 会自动将其序列化为 ?pageNum=1&pageSize=10&keyword=xxx&role=xxx
  return request.get<any, PageResult<User>>('/user/admin/list', { params })
}

// 1.6 ROOT 调整用户角色 (USER <-> ADMIN) -> PUT /api/user/admin/{id}/role
export const updateUserRoleApi = (id: number, role: 'USER' | 'ADMIN') => {
  return request.put<any, void>(`/user/admin/${id}/role`, { role })
}