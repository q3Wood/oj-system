<template>
  <div class="workbench-container" v-loading="pageLoading">
    <el-row :gutter="16" class="workbench-row">
      <!-- ================= 左半屏：题面 / 题解 / 提交记录 (力扣排版) ================= -->
      <el-col :span="12" class="pane-col">
        <el-card class="pane-card description-pane" shadow="never">
          <template #header>
            <div class="problem-meta-header">
              <!-- 1. 顶部面包屑返回栏 -->
              <div class="breadcrumb-bar">
                <el-link :underline="false" icon="Back" @click="$router.push('/problems')">
                  返回题库列表
                </el-link>
              </div>

              <!-- 2. 主标题与限制标签 -->
              <div class="title-row">
                <h2 class="problem-title">{{ problem?.id }} {{ problem?.title }}</h2>
                <div class="tag-wrap">
                  <el-tag type="info" size="small">{{ problem?.timeLimitMs }} ms</el-tag>
                  <el-tag type="warning" size="small">{{ problem?.memoryLimitMb }} MB</el-tag>
                </div>
              </div>

              <!-- 3. 选项卡切换 -->
              <div class="tabs-row">
                <el-radio-group v-model="activeTab" size="small" @change="handleTabChange">
                  <el-radio-button label="desc">题目描述</el-radio-button>
                  <el-radio-button label="solution">题解</el-radio-button>
                  <el-radio-button label="submissions">提交记录</el-radio-button>
                  <el-radio-button label="discuss" disabled>讨论 (待开放)</el-radio-button>
                </el-radio-group>
              </div>
            </div>
          </template>

          <!-- Tab 1: 题目描述视图 (Markdown) -->
          <div v-show="activeTab === 'desc'" class="markdown-body" v-html="renderedDescription"></div>

          <!-- Tab 2: 题解视图 (调用独立的通用子组件) -->
          <div v-show="activeTab === 'solution'" class="solution-pane-wrap">
            <ProblemSolution :problem-id="problemId" />
          </div>

          <!-- Tab 3: 本题提交记录视图 -->
          <div v-show="activeTab === 'submissions'" class="problem-submissions-view">
            <el-table :data="historySubmissions" v-loading="historyLoading" size="small" style="width: 100%">
              <el-table-column prop="verdict" label="评测状态" width="160">
                <template #default="{ row }">
                  <el-tag :type="getVerdictTagType(row.verdict)" size="small">
                    {{ row.verdict }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="maxTimeMs" label="耗时" width="90" align="center">
                <template #default="{ row }">{{ row.maxTimeMs }} ms</template>
              </el-table-column>
              <el-table-column prop="peakMemoryKb" label="内存" width="100" align="center">
                <template #default="{ row }">{{ row.peakMemoryKb }} KB</template>
              </el-table-column>
              <el-table-column prop="createdAt" label="提交时间" min-width="150" align="center" />
              <el-table-column label="操作" width="90" align="center">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="openReportDialog(row)">
                    查看
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>
      </el-col>

      <!-- ================= 右半屏：代码编辑与提交区域 ================= -->
      <el-col :span="12" class="pane-col">
        <el-card class="pane-card editor-pane" shadow="never">
          <template #header>
            <div class="editor-header">
              <div class="lang-select">
                <span class="label">语言:</span>
                <el-select v-model="selectedLanguage" size="small" style="width: 110px">
                  <el-option label="C++ (g++)" value="cpp" />
                  <el-option label="C (gcc)" value="c" />
                </el-select>
              </div>
              <div class="action-buttons">
                <el-button size="small" icon="Refresh" @click="resetCodeTemplate">重置模板</el-button>
                <el-button 
                  type="success" 
                  size="small" 
                  icon="VideoPlay" 
                  :loading="submitting" 
                  @click="handleSubmitCode"
                >
                  提交评测
                </el-button>
              </div>
            </div>
          </template>

          <!-- 真正的 VS Code 内核 Monaco Editor -->
          <div class="code-editor-wrap">
            <MonacoEditor
              v-model="sourceCode"
              :language="selectedLanguage"
              theme="vs-dark"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ================= 右下角极简悬浮评测状态卡片 (力扣风格) ================= -->
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
              {{ isEvaluating ? '正在排队评测中...' : latestSubmission?.verdict }}
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

    <SubmissionDetailModal
      v-model="reportDialogVisible"
      :submission-id="currentReportSubId"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading, Check, Close, Refresh, VideoPlay, Back } from '@element-plus/icons-vue'
import { marked } from 'marked'
import markedKatex from 'marked-katex-extension'
import 'katex/dist/katex.min.css'

