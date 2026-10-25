import request from '../utils/request'
import type { Contest, ContestProblem, ContestRank, PageResult, ContestQueryParams } from '../types'

// 4.1 分页获取比赛列表 (前台与后台管理合一) -> GET /api/contest/list
export const getContestListApi = (params?: ContestQueryParams) => {
  return request.get<any, PageResult<Contest>>('/contest/list', { params })
}

// 4.2 获取单场比赛详情 (自带 isRegistered 等统计状态) -> GET /api/contest/{id}
export const getContestDetailApi = (id: number) => {
  return request.get<any, Contest>(`/contest/${id}`)
}

// 4.3 获取比赛题目列表 -> GET /api/contest/{id}/problems
export const getContestProblemsApi = (id: number) => {
  return request.get<any, ContestProblem[]>(`/contest/${id}/problems`)
}

// 4.4 获取比赛单题题面详情 -> GET /api/contest/{id}/problems/{displayId}
export const getContestProblemDetailApi = (id: number, displayId: string) => {
  return request.get<any, ContestProblem>(`/contest/${id}/problems/${displayId}`)
}

// 4.5 获取比赛实时 ICPC 榜单 -> GET /api/contest/{id}/ranklist
export const getContestRanklistApi = (id: number) => {
  return request.get<any, ContestRank[]>(`/contest/${id}/ranklist`)
}

// 4.6 用户报名参加比赛 -> POST /api/contest/{id}/register
export const registerContestApi = (id: number) => {
  return request.post<any, void>(`/contest/${id}/register`)
}

// ==================== 以下为后台管理接口 (/contest/admin/**) ====================

// 4.7 管理员创建比赛 -> POST /api/contest/admin
export const createContestApi = (data: any) => {
  return request.post<any, Contest>('/contest/admin', data)
}

// 4.8 管理员编辑比赛 -> PUT /api/contest/admin/{id}
export const updateContestApi = (id: number, data: any) => {
  return request.put<any, void>(`/contest/admin/${id}`, data)
}

// 4.9 管理员删除比赛 -> DELETE /api/contest/admin/{id}
export const deleteContestApi = (id: number) => {
  return request.delete<any, void>(`/contest/admin/${id}`)
}

// 4.10 赛后一键公开保密题到公共题库 -> POST /api/contest/admin/{id}/publish-problems
export const publishContestProblemsApi = (id: number) => {
  return request.post<any, void>(`/contest/admin/${id}/publish-problems`)
}