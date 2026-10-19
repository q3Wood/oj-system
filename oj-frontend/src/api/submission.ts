import request from '../utils/request'
import type { Submission, PageResult } from '../types'

// 3.1 提交代码进行评测
export const submitCodeApi = (data: { problemId: number; language: string; code: string }) => {
  return request.post<any, Submission>('/submissions', data)
}

// 3.2 根据 ID 查询提交记录详情
export const getSubmissionDetailApi = (id: number) => {
  return request.get<any, Submission>(`/submissions/${id}`)
}

// 3.3 分页多条件查询提交记录大厅
export const getSubmissionListApi = (params: {
  pageNum?: number
  pageSize?: number
  userId?: number
  problemId?: number
  verdict?: string
  username?: string
}) => {
  return request.get<any, PageResult<Submission>>('/submissions', { params })
}