// 组件引入 (保持你的相对路径)
import MonacoEditor from '../../components/MonacoEditor.vue'
import ProblemSolution from '../../components/ProblemSolution.vue'
import SubmissionDetailModal from '../../components/SubmissionDetailModal.vue'
// API 与类型
import { getProblemDetailApi } from '../../api/problem'
import { submitCodeApi, getSubmissionListApi, getSubmissionDetailApi } from '../../api/submission'
import { useUserStore } from '../../stores/user'
import type { Problem, Submission, CaseDetail } from '../../types/index'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const problemId = Number(route.params.id)
const pageLoading = ref(false)
const submitting = ref(false)
const problem = ref<Problem | null>(null)
const activeTab = ref('desc')

// 评测状态与弹窗控制 (统一为一个弹窗)
const floatingCardVisible = ref(false)
const isEvaluating = ref(false)
const latestSubmission = ref<Submission | null>(null)
const reportDialogVisible = ref(false)
const selectedReport = ref<Submission | null>(null)

// 语言与代码
const selectedLanguage = ref('cpp')
const defaultCppTemplate = 
`#include <iostream>

using namespace std;

int main() {
    // 请在此处编写你的解题逻辑
    int a, b;
    while (cin >> a >> b) {
        cout << (a + b) << endl;
    }
    return 0;
}
`
const sourceCode = ref(defaultCppTemplate)

// 历史提交记录
const historySubmissions = ref<Submission[]>([])
const historyLoading = ref(false)

// Markdown 题面解析
const renderedDescription = computed(() => {
  if (!problem.value?.description) return '<p>暂无题目描述</p>'
  return marked.parse(problem.value.description) as string
})

// 解析弹窗里的测试点 JSON
const parsedDetails = computed<CaseDetail[]>(() => {
  if (!selectedReport.value?.judgeDetails) return []
  const details = selectedReport.value.judgeDetails
  if (typeof details === 'string') {
    try {
      const parsed = JSON.parse(details)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }
  return Array.isArray(details) ? details : []
})

// 获取题目详情
const fetchProblemDetail = async () => {
  pageLoading.value = true
  try {
    problem.value = await getProblemDetailApi(problemId)
  } finally {
    pageLoading.value = false
  }
}

const currentReportSubId = ref<number | null>(null)

const openReportDialog = (sub: Submission | null) => {
  if (!sub) return
  currentReportSubId.value = sub.id
  reportDialogVisible.value = true
}
// 选项卡切换处理
const handleTabChange = (val: any) => {
  if (val === 'submissions') {
    fetchHistorySubmissions()
  }
}

// 查询本题历史提交
const fetchHistorySubmissions = async () => {
  historyLoading.value = true
  try {
    const data = await getSubmissionListApi({
      pageNum: 1,
      pageSize: 50,
      problemId: problemId,
      userId: userStore.userInfo?.id
    })
    historySubmissions.value = data.items
  } finally {
    historyLoading.value = false
  }
}

// 打开统一的报告弹窗 (悬浮条点“查看报告”或者表格点“查看”都走这里)
// const openReportDialog = async (sub: Submission | null) => {
//   if (!sub) return
//   // 如果没有详细信息，重新调接口单查完整数据
//   if (!sub.judgeDetails || sub.judgeDetails.length === 0) {
//     try {
//       sub = await getSubmissionDetailApi(sub.id)
//     } catch (e) {}
//   }
//   selectedReport.value = sub
//   reportDialogVisible.value = true
// }

// 重置代码模板
const resetCodeTemplate = () => {
  sourceCode.value = defaultCppTemplate
}

// 提交代码并开启智能异步轮询
const handleSubmitCode = async () => {
  if (!userStore.token) {
    ElMessage.warning('请先登录再提交代码！')
    router.push('/login')
    return
  }

  if (!sourceCode.value.trim()) {
    ElMessage.warning('代码不能为空！')
    return
  }

  submitting.value = true
  isEvaluating.value = true
  floatingCardVisible.value = true

  try {
    const initialSubmission = await submitCodeApi({
      problemId: problemId,
      language: selectedLanguage.value,
      code: sourceCode.value
    })

    latestSubmission.value = initialSubmission
    // 自动切到提交记录 Tab
    activeTab.value = 'submissions'
    fetchHistorySubmissions()

    // 开启轮询
    const pollInterval = setInterval(async () => {
      try {
        const latest = await getSubmissionDetailApi(initialSubmission.id)
        latestSubmission.value = latest

        if (latest.verdict !== 'PENDING' && latest.verdict !== 'RUNNING') {
          clearInterval(pollInterval)
          isEvaluating.value = false
          submitting.value = false

          if (latest.verdict === 'Accepted') {
            ElMessage.success('恭喜通过全部测试点！')
          } else {
            ElMessage.error(`评测未通过：${latest.verdict}`)
          }
          fetchHistorySubmissions()
        }
      } catch (err) {
        clearInterval(pollInterval)
        isEvaluating.value = false
        submitting.value = false
      }
    }, 800)

  } catch (error) {
    submitting.value = false
    isEvaluating.value = false
    floatingCardVisible.value = false
  }
}

// 样式与辅助函数
const getFloatingCardClass = () => {
  if (isEvaluating.value) return 'card-pending'
  if (latestSubmission.value?.verdict === 'Accepted') return 'card-accepted'
  return 'card-failed'
}

// const getVerdictClass = (verdict: string) => {
//   if (verdict === 'Accepted') return 'banner-ac'
//   if (verdict === 'Compile Error') return 'banner-ce'
//   return 'banner-wa'
// }

const getVerdictTagType = (verdict: string) => {
  if (verdict === 'Accepted') return 'success'
  if (verdict === 'Time Limit Exceeded' || verdict === 'Memory Limit Exceeded') return 'warning'
  return 'danger'
}

// const getErrorMessage = (sub: Submission | null) => {
//   if (!sub || !sub.judgeDetails) return '无详细报错日志'
//   let raw = sub.judgeDetails
//   if (typeof raw === 'string') {
//     try {
//       const parsed = JSON.parse(raw)
//       return typeof parsed === 'string' ? parsed : JSON.stringify(parsed, null, 2)
//     } catch {
//       return raw
//     }
//   }
//   return JSON.stringify(raw, null, 2)
// }

marked.use(
  markedKatex({
    throwOnError: false,
    errorColor: '#cc0000'
  })
)
onMounted(() => {
  fetchProblemDetail()
})
</script>

<style scoped>
.workbench-container {
  height: calc(100vh - 110px);
  max-width: 1600px;
  margin: 0 auto;
}
.workbench-row, .pane-col {
  height: 100%;
}
.pane-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  border-radius: 8px;
}
:deep(.el-card__body) {
  flex: 1;
  overflow: hidden;
  padding: 0;
}

