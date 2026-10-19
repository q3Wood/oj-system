<template>
  <div class="contest-solve-page" v-loading="pageLoading">
    <!-- 1. 顶部牛客同款极简黑色导航栏 -->
    <div class="solve-navbar">
      <div class="nav-left">
        <el-link :underline="false" icon="Back" style="color: #a8abb2; margin-right: 14px;" @click="goBackToContest">
          比赛主页
        </el-link>
        <!-- 快速切题下拉框 -->
        <el-dropdown @command="switchProblem">
          <span class="problem-dropdown-btn">
            <el-icon style="margin-right: 6px;"><Menu /></el-icon>
            Problem {{ displayId }}: {{ currentProblem?.displayTitle }}
            <el-icon style="margin-left: 6px;"><ArrowDown /></el-icon>
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
        <!-- 语言切换 -->
        <el-select v-model="selectedLanguage" size="small" style="width: 110px; margin-right: 12px;">
          <el-option label="C++ (g++)" value="cpp" />
          <el-option label="C (gcc)" value="c" />
        </el-select>

        <el-button size="small" icon="Refresh" style="margin-right: 12px;" @click="resetCodeTemplate">
          重置模板
        </el-button>

        <!-- 牛客同款：我的提交记录抽屉入口 -->
        <el-button size="small" icon="Tickets" style="margin-right: 12px;" @click="openMySubmissionsDrawer">
          我的提交
        </el-button>

        <!-- 提交代码按钮 -->
        <el-button 
          type="success" 
          size="small" 
          icon="VideoPlay" 
          :loading="submitting" 
          @click="handleSubmit"
        >
          保存并提交
        </el-button>
      </div>
    </div>

    <!-- 2. 全屏双栏工作台 (锁死视口高度，消灭外层滚动条) -->
    <div class="workbench-body">
      <!-- 左半屏：题面描述 (独立滚动区) -->
      <div class="pane left-pane">
        <div class="pane-inner">
          <div class="problem-header-box">
            <h2 class="problem-title">Problem {{ displayId }}. {{ currentProblem?.displayTitle }}</h2>
            <div class="limit-tags">
              <el-tag type="info" size="small">时间限制: {{ currentProblem?.timeLimitMs }} ms</el-tag>
              <el-tag type="warning" size="small">内存限制: {{ currentProblem?.memoryLimitMb }} MB</el-tag>
            </div>
          </div>
          <!-- 题面 Markdown 正文 -->
          <div class="markdown-body" v-html="renderedDescription"></div>
        </div>
      </div>

      <!-- 右半屏：Monaco Editor 满屏编辑器 -->
      <div class="pane right-pane">
        <MonacoEditor
          v-model="sourceCode"
          :language="selectedLanguage"
          theme="vs-dark"
        />
      </div>
    </div>

    <!-- 3. 牛客同款：右侧滑出的“我的提交记录”抽屉 -->
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
          <el-icon v-if="isEvaluating" class="is-loading" :size="20"><Loading /></el-icon>
          <el-icon v-else :size="20">
            <Check v-if="latestSubmission?.verdict === 'Accepted'" />
            <Close v-else />
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
          <el-button v-if="!isEvaluating" link type="primary" size="small" @click="openReportDialog(latestSubmission)">
            查看报告
          </el-button>
          <el-button link icon="Close" @click="floatingCardVisible = false"></el-button>
        </div>
      </div>
    </transition>

    <!-- 5. 详细评测报告弹窗 -->
    <!-- 统一复用评测报告弹窗 (带代码展示) -->
<SubmissionDetailModal
  v-model="reportDialogVisible"
  :submission-id="currentReportSubId"
