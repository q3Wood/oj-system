import request from '../utils/request'
import type { Submission, PageResult, SubmissionQueryParams } from '../types'

// 3.1 统一提交代码进行评测 (支持日常练习与比赛内提交) -> POST /api/submission
export const submitCodeApi = (data: {
  problemId: number
  contestId?: number
  language: string
  code: string
}) => {
  return request.post<any, Submission>('/submission', data)
}

// 3.2 根据 ID 查询提交记录详情 (含代码与测试点明细) -> GET /api/submission/{id}
export const getSubmissionDetailApi = (id: number) => {
  return request.get<any, Submission>(`/submission/${id}`)
}

// 3.3 分页多条件查询提交记录列表 -> GET /api/submission/list
export const getSubmissionListApi = (params: SubmissionQueryParams) => {
  return request.get<any, PageResult<Submission>>('/submission/list', { params })
}