/* 左侧题面排版 */
.description-pane :deep(.el-card__body) {
  overflow-y: auto;
  padding: 24px;
}
.problem-meta-header {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.breadcrumb-bar {
  font-size: 13px;
}
.title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.problem-title {
  font-size: 18px;
  margin: 0;
  color: #303133;
}
.tag-wrap {
  display: flex;
  gap: 8px;
}
.tabs-row {
  margin-top: 4px;
}

/* Markdown 排版细节 */
.markdown-body {
  font-size: 15px;
  line-height: 1.7;
  color: #2c3e50;
}
.markdown-body :deep(h3) {
  margin-top: 16px;
  margin-bottom: 8px;
  border-bottom: 1px solid #eee;
  padding-bottom: 6px;
}
.markdown-body :deep(pre) {
  background-color: #f6f8fa;
  border-radius: 6px;
  padding: 12px;
  overflow-x: auto;
}

/* 右侧代码编辑区 */
.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.lang-select {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #606266;
}
.code-editor-wrap {
  height: 100%;
  background-color: #1e1e1e;
}

/* 统一评测报告弹窗 */
.result-banner {
  padding: 16px 24px;
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.verdict-text {
  font-size: 24px;
  font-weight: bold;
}
.stats-text {
  display: flex;
  gap: 20px;
  font-size: 14px;
}
.banner-ac { background-color: #f0f9eb; color: #67c23a; border: 1px solid #e1f3d8; }
.banner-wa { background-color: #fef0f0; color: #f56c6c; border: 1px solid #fde2e2; }
.banner-ce { background-color: #f4f4f5; color: #909399; border: 1px solid #e9e9eb; }

/* 错误终端 */
.error-log-section h4 {
  margin-bottom: 8px;
  color: #f56c6c;
}
.error-terminal {
  background-color: #1e1e1e;
  color: #f56c6c;
  padding: 14px 18px;
  border-radius: 6px;
  font-family: 'Fira Code', Consolas, Monaco, monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid #333;
}
.case-details-section h4 {
  margin-bottom: 12px;
  color: #303133;
}

/* 右下角悬浮卡片 */
.floating-judge-card {
  position: fixed;
  right: 32px;
  bottom: 32px;
  min-width: 300px;
  max-width: 380px;
  padding: 14px 18px;
  border-radius: 8px;
  background-color: #fff;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
  display: flex;
  justify-content: space-between;
  align-items: center;
  z-index: 2000;
  transition: all 0.3s ease;
}
.card-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.card-info {
  display: flex;
  flex-direction: column;
}
.card-status-title {
  font-size: 15px;
  font-weight: bold;
}
.card-sub-info {
  font-size: 12px;
  opacity: 0.85;
  margin-top: 2px;
}
.card-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-pending { background-color: #ecf5ff; border: 1px solid #d9ecff; color: #409eff; }
.card-accepted { background-color: #f0f9eb; border: 1px solid #e1f3d8; color: #67c23a; }
.card-failed { background-color: #fef0f0; border: 1px solid #fde2e2; color: #f56c6c; }
</style>