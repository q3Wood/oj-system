export interface Result<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  total: number
  items: T[]
}

export interface User {
  id: number
  username: string
  role: 'USER' | 'ADMIN' | 'ROOT'
  createdAt: string
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
}

export interface CaseDetail {
  id: number
  verdict: string
  time_ms: number
  memory_kb: number
  message: string
}

export interface Submission {
  id: number
  userId: number
  problemId: number
  language: string
  code?: string
  verdict: string
  maxTimeMs: number
  peakMemoryKb: number
  judgeDetails?: CaseDetail[]
  createdAt: string
  username?: string
}

// 7. 题解对象
export interface Solution {
  id: number
  problemId: number
  userId: number
  authorName?: string
  title: string
  content?: string
  likes: number
  createdAt: string
}

// 8. 比赛基本信息
export interface Contest {
  id: number
  title: string
  description: string
  startTime: string
  endTime: string
  status: 'NOT_STARTED' | 'RUNNING' | 'ENDED'
  createdAt: string
}

// 9. 比赛题目映射对象
export interface ContestProblem {
  displayId: string       // "A", "B"...
  displayTitle: string    // 比赛标题
  problemId: number       // 真实 ID
  description?: string    // 题面描述
  timeLimitMs?: number
  memoryLimitMb?: number
}

// 10. ICPC 单题作答结果
export interface ProblemResult {
  solved: boolean
  timeMin: number
  wrongTries: number
}

// 11. ICPC 实时榜单单行选手数据
export interface ContestRank {
  rank: number
  userId: number
  username: string
  solvedCount: number
  totalPenalty: number
  problemResults: Record<string, ProblemResult> // Key 为 "A", "B" 等
}