/>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Menu, ArrowDown, VideoPlay, Refresh, Loading, Check, Close, Tickets } from '@element-plus/icons-vue'
import { marked } from 'marked'
import MonacoEditor from '../../components/MonacoEditor.vue'
import {
  getContestProblemsApi,
  getContestProblemDetailApi,
  submitContestCodeApi
} from '../../api/contest'
import { getSubmissionListApi, getSubmissionDetailApi } from '../../api/submission'
import { useUserStore } from '../../stores/user'
import type { ContestProblem, Submission, CaseDetail } from '../../types'
import SubmissionDetailModal from '../../components/SubmissionDetailModal.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const contestId = Number(route.params.id)
const displayId = ref(route.params.displayId as string)

const pageLoading = ref(false)
const submitting = ref(false)
const currentProblem = ref<ContestProblem | null>(null)
const problemList = ref<ContestProblem[]>([])

// 语言与编辑器
const selectedLanguage = ref('cpp')
const defaultTemplate = `#include <iostream>\nusing namespace std;\n\nint main() {\n    // 请在此处完成比赛答题\n    return 0;\n}\n`
const sourceCode = ref(defaultTemplate)

// 浮窗与评测状态
const floatingCardVisible = ref(false)
const isEvaluating = ref(false)
const latestSubmission = ref<Submission | null>(null)
const reportDialogVisible = ref(false)
const selectedReport = ref<Submission | null>(null)

// 抽屉与提交记录
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const mySubmissions = ref<Submission[]>([])

const renderedDescription = computed(() => {
  return marked.parse(currentProblem.value?.description || '') as string
})

const currentReportSubId = ref<number | null>(null)

const openReportDialog = (sub: Submission | null) => {
  if (!sub) return
  currentReportSubId.value = sub.id
  reportDialogVisible.value = true
}

const parsedDetails = computed<CaseDetail[]>(() => {
  if (!selectedReport.value?.judgeDetails) return []
  const d = selectedReport.value.judgeDetails
  if (typeof d === 'string') {
    try {
      const p = JSON.parse(d)
      return Array.isArray(p) ? p : []
    } catch {
      return []
    }
  }
  return Array.isArray(d) ? d : []
})

// 获取单题详情
const fetchProblemDetail = async () => {
  pageLoading.value = true
  try {
    currentProblem.value = await getContestProblemDetailApi(contestId, displayId.value)
  } catch (e) {
    ElMessage.error('无法读取题目，请检查比赛是否已开始！')
  } finally {
    pageLoading.value = false
  }
}

// 获取全场题目列表
const fetchProblemList = async () => {
  try {
    problemList.value = await getContestProblemsApi(contestId)
  } catch (e) {}
}

// 打开我的提交抽屉
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
  } finally {
    drawerLoading.value = false
  }
}

// // 打开评测详情弹窗
// const openReportDialog = async (sub: Submission | null) => {
//   if (!sub) return
//   if (!sub.judgeDetails || sub.judgeDetails.length === 0) {
//     try {
//       sub = await getSubmissionDetailApi(sub.id)
//     } catch (e) {}
//   }
//   selectedReport.value = sub
//   reportDialogVisible.value = true
// }

// 快速切题
const switchProblem = (newDisplayId: string) => {
  displayId.value = newDisplayId
  router.push(`/contests/${contestId}/problems/${newDisplayId}`)
}

const resetCodeTemplate = () => {
  sourceCode.value = defaultTemplate
}

const goBackToContest = () => {
  router.push(`/contests/${contestId}`)
}

