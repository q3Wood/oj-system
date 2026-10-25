import request from '../utils/request'
import type { Solution, PageResult, SolutionQueryParams } from '../types'

// 5.1 获取某题目的题解列表 -> GET /api/solution/list
export const getSolutionListApi = (params: SolutionQueryParams) => {
  return request.get<any, PageResult<Solution>>('/solution/list', { params })
}

// 5.2 根据 ID 查看题解详情 (Markdown 正文) -> GET /api/solution/{id}
export const getSolutionDetailApi = (id: number) => {
  return request.get<any, Solution>(`/solution/${id}`)
}

// 5.3 发布新题解 -> POST /api/solution
export const createSolutionApi = (data: { problemId: number; title: string; content: string }) => {
  return request.post<any, Solution>('/solution', data)
}

// 5.4 修改题解 -> PUT /api/solution/{id}
export const updateSolutionApi = (id: number, data: { title: string; content: string }) => {
  return request.put<any, Solution>(`/solution/${id}`, data)
}

// 5.5 删除题解 (逻辑软删除) -> DELETE /api/solution/{id}
export const deleteSolutionApi = (id: number) => {
  return request.delete<any, void>(`/solution/${id}`)
}

// 5.6 题解点赞 / 取消点赞切换 -> POST /api/solution/{id}/like
export const toggleLikeSolutionApi = (id: number) => {
  return request.post<any, boolean>(`/solution/${id}/like`)
}