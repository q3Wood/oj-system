import request from '../utils/request'
import type { Problem, PageResult } from '../types'

// 1. 统一获取题目列表 (前台查公开，后台自动查全量)
export const getProblemListApi = (params: { pageNum?: number; pageSize?: number; keyword?: string; isPublic?: number }) => {
  return request.get<any, PageResult<Problem>>('/problems', { params })
}

// 2. 根据 ID 获取题目详情
export const getProblemDetailApi = (id: number) => {
  return request.get<any, Problem>(`/problems/${id}`)
}

// 3. 管理员创建题目
export const createProblemApi = (formData: FormData) => {
  return request.post<any, Problem>('/problems', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 4. 管理员编辑修改题目 (包含时空限制与公开状态)
export const updateProblemApi = (id: number, data: FormData | object) => {
  const isFormData = data instanceof FormData
  return request.put<any, Problem>(`/problems/${id}`, data, {
    headers: isFormData ? { 'Content-Type': 'multipart/form-data' } : undefined
  })
}