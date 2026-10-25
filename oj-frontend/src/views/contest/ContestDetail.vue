<template>
  <div class="nowcoder-contest-container" v-loading="loading">
    <!-- 1. 牛客同款深色头部 Banner -->
    <div class="contest-hero-banner">
      <div class="banner-inner">
        <div class="breadcrumb-nav">
          <el-link :underline="false" :icon="Back" style="color: #909399;" @click="$router.push('/contests')">
            赛事大厅
          </el-link>
          <span class="sep">/</span>
          <span style="color: #c0c4cc;">当前比赛</span>
        </div>

        <div class="banner-main-row">
          <div class="banner-left-info">
            <div class="banner-title-row">
              <h1 class="main-title">{{ contest?.title }}</h1>
              <div class="badge-group">
                <el-tag effect="dark" type="success" size="small">原创</el-tag>
                <el-tag effect="dark" type="warning" size="small">ICPC 赛制</el-tag>
                <el-tag effect="dark" :type="getStatusTagType(contest?.status)" size="small">
                  {{ getStatusText(contest?.status) }}
                </el-tag>
              </div>
            </div>

            <div class="banner-meta-bar">
              <span class="meta-item">时间: <b>{{ contest?.startTime }}</b> 至 <b>{{ contest?.endTime }}</b></span>
              <span class="meta-item">已报名: <b>{{ contest?.registeredCount ?? 0 }} 人</b></span>
              <span class="meta-item countdown-text" v-if="contest?.status === 'RUNNING'">
                距离比赛结束: <b>{{ countdownStr }}</b>
              </span>
              <span class="meta-item countdown-text" v-else-if="contest?.status === 'NOT_STARTED'">
                距离开赛: <b>{{ countdownStr }}</b>
              </span>
            </div>
          </div>

          <!-- 右侧操作区 -->
          <div class="banner-right-action">
            <template v-if="contest?.status !== 'ENDED'">
              <el-button
                v-if="!isRegistered"
                type="primary"
                size="large"
                class="register-btn"
                :loading="registering"
                @click="handleRegister"
              >
                立即报名参赛
              </el-button>
              <div v-else class="registered-badge">
                <el-icon :size="16" style="margin-right: 6px;"><Check/></el-icon>
                已报名参赛
              </div>
            </template>
            <template v-else>
              <div class="ended-badge">比赛已结束 (开放补题)</div>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- 2. 标签导航栏 -->
    <div class="contest-tab-nav">
      <div class="nav-inner">
        <div class="tab-item" :class="{ active: activeTab === 'info' }" @click="handleTabChange('info')">
          比赛说明
        </div>
        <div class="tab-item" :class="{ active: activeTab === 'problems' }" @click="handleTabChange('problems')">
          题目 ({{ contest?.problemCount ?? problemList.length }})
        </div>
        <div class="tab-item" :class="{ active: activeTab === 'ranklist' }" @click="handleTabChange('ranklist')">
          🏆 排名 (ICPC 榜单)
        </div>
      </div>
    </div>

    <!-- 3. 主体内容区域 -->
    <div class="contest-body-wrap">
      <div class="content-main-area">
        <!-- Tab 1: 比赛说明 (复用 ProblemDescription) -->
        <ProblemDescription :content="contest?.description" v-show="activeTab === 'info'"/>

        <!-- Tab 2: 题目列表 (管理员在未开赛时亦可提前预览测试题目) -->
        <div v-show="activeTab === 'problems'" class="problems-table-section">
          <div v-if="contest?.status === 'NOT_STARTED' && !isAdmin" class="locked-tip-box">
            <el-empty description="比赛尚未开始，题目处于严格保密状态，开赛后准时解锁！" />
          </div>
          <div v-else>
            <el-alert
              v-if="contest?.status === 'NOT_STARTED' && isAdmin"
              title="管理员预览模式：比赛尚未开始，您当前以管理员身份提前检阅比赛题目。"
              type="warning"
              show-icon
              :closable="false"
              style="margin-bottom: 16px;"
            />
            <el-table :data="problemList" style="width: 100%" stripe>
              <el-table-column prop="displayId" label="题号" width="100" align="center">
                <template #default="{ row }">
                  <span class="problem-display-id">{{ row.displayId }}</span>
                </template>
              </el-table-column>

              <el-table-column prop="displayTitle" label="标题" min-width="300">
                <template #default="{ row }">
                  <el-link
                    type="primary"
                    :underline="false"
                    class="problem-link"
                    @click="openProblemInNewTab(row.displayId)"
                  >
                    {{ row.displayTitle }}
                  </el-link>
                </template>
              </el-table-column>

              <el-table-column label="作答操作" width="160" align="center">
                <template #default="{ row }">
                  <el-button
                    type="primary"
                    size="small"
                    :icon="EditPen"
                    @click="openProblemInNewTab(row.displayId)"
                  >
                    在新标签页做题
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <!-- Tab 3: ICPC 实时排行榜 -->
        <div v-show="activeTab === 'ranklist'" class="ranklist-section">
          <div class="rank-tools">
            <span class="tip">榜单严格按 ICPC 赛制计算：AC 题数优先，同题数按总罚时升序排序 (每次非 CE 错题罚时 20 分钟)</span>
            <el-button size="small" :icon="Refresh" @click="fetchRanklist">刷新实时榜单</el-button>
          </div>

          <el-table :data="ranklist" border stripe style="width: 100%">
            <el-table-column prop="rank" label="名次" width="80" align="center">
              <template #default="{ row }">
                <div class="rank-badge" :class="'rank-' + row.rank">{{ row.rank }}</div>
              </template>
            </el-table-column>

            <el-table-column prop="username" label="选手" min-width="160">
              <template #default="{ row }">
                <span class="username-text">{{ row.username }}</span>
              </template>
            </el-table-column>

            <el-table-column prop="solvedCount" label="题数" width="90" align="center">
              <template #default="{ row }">
                <b style="color: #67c23a; font-size: 16px;">{{ row.solvedCount }}</b>
              </template>
            </el-table-column>

            <el-table-column prop="totalPenalty" label="总罚时" width="110" align="center">
              <template #default="{ row }">{{ row.totalPenalty }} m</template>
            </el-table-column>

            <el-table-column
              v-for="p in problemList"
              :key="p.displayId"
              :label="p.displayId"
              width="95"
              align="center"
            >
              <template #default="{ row }">
                <div class="problem-cell" :class="getCellClass(row.problemResults?.[p.displayId])">
                  <template v-if="row.problemResults?.[p.displayId]?.solved">
                    <div class="cell-main">
                      +{{ row.problemResults[p.displayId].wrongTries > 0 ? row.problemResults[p.displayId].wrongTries : '' }}
                    </div>
                    <div class="cell-sub">{{ row.problemResults[p.displayId].timeMin }}m</div>
                  </template>
                  <template v-else-if="row.problemResults?.[p.displayId]?.wrongTries > 0">
                    <div class="cell-failed">-{{ row.problemResults[p.displayId].wrongTries }}</div>
                  </template>
                  <template v-else>
                    <span class="cell-empty">-</span>
                  </template>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Check, Back, EditPen } from '@element-plus/icons-vue'