// 提交比赛代码
const handleSubmit = async () => {
  if (!userStore.token) {
    ElMessage.warning('请先登录再提交！')
    router.push('/login')
    return
  }
  if (!currentProblem.value) return

  submitting.value = true
  isEvaluating.value = true
  floatingCardVisible.value = true

  try {
    const initial = await submitContestCodeApi(contestId, {
      problemId: currentProblem.value.problemId,
      language: selectedLanguage.value,
      code: sourceCode.value
    })

    latestSubmission.value = initial

    // 智能轮询
    const timer = setInterval(async () => {
      try {
        const detail = await getSubmissionDetailApi(initial.id)
        latestSubmission.value = detail

        if (detail.verdict !== 'PENDING' && detail.verdict !== 'RUNNING') {
          clearInterval(timer)
          isEvaluating.value = false
          submitting.value = false

          if (detail.verdict === 'Accepted') {
            ElMessage.success(`🎉 Problem ${displayId.value} 评测通过 (Accepted)！`)
          } else {
            ElMessage.error(`评测未通过: ${detail.verdict}`)
          }
        }
      } catch {
        clearInterval(timer)
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
  if (latestSubmission.value?.verdict === 'Accepted') return 'card-accepted'
  return 'card-failed'
}

const getVerdictClass = (v: string) => {
  if (v === 'Accepted') return 'banner-ac'
  if (v === 'Compile Error') return 'banner-ce'
  return 'banner-wa'
}

const getVerdictTagType = (v: string) => {
  if (v === 'Accepted') return 'success'
  if (v === 'Time Limit Exceeded' || v === 'Memory Limit Exceeded') return 'warning'
  return 'danger'
}

const getErrorMessage = (sub: Submission | null) => {
  if (!sub || !sub.judgeDetails) return '无报错日志'
  let raw = sub.judgeDetails
  if (typeof raw === 'string') {
    try {
      const p = JSON.parse(raw)
      return typeof p === 'string' ? p : JSON.stringify(p, null, 2)
    } catch {
      return raw
    }
  }
  return JSON.stringify(raw, null, 2)
}

watch(() => route.params.displayId, (newId) => {
  if (newId) {
    displayId.value = newId as string
    fetchProblemDetail()
  }
})

onMounted(() => {
  fetchProblemDetail()
  fetchProblemList()
})
</script>

<style scoped>
/* 锁死视口高度：顶部 App.vue 导航栏占约 60px，内边距扣除，精准撑满剩余视口 */
.contest-solve-page {
  height: calc(100vh - 108px);
  display: flex;
  flex-direction: column;
  background-color: #1e1e1e;
  overflow: hidden; /* 彻底消灭最外层双重滚动条！ */
  margin: -24px -40px; /* 抵消 App.vue main 的内边距 */
}

/* 顶部牛客同款极简黑色导航栏 */
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

/* 双栏工作台：严格填满视口剩余高度 */
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

/* 左侧题面：专属垂直滚动区，平滑顺畅滑到底 */
.left-pane {
  background-color: #ffffff;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
  box-sizing: border-box;
}
.pane-inner {
  padding: 24px 32px 60px 32px; /* 底部留白充足，绝对不会被切断 */
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

/* Markdown 排版细节 */
.markdown-body {
  font-size: 15px;
  line-height: 1.8;
  color: #2c3e50;
}
.markdown-body :deep(h3) {
  margin-top: 24px;
  margin-bottom: 10px;
  border-bottom: 1px solid #eeeeee;
  padding-bottom: 6px;
  color: #303133;
}
.markdown-body :deep(pre) {
  background-color: #f6f8fa;
  border-radius: 6px;
  padding: 14px;
  overflow-x: auto;
}

/* 右侧满屏 Monaco 编辑器 */
.right-pane {
  background-color: #1e1e1e;
  overflow: hidden;
}

/* 右下角悬浮通知卡片 */
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

/* 弹窗与终端样式 */
.result-banner {
  padding: 16px 24px;
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.verdict-text { font-size: 24px; font-weight: bold; }
.stats-text { display: flex; gap: 20px; font-size: 14px; }
.banner-ac { background-color: #f0f9eb; color: #67c23a; border: 1px solid #e1f3d8; }
.banner-wa { background-color: #fef0f0; color: #f56c6c; border: 1px solid #fde2e2; }
.banner-ce { background-color: #f4f4f5; color: #909399; border: 1px solid #e9e9eb; }
.error-terminal {
  background-color: #1e1e1e;
  color: #f56c6c;
  padding: 14px;
  border-radius: 6px;
  font-family: monospace;
  font-size: 13px;
  max-height: 250px;
  overflow-y: auto;
}
</style>