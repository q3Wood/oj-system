import request from '../utils/request'
import type {User} from '../types'

// 1.1 用户注册
export const userRegisterApi = (data: {username: string, password: string}) => {
    return request.post<any, void>('/user/register', data)
}

// 1.2 用户登录(返回 Token 字符串)
export const userLoginApi = (data: {username: string, password: string}) => {
    return request.post<any, string>('/user/login', data)
}

// 1.3 获取当前登录用户信息
export const getUserInfoApi = () => {
    return request.get<any, User>('/user/info')
}

// 1.4 用户登出
export const userLogoutApi = () => {
    return request.post<any, void>('/user/logout')
}

import type { PageResult } from '../types' // 确保引入了 PageResult

// 1.5 ROOT 获取全量用户列表 (分页 + 模糊搜索)
export const getAdminUserListApi = (params: { pageNum?: number; pageSize?: number; keyword?: string }) => {
  return request.get<any, PageResult<User>>('/admin/users', { params })
}

// 1.6 ROOT 调整用户角色 (USER <-> ADMIN)
export const updateUserRoleApi = (id: number, role: 'USER' | 'ADMIN') => {
  return request.put<any, void>(`/admin/users/${id}/role`, { role })
}

