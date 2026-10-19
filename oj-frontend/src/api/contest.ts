import request from '../utils/request'
import type { Contest, ContestProblem, ContestRank, PageResult, Submission } from '../types'

// 5.1 获取公开比赛列表 (分页)
export const getContestListApi = (params: { pageNum?: number; pageSize?: number }) => {
  return request.get<any, PageResult<Contest>>('/contests', { params })
}

// 5.2 获取单场比赛详情
export const getContestDetailApi = (id: number) => {
  return request.get<any, Contest>(`/contests/${id}`)
}

// 5.3 获取比赛题目列表 (未开赛时后端会自动拦截报错)
export const getContestProblemsApi = (id: number) => {
  return request.get<any, ContestProblem[]>(`/contests/${id}/problems`)
}

// 5.4 获取比赛单题详情
export const getContestProblemDetailApi = (id: number, displayId: string) => {
  return request.get<any, ContestProblem>(`/contests/${id}/problems/${displayId}`)
}

// 5.5 比赛内提交代码 (打上 contest_id 异步评测)
export const submitContestCodeApi = (contestId: number, data: { problemId: number; language: string; code: string }) => {
  return request.post<any, Submission>(`/contests/${contestId}/submissions`, data)
}

// 5.6 获取比赛实时 ICPC 榜单 (滚榜数据)
export const getContestRanklistApi = (id: number) => {
  return request.get<any, ContestRank[]>(`/contests/${id}/ranklist`)
}

// 5.7 报名比赛
export const registerContestApi = (id: number) => {
  return request.post<any, void>(`/contests/${id}/register`)
}

// 5.8 查询是否已报名
export const getContestRegisterStatusApi = (id: number) => {
  return request.get<any, boolean>(`/contests/${id}/register-status`)
}

// 管理员获取全量比赛列表
export const getAdminContestListApi = (params: { pageNum?: number; pageSize?: number; keyword?: string }) => {
  return request.get<any, PageResult<any>>('/admin/contests', { params })
}

// 管理员创建比赛
export const createContestApi = (data: any) => {
  return request.post<any, any>('/admin/contests', data)
}

// 管理员编辑比赛
export const updateContestApi = (id: number, data: any) => {
  return request.put<any, void>(`/admin/contests/${id}`, data)
}

// 管理员删除比赛
export const deleteContestApi = (id: number) => {
  return request.delete<any, void>(`/admin/contests/${id}`)
}

// 赛后一键公开题目
export const publishContestProblemsApi = (id: number) => {
  return request.post<any, void>(`/admin/contests/${id}/publish-problems`)
}

