import request from '../utils/request'
import type { Solution, PageResult } from '../types'

// 4.1 获取某题目的题解列表
export const getSolutionListApi = (params: { problemId: number; pageNum?: number; pageSize?: number }) => {
  return request.get<any, PageResult<Solution>>('/solutions', { params })
}

// 4.2 根据 ID 查看题解详情 (Markdown 正文)
export const getSolutionDetailApi = (id: number) => {
  return request.get<any, Solution>(`/solutions/${id}`)
}

// 4.3 发布新题解
export const createSolutionApi = (data: { problemId: number; title: string; content: string }) => {
  return request.post<any, Solution>('/solutions', data)
}

// 4.4 修改题解
export const updateSolutionApi = (id: number, data: { title: string; content: string }) => {
  return request.put<any, Solution>(`/solutions/${id}`, data)
}

// 4.5 删除题解
export const deleteSolutionApi = (id: number) => {
  return request.delete<any, void>(`/solutions/${id}`)
}