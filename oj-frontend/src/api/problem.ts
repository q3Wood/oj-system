import request from '../utils/request'
import type { Problem, PageResult, ProblemQueryParams } from '../types'

// 2.1 多条件分页获取题目列表 (前台查公开+做题状态，后台自动查全量) -> GET /api/problem/list
export const getProblemListApi = (params: ProblemQueryParams) => {
  return request.get<any, PageResult<Problem>>('/problem/list', { params })
}

// 2.2 根据 ID 获取题目详情 -> GET /api/problem/{id}
export const getProblemDetailApi = (id: number) => {
  return request.get<any, Problem>(`/problem/${id}`)
}

// ==================== 以下为后台管理接口 (/problem/admin/**) ====================

// 2.3 管理员创建题目 (multipart/form-data) -> POST /api/problem/admin
export const createProblemApi = (formData: FormData) => {
  return request.post<any, Problem>('/problem/admin', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 2.4 管理员编辑修改题目 (multipart/form-data) -> PUT /api/problem/admin/{id}
export const updateProblemApi = (id: number, formData: FormData) => {
  return request.put<any, Problem>(`/problem/admin/${id}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 2.5 管理员逻辑软删除题目 -> DELETE /api/problem/admin/{id}
export const deleteProblemApi = (id: number) => {
  return request.delete<any, void>(`/problem/admin/${id}`)
}

// 2.6 管理员快捷切换题目可见性 -> PUT /api/problem/admin/{id}/visibility
export const updateProblemVisibilityApi = (id: number, isPublic: number) => {
  return request.put<any, void>(`/problem/admin/${id}/visibility`, null, {
    params: { isPublic }
  })
}