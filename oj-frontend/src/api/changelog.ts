import request from '../utils/request'
import type { Changelog, ChangelogLatest, ChangelogType, PageResult } from '../types'

// 6.1 分页获取更新日志时间轴列表 -> GET /api/changelog/list
export const getChangelogListApi = (params?: {
  pageNum?: number
  pageSize?: number
  type?: ChangelogType | ''
}) => {
  return request.get<any, PageResult<Changelog>>('/changelog/list', { params })
}

// 6.2 获取最新一条更新日志摘要 (用于导航栏 NEW 角标) -> GET /api/changelog/latest
export const getLatestChangelogApi = () => {
  return request.get<any, ChangelogLatest | null>('/changelog/latest')
}

// 6.3 发布新更新日志 (仅 ROOT) -> POST /api/changelog/admin
export const createChangelogApi = (data: {
  version: string
  title: string
  type: ChangelogType
  content: string
  releaseDate?: string
}) => {
  return request.post<any, Changelog>('/changelog/admin', data)
}

// 6.4 编辑修改更新日志 (仅 ROOT) -> PUT /api/changelog/admin/{id}
export const updateChangelogApi = (
  id: number,
  data: {
    version: string
    title: string
    type: ChangelogType
    content: string
    releaseDate?: string
  }
) => {
  return request.put<any, Changelog>(`/changelog/admin/${id}`, data)
}

// 6.5 删除更新日志 (仅 ROOT) -> DELETE /api/changelog/admin/{id}
export const deleteChangelogApi = (id: number) => {
  return request.delete<any, null>(`/changelog/admin/${id}`)
}