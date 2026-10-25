<template>
  <div class="workbench-container" v-loading="pageLoading">
    <el-row :gutter="16" class="workbench-row">
      <!-- ================= 左半屏：题面 / 题解 / 提交记录 ================= -->
      <el-col :span="12" class="pane-col">
        <el-card class="pane-card description-pane" shadow="never">
          <template #header>
            <div class="problem-meta-header">
              <div class="breadcrumb-bar">
                <el-link :underline="false" :icon="Back" @click="$router.push('/problems')">
                  返回题库列表
                </el-link>
              </div>

              <!-- 第一行：左边题号+标题，最右边做题状态 -->
              <div class="title-row">
                <h2 class="problem-title">{{ problem?.id }}. {{ problem?.title }}</h2>
                <div v-if="problem" class="status-right">
                  <ProblemStatusBadge :status="problem.userStatus" />
                  <!-- <span class="status-text" :class="getStatusTextClass(problem.userStatus)">
                    {{ getStatusLabel(problem.userStatus) }}
                  </span> -->
                </div>
              </div>

              <!-- 第二行：左边选项卡切换，最右边时间与内存限制 -->
              <div class="tabs-row">
                <el-radio-group v-model="activeTab" size="small" @change="handleTabChange">
                  <el-radio-button value="desc">题目描述</el-radio-button>
                  <el-radio-button value="solution">题解</el-radio-button>
                  <el-radio-button value="submissions">提交记录</el-radio-button>
                  <el-radio-button value="discuss" disabled>讨论 (待开放)</el-radio-button>
                </el-radio-group>

                <div class="tag-wrap">
                  <el-tag type="info" size="small">{{ problem?.timeLimitMs }} ms</el-tag>
                  <el-tag type="warning" size="small">{{ problem?.memoryLimitMb }} MB</el-tag>
                </div>
              </div>
            </div>
          </template>

          <!-- Tab 1: 题目描述 (复用通用组件) -->
          <ProblemDescription v-show="activeTab === 'desc'" :content="problem?.description" />

          <!-- Tab 2: 题解视图 -->
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

      <!-- ================= 右半屏：复用通用代码编辑与评测工作台 ================= -->
      <el-col :span="12" class="pane-col">
        <CodeWorkspace
          :problem-id="problemId"
          @submitted="onCodeSubmitted"
          @judge-finished="onJudgeFinished"
          @view-report="openReportDialog"
        />
      </el-col>
    </el-row>

    <!-- 评测详情弹窗 -->
    <SubmissionDetailModal
      v-model="reportDialogVisible"
      :submission-id="currentReportSubId"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Back } from '@element-plus/icons-vue'
import ProblemStatusBadge from '../../components/ProblemStatusBadge.vue'
import ProblemDescription from '../../components/ProblemDescription.vue'
import ProblemSolution from '../../components/ProblemSolution.vue'
import CodeWorkspace from '../../components/CodeWorkspace.vue'
import SubmissionDetailModal from '../../components/SubmissionDetailModal.vue'
import { getProblemDetailApi } from '../../api/problem'
import { getSubmissionListApi } from '../../api/submission'
import { useUserStore } from '../../stores/user'
import type { Problem, Submission } from '../../types'

const route = useRoute()
const userStore = useUserStore()

const problemId = Number(route.params.id)
const pageLoading = ref(false)
const problem = ref<Problem | null>(null)
const activeTab = ref('desc')

const historySubmissions = ref<Submission[]>([])
const historyLoading = ref(false)
const reportDialogVisible = ref(false)
const currentReportSubId = ref<number | null>(null)

const fetchProblemDetail = async () => {
  pageLoading.value = true
  try {
    problem.value = await getProblemDetailApi(problemId)
  } catch {
  } finally {
    pageLoading.value = false
  }
}

const fetchHistorySubmissions = async () => {
  if (!userStore.userInfo?.id) return
  historyLoading.value = true
  try {
    const data = await getSubmissionListApi({
      pageNum: 1,
      pageSize: 50,
      problemId: problemId,
      userId: userStore.userInfo.id
    })
    historySubmissions.value = data.items
  } catch {
  } finally {
    historyLoading.value = false
  }
}

const handleTabChange = (val: any) => {
  if (val === 'submissions') {
    fetchHistorySubmissions()
  }
}

const onCodeSubmitted = () => {
  activeTab.value = 'submissions'
  fetchHistorySubmissions()
}

const onJudgeFinished = (latest: Submission) => {
  fetchHistorySubmissions()
  if (problem.value) {
    if (latest.verdict === 'Accepted' || latest.verdict === 'AC') {
      problem.value.userStatus = 'SOLVED'
    } else if (problem.value.userStatus !== 'SOLVED') {
      problem.value.userStatus = 'ATTEMPTED'
    }
  }
}

const openReportDialog = (sub: Submission | null) => {
  if (!sub) return
  currentReportSubId.value = sub.id
  reportDialogVisible.value = true
}

const getVerdictTagType = (verdict: string) => {
  if (verdict === 'Accepted' || verdict === 'AC') return 'success'
  if (verdict === 'Time Limit Exceeded' || verdict === 'Memory Limit Exceeded') return 'warning'
  return 'danger'
}

const getStatusLabel = (status?: string) => {
  if (status === 'SOLVED') return '已通过'
  if (status === 'ATTEMPTED') return '尝试过'
  return '未开始'
}

const getStatusTextClass = (status?: string) => {
  if (status === 'SOLVED') return 'text-solved'
  if (status === 'ATTEMPTED') return 'text-attempted'
  return 'text-none'
}

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
.status-right {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 500;
}
.text-solved { color: #67c23a; }
.text-attempted { color: #e6a23c; }
.text-none { color: #909399; }

.tabs-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
}
.tag-wrap {
  display: flex;
  gap: 8px;
}
</style>