import ProblemDescription from '../../components/ProblemDescription.vue'
import {
  getContestDetailApi,
  getContestProblemsApi,
  getContestRanklistApi,
  registerContestApi
} from '../../api/contest'
import { useUserStore } from '../../stores/user'
import type { Contest, ContestProblem, ContestRank, ProblemResult } from '../../types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const contestId = Number(route.params.id)

const loading = ref(false)
const contest = ref<Contest | null>(null)
const activeTab = ref('info')

const problemList = ref<ContestProblem[]>([])
const ranklist = ref<ContestRank[]>([])

const isRegistered = ref(false)
const registering = ref(false)

const isAdmin = computed(() => {
  const role = userStore.userInfo?.role
  return role === 'ADMIN' || role === 'ROOT'
})

const countdownStr = ref('00:00:00')
let countdownTimer: ReturnType<typeof setInterval> | null = null

const fetchContestDetail = async () => {
  loading.value = true
  try {
    const data = await getContestDetailApi(contestId)
    contest.value = data
    isRegistered.value = !!data.isRegistered
    startCountdown()
    if (data.status !== 'NOT_STARTED' || isAdmin.value) {
      fetchProblems()
    }
  } catch {
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  if (!userStore.token) {
    ElMessage.warning('请先登录再报名参赛！')
    router.push('/login')
    return
  }

  registering.value = true
  try {
    await registerContestApi(contestId)
    ElMessage.success('🎉 报名成功！祝你比赛取得优异成绩！')
    isRegistered.value = true
    if (contest.value) {
      contest.value.registeredCount = (contest.value.registeredCount || 0) + 1
    }
    if (contest.value?.status !== 'NOT_STARTED' || isAdmin.value) {
      fetchProblems()
    }
  } catch {
  } finally {
    registering.value = false
  }
}

const startCountdown = () => {
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    if (!contest.value) return
    const now = Date.now()
    const target =
      contest.value.status === 'NOT_STARTED'
        ? new Date(contest.value.startTime).getTime()
        : new Date(contest.value.endTime).getTime()

    const diff = target - now
    if (diff <= 0) {
      countdownStr.value = contest.value.status === 'NOT_STARTED' ? '已开赛' : '已结束'
      if (countdownTimer) clearInterval(countdownTimer)
      fetchContestDetail()
      return
    }
    const h = Math.floor(diff / (1000 * 60 * 60))
    const m = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))
    const s = Math.floor((diff % (1000 * 60)) / 1000)
    countdownStr.value = `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
  }, 1000)
}

const fetchProblems = async () => {
  try {
    problemList.value = await getContestProblemsApi(contestId)
  } catch {}
}

const fetchRanklist = async () => {
  try {
    ranklist.value = await getContestRanklistApi(contestId)
  } catch {}
}

const handleTabChange = (tab: string) => {
  activeTab.value = tab
  if (tab === 'problems' && problemList.value.length === 0) {
    if (contest.value?.status !== 'NOT_STARTED' || isAdmin.value) {
      fetchProblems()
    }
  } else if (tab === 'ranklist') {
    if (problemList.value.length === 0 && (contest.value?.status !== 'NOT_STARTED' || isAdmin.value)) {
      fetchProblems()
    }
    fetchRanklist()
  }
}

const openProblemInNewTab = (displayId: string) => {
  const routeData = router.resolve(`/contests/${contestId}/problems/${displayId}`)
  window.open(routeData.href, '_blank')
}

const getCellClass = (res?: ProblemResult) => {
  if (!res) return ''
  if (res.solved) return 'cell-ac-bg'
  if (res.wrongTries > 0) return 'cell-wa-bg'
  return ''
}

const getStatusTagType = (status?: string) => {
  switch (status) {
    case 'RUNNING':
      return 'success'
    case 'NOT_STARTED':
      return 'warning'
    default:
      return 'info'
  }
}

const getStatusText = (status?: string) => {
  switch (status) {
    case 'RUNNING':
      return '进行中'
    case 'NOT_STARTED':
      return '未开始'
    case 'ENDED':
      return '已结束'
    default:
      return '未知'
  }
}

onMounted(() => {
  fetchContestDetail()
})

onBeforeUnmount(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})
</script>

<style scoped>
.nowcoder-contest-container {
  min-height: calc(100vh - 110px);
  background-color: #ffffff;
  margin: -24px -40px;
}
.contest-hero-banner {
  background: linear-gradient(135deg, #1f2329 0%, #2d323c 100%);
  color: #fff;
  padding: 24px 48px;
  border-bottom: 1px solid #3d4450;
}
.banner-inner {
  max-width: 1400px;
  margin: 0 auto;
}
.breadcrumb-nav {
  display: flex;
  align-items: center;
  font-size: 13px;
  margin-bottom: 12px;
}
.sep {
  margin: 0 8px;
  color: #606266;
}
.banner-main-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
}
.banner-left-info {
  flex: 1;
}
.banner-title-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
}
.main-title {
  margin: 0;
  font-size: 26px;
  font-weight: bold;
  letter-spacing: 0.5px;
}
.badge-group {
  display: flex;
  gap: 8px;
}
.banner-meta-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 28px;
  font-size: 14px;
  color: #a8abb2;
}
.countdown-text b {
  color: #e6a23c;
  font-family: monospace;
  font-size: 16px;
}
.banner-right-action {
  flex-shrink: 0;
}
.register-btn {
  font-size: 16px;
  font-weight: bold;
  height: 44px;
  padding: 0 28px;
  border-radius: 8px;
  box-shadow: 0 4px 14px rgba(64, 158, 255, 0.4);
}
.registered-badge {
  display: flex;
  align-items: center;
  background-color: rgba(103, 194, 58, 0.15);
  border: 1px solid #67c23a;
  color: #67c23a;
  font-size: 15px;
  font-weight: bold;
  padding: 10px 22px;
  border-radius: 8px;
}
.ended-badge {
  background-color: rgba(144, 147, 153, 0.2);
  border: 1px solid #909399;
  color: #c0c4cc;
  font-size: 14px;
  padding: 10px 20px;
  border-radius: 8px;
}
.contest-tab-nav {
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.04);
}
.nav-inner {
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  padding: 0 48px;
}
.tab-item {
  padding: 16px 24px;
  font-size: 15px;
  font-weight: 500;
  color: #606266;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
}
.tab-item:hover {
  color: #409eff;
}
.tab-item.active {
  color: #409eff;
  border-bottom-color: #409eff;
  font-weight: bold;
}
.contest-body-wrap {
  max-width: 1400px;
  margin: 0 auto;
  padding: 32px 48px;
  background-color: #ffffff;
}
.content-main-area {
  min-height: 480px;
}
.problem-display-id {
  font-size: 16px;
  font-weight: bold;
  color: #409eff;
}
.problem-link {
  font-size: 15px;
  font-weight: 500;
}
.rank-tools {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.tip {
  font-size: 13px;
  color: #909399;
}
.rank-badge {
  font-weight: bold;
  font-size: 14px;
}
.rank-1 { color: #f56c6c; font-size: 17px; }
.rank-2 { color: #e6a23c; font-size: 16px; }
.rank-3 { color: #409eff; font-size: 15px; }
.problem-cell {
  height: 44px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  border-radius: 4px;
}
.cell-ac-bg { background-color: #e1f3d8; color: #67c23a; }
.cell-wa-bg { background-color: #fde2e2; color: #f56c6c; }
.cell-main { font-weight: bold; font-size: 14px; }
.cell-sub { font-size: 11px; }
.cell-failed { font-weight: bold; font-size: 14px; }
.cell-empty { color: #c0c4cc; }
</style>