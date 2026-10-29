<template>
  <div class="contest-solve-page" v-loading="pageLoading">
    <!-- 1. 顶部牛客同款极简黑色导航栏 -->
    <div class="solve-navbar">
      <div class="nav-left">
        <el-link underline="never" :icon="Back" style="color: #a8abb2; margin-right: 14px;" @click="goBackToContest">
          比赛主页
        </el-link>
        <!-- 快速切题下拉框 -->
        <el-dropdown @command="switchProblem">
          <span class="problem-dropdown-btn">
            <el-icon style="margin-right: 6px;"><Menu/></el-icon>
            Problem {{ displayId }}: {{ currentProblem?.displayTitle }}
            <el-icon style="margin-left: 6px;"><ArrowDown/></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="p in problemList"
                :key="p.displayId"
                :command="p.displayId"
                :disabled="p.displayId === displayId"
              >
                Problem {{ p.displayId }}: {{ p.displayTitle }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <div class="nav-right">
        <span class="auto-save-tip">已开启本地实时保存</span>
        <!-- 语言切换 -->
        <el-select
          v-model="selectedLanguage"
          size="small"
          style="width: 115px; margin-right: 12px;"
          @change="loadSavedCode"
        >
          <el-option label="C++ (g++)" value="cpp" />
          <el-option label="C (gcc)" value="c" />
        </el-select>

        <el-button size="small" :icon="Refresh" style="margin-right: 12px;" @click="resetCodeTemplate">
          重置模板
        </el-button>

        <!-- 我的提交记录抽屉入口 -->
        <el-button size="small" :icon="Tickets" style="margin-right: 12px;" @click="openMySubmissionsDrawer">
          我的提交
        </el-button>

        <!-- 提交代码按钮 -->
        <el-button
          type="success"
          size="small"
          :icon="VideoPlay"
          :loading="submitting"
          @click="handleSubmit"
        >
          保存并提交
        </el-button>
      </div>
    </div>

    <!-- 2. 全屏双栏工作台 -->
    <div class="workbench-body">
      <!-- 左半屏：题面描述 -->
      <div class="pane left-pane">
        <div class="pane-inner">
          <div class="problem-header-box">
            <h2 class="problem-title">Problem {{ displayId }}. {{ currentProblem?.displayTitle }}</h2>
            <div class="limit-tags">
              <el-tag type="info" size="small">时间限制: {{ currentProblem?.timeLimitMs }} ms</el-tag>
              <el-tag type="warning" size="small">内存限制: {{ currentProblem?.memoryLimitMb }} MB</el-tag>
            </div>
          </div>
          <!-- 复用通用 Markdown + KaTeX 题面组件 -->
          <ProblemDescription :content="currentProblem?.description"/>
        </div>
      </div>

      <!-- 右半屏：Monaco Editor 满屏编辑器 -->
      <div class="pane right-pane">
        <MonacoEditor :language="selectedLanguage" theme="vs-dark" v-model="sourceCode"/>
      </div>
    </div>

    <!-- 3. 右侧滑出的“我的提交记录”抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="`Problem ${displayId} - 我的提交记录`"
      size="680px"
      direction="rtl"
      destroy-on-close
    >
      <div v-loading="drawerLoading">
        <el-table :data="mySubmissions" style="width: 100%" stripe size="small">
          <el-table-column prop="verdict" label="状态" width="160">
            <template #default="{ row }">
              <el-tag :type="getVerdictTagType(row.verdict)" size="small">
                {{ row.verdict }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="maxTimeMs" label="耗时" width="80" align="center">
            <template #default="{ row }">{{ row.maxTimeMs }} ms</template>
          </el-table-column>
          <el-table-column prop="peakMemoryKb" label="内存" width="90" align="center">
            <template #default="{ row }">{{ row.peakMemoryKb }} KB</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="时间" min-width="140" align="center" />
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openReportDialog(row)">
                查看
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="mySubmissions.length === 0" description="本题暂无提交记录" />
      </div>
    </el-drawer>

    <!-- 4. 右下角极简悬浮评测状态卡片 -->
    <transition name="el-zoom-in-bottom">
      <div v-if="floatingCardVisible" class="floating-judge-card" :class="getFloatingCardClass()">
        <div class="card-left">
          <el-icon v-if="isEvaluating" class="is-loading" :size="20"><Loading/></el-icon>
          <el-icon v-else :size="20">
            <Check v-if="isAccepted(latestSubmission?.verdict)"/>
            <Close v-else/>
          </el-icon>
          <div class="card-info">
            <div class="card-status-title">
              {{ isEvaluating ? '正在后台排队评测中...' : latestSubmission?.verdict }}
            </div>
            <div class="card-sub-info" v-if="!isEvaluating && latestSubmission">
              耗时: {{ latestSubmission.maxTimeMs }}ms | 内存: {{ latestSubmission.peakMemoryKb }}KB
            </div>
          </div>
        </div>
        <div class="card-right">
          <el-button
            v-if="!isEvaluating && latestSubmission"
            link
            :icon="VideoPlay"
            type="primary"
            size="small"
            @click="openReportDialog(latestSubmission)"
          >
            查看报告
          </el-button>
          <el-button link :icon="Close" @click="floatingCardVisible = false" />
        </div>
      </div>
    </transition>

    <!-- 5. 统一复用评测报告弹窗 -->
    <SubmissionDetailModal :submission-id="currentReportSubId" v-model="reportDialogVisible"/>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Menu, ArrowDown, VideoPlay, Refresh, Loading, Check, Close, Tickets, Back } from '@element-plus/icons-vue'
import MonacoEditor from '../../components/MonacoEditor.vue'
import ProblemDescription from '../../components/ProblemDescription.vue'
import SubmissionDetailModal from '../../components/SubmissionDetailModal.vue'
import { getContestProblemsApi, getContestProblemDetailApi } from '../../api/contest'
import { submitCodeApi, getSubmissionListApi, getSubmissionDetailApi } from '../../api/submission'
import { useUserStore } from '../../stores/user'
import type { ContestProblem, Submission } from '../../types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const contestId = Number(route.params.id)
const displayId = ref(route.params.displayId as string)

const pageLoading = ref(false)
const submitting = ref(false)
const currentProblem = ref<ContestProblem | null>(null)
const problemList = ref<ContestProblem[]>([])

const CODE_TEMPLATES: Record<string, string> = {
  cpp: `#include <iostream>\nusing namespace std;\n\nint main() {\n    ios::sync_with_stdio(false);\n    cin.tie(nullptr);\n    // 请在此处完成比赛答题\n    return 0;\n}\n`,
  c: `#include <stdio.h>\n\nint main() {\n    // 请在此处完成比赛答题\n    return 0;\n}\n`
}

const selectedLanguage = ref('cpp')
const sourceCode = ref(CODE_TEMPLATES.cpp)

// 浮窗与评测轮询控制
const floatingCardVisible = ref(false)
const isEvaluating = ref(false)
const latestSubmission = ref<Submission | null>(null)
const reportDialogVisible = ref(false)
const currentReportSubId = ref<number | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null

// 抽屉与提交记录
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const mySubmissions = ref<Submission[]>([])

// 本地代码自动缓存 Key
const getStorageKey = () => {
  const uid = userStore.userInfo?.id || 'guest'
  const pid = currentProblem.value?.problemId || displayId.value
  return `oj:code:${uid}:${pid}:${selectedLanguage.value}`
}

const loadSavedCode = () => {
  const saved = localStorage.getItem(getStorageKey())
  sourceCode.value = saved !== null ? saved : (CODE_TEMPLATES[selectedLanguage.value] || '')
}

watch(sourceCode, (newVal) => {
  if (currentProblem.value) {
    localStorage.setItem(getStorageKey(), newVal)
  }
})

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const openReportDialog = (sub: Submission | null) => {
  if (!sub) return
  currentReportSubId.value = sub.id
  reportDialogVisible.value = true
}

const fetchProblemDetail = async () => {
  pageLoading.value = true
  try {
    currentProblem.value = await getContestProblemDetailApi(contestId, displayId.value)
    loadSavedCode()
  } catch {
  } finally {
    pageLoading.value = false
  }
}

const fetchProblemList = async () => {
  try {
    problemList.value = await getContestProblemsApi(contestId)
  } catch {}
}

const openMySubmissionsDrawer = async () => {
  if (!userStore.token) {
    ElMessage.warning('请先登录查看提交记录！')
    return
  }
  drawerVisible.value = true
  drawerLoading.value = true
  try {
    const data = await getSubmissionListApi({
      pageNum: 1,
      pageSize: 30,
      problemId: currentProblem.value?.problemId,
      userId: userStore.userInfo?.id
    })
    mySubmissions.value = data.items
  } catch {
  } finally {
    drawerLoading.value = false
  }
}

const switchProblem = (newDisplayId: string) => {
  displayId.value = newDisplayId
  router.push(`/contests/${contestId}/problems/${newDisplayId}`)
}

const resetCodeTemplate = async () => {
  try {
    await ElMessageBox.confirm('确定要清空当前代码并恢复初始模板吗？', '重置确认', {
      confirmButtonText: '确定重置',
      cancelButtonText: '取消',
      type: 'warning'
    })
    sourceCode.value = CODE_TEMPLATES[selectedLanguage.value] || ''
    localStorage.removeItem(getStorageKey())
    ElMessage.success('已恢复初始代码模板')
  } catch {}
}

const goBackToContest = () => {
  router.push(`/contests/${contestId}`)
}

const isAccepted = (verdict?: string) => {
  return verdict === 'Accepted' || verdict === 'AC'
}

// 统一调用 submitCodeApi (携带 contestId)
const handleSubmit = async () => {
  if (!userStore.token) {
    ElMessage.warning('请先登录再提交！')
    router.push('/login')
    return
  }
  if (!currentProblem.value) return
  if (!sourceCode.value.trim()) {
    ElMessage.warning('提交代码不能为空！')
    return
  }

  stopPolling()
  submitting.value = true
  isEvaluating.value = true
  floatingCardVisible.value = true

  try {
    const initial = await submitCodeApi({
      problemId: currentProblem.value.problemId,
      contestId: contestId,
      language: selectedLanguage.value,
      code: sourceCode.value
    })

    latestSubmission.value = initial

    pollTimer = setInterval(async () => {
      try {
        const detail = await getSubmissionDetailApi(initial.id)
        latestSubmission.value = detail

        if (detail.verdict !== 'PENDING' && detail.verdict !== 'RUNNING') {
          stopPolling()
          isEvaluating.value = false
          submitting.value = false

          if (isAccepted(detail.verdict)) {
            ElMessage.success(`🎉 Problem ${displayId.value} 评测通过 (Accepted)！`)
          } else {
            ElMessage.error(`评测未通过: ${detail.verdict}`)
          }
        }
      } catch {
        stopPolling()
        isEvaluating.value = false
        submitting.value = false
      }
    }, 800)
  } catch {
    submitting.value = false
    isEvaluating.value = false
    floatingCardVisible.value = false
  }
}

const getFloatingCardClass = () => {
  if (isEvaluating.value) return 'card-pending'
  if (isAccepted(latestSubmission.value?.verdict)) return 'card-accepted'
  return 'card-failed'
}

const getVerdictTagType = (v: string) => {
  if (isAccepted(v)) return 'success'
  if (v === 'Time Limit Exceeded' || v === 'Memory Limit Exceeded') return 'warning'
  return 'danger'
}

watch(
  () => route.params.displayId,
  (newId) => {
    if (newId) {
      displayId.value = newId as string
      fetchProblemDetail()
    }
  }
)

onMounted(() => {
  fetchProblemDetail()
  fetchProblemList()
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<style scoped>
.contest-solve-page {
  height: calc(100% + 40px);
  display: flex;
  flex-direction: column;
  background-color: #1e1e1e;
  overflow: hidden;
  margin: -20px -32px;
}
.solve-navbar {
  height: 48px;
  background-color: #252526;
  border-bottom: 1px solid #333333;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  flex-shrink: 0;
}
.nav-left, .nav-right {
  display: flex;
  align-items: center;
}
.auto-save-tip {
  font-size: 12px;
  color: #909399;
  margin-right: 12px;
}
.problem-dropdown-btn {
  display: flex;
  align-items: center;
  color: #ffffff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  background: #333333;
  padding: 5px 12px;
  border-radius: 4px;
  transition: background 0.2s;
}
.problem-dropdown-btn:hover {
  background: #444444;
}
.workbench-body {
  flex: 1;
  display: flex;
  overflow: hidden;
  height: calc(100% - 48px);
}
.pane {
  flex: 1;
  height: 100%;
}
.left-pane {
  background-color: #ffffff;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
  box-sizing: border-box;
}
.pane-inner {
  padding: 24px 32px 60px 32px;
}
.problem-header-box {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}
.problem-title {
  margin: 0 0 10px 0;
  font-size: 22px;
  color: #303133;
}
.limit-tags {
  display: flex;
  gap: 10px;
}
.right-pane {
  background-color: #1e1e1e;
  overflow: hidden;
}
.floating-judge-card {
  position: fixed;
  right: 32px;
  bottom: 32px;
  min-width: 300px;
  padding: 14px 18px;
  border-radius: 8px;
  background-color: #ffffff;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.2);
  display: flex;
  justify-content: space-between;
  align-items: center;
  z-index: 2000;
}
.card-left { display: flex; align-items: center; gap: 12px; }
.card-info { display: flex; flex-direction: column; }
.card-status-title { font-size: 15px; font-weight: bold; }
.card-sub-info { font-size: 12px; opacity: 0.85; margin-top: 2px; }
.card-right { display: flex; align-items: center; gap: 8px; }
.card-pending { background-color: #ecf5ff; border: 1px solid #d9ecff; color: #409eff; }
.card-accepted { background-color: #f0f9eb; border: 1px solid #e1f3d8; color: #67c23a; }
.card-failed { background-color: #fef0f0; border: 1px solid #fde2e2; color: #f56c6c; }
</style>