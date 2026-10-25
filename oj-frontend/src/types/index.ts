export interface PageQueryParams {
  pageNum?: number
  pageSize?: number
}

export interface PageResult<T> {
  total: number
  items: T[]
}

export interface User {
  id: number
  username: string
  role: 'USER' | 'ADMIN' | 'ROOT'
  createdAt?: string
}

export interface UserQueryParams extends PageQueryParams {
  keyword?: string
  role?: 'USER' | 'ADMIN' | 'ROOT' | ''
}

export interface Problem {
  id: number
  title: string
  description?: string
  timeLimitMs: number
  memoryLimitMb: number
  author?: string
  createdAt?: string
  isPublic?: number // 0: 私有, 1: 公开
  userStatus?: 'NOT_STARTED' | 'ATTEMPTED' | 'SOLVED' // 新增：当前用户做题状态
}

export interface ProblemQueryParams extends PageQueryParams {
  id?: number
  title?: string
  author?: string
  isPublic?: number
}

export interface CaseDetail {
  id?: number
  caseId?: number
  verdict: string
  time_ms?: number
  timeMs?: number
  memory_kb?: number
  memoryKb?: number
  message?: string
}

export interface Submission {
  id: number
  userId: number
  username?: string
  problemId: number
  problemTitle?: string
  contestId?: number | null
  language: string
  code?: string
  verdict: string
  maxTimeMs: number
  peakMemoryKb: number
  judgeDetails?: string | CaseDetail[]
  createdAt: string
}

export interface SubmissionQueryParams extends PageQueryParams {
  userId?: number
  username?: string
  problemId?: number
  contestId?: number
  verdict?: string
}

export interface Solution {
  id: number
  problemId: number
  userId: number
  authorName: string
  title: string
  content?: string
  likes: number
  hasLiked?: boolean
  createdAt: string
}

export interface SolutionQueryParams extends PageQueryParams {
  problemId: number
  userId?: number
  keyword?: string
}

export interface Contest {
  id: number
  title: string
  description: string
  startTime: string
  endTime: string
  status: 'NOT_STARTED' | 'RUNNING' | 'ENDED'
  problemCount?: number
  registeredCount?: number
  isRegistered?: boolean
  isAllPublic?: boolean
  createdAt?: string
}

export interface ContestQueryParams extends PageQueryParams {
  keyword?: string
}

export interface ContestProblem {
  displayId: string       // "A", "B"...
  displayTitle: string    // 比赛标题
  problemId: number       // 真实 ID
  description?: string    // 题面描述
  timeLimitMs?: number
  memoryLimitMb?: number
}

export interface ProblemResult {
  solved: boolean
  timeMin: number
  wrongTries: number
}


export interface ContestRank {
  rank: number
  userId: number
  username: string
  solvedCount: number
  totalPenalty: number
  problemResults: Record<string, ProblemResult> // Key 为 "A", "B" 等
}



export interface Result<T> {
  code: number
  message: string
  data: T
}


// 13. 用户登录/注册请求参数 (对应后端的 UserDTO)
export interface UserAuthDTO {
  username: string
  password: string
}



