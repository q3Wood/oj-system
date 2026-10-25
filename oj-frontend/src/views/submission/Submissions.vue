<template>
  <div class="submissions-container">
    <el-card class="box-card" shadow="never">
      <!-- 顶部过滤筛选栏 -->
      <div class="filter-bar">
        <div class="filter-item">
          <span class="label">题号:</span>
          <el-input
            v-model.number="queryParams.problemId"
            placeholder="输入题号 (如 1001)"
            style="width: 160px"
            clearable
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          />
        </div>

        <div class="filter-item">
          <span class="label">用户名称:</span>
          <el-input
            v-model.trim="queryParams.username"
            placeholder="输入用户名搜索..."
            style="width: 170px"
            clearable
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          />
        </div>

        <div class="filter-item">
          <span class="label">评测状态:</span>
          <el-select
            v-model="queryParams.verdict"
            placeholder="全部状态"
            style="width: 190px"
            clearable
            @change="handleSearch"
          >
            <el-option label="Accepted (通过)" value="Accepted" />
            <el-option label="Wrong Answer (答案错误)" value="Wrong Answer" />
            <el-option label="Time Limit Exceeded (超时)" value="Time Limit Exceeded" />
            <el-option label="Memory Limit Exceeded (超内存)" value="Memory Limit Exceeded" />
            <el-option label="Runtime Error (运行错误)" value="Runtime Error" />
            <el-option label="Compile Error (编译错误)" value="Compile Error" />
          </el-select>
        </div>

        <el-button type="primary" :icon="Search" @click="handleSearch">筛选</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </div>

      <!-- 提交记录表格 -->
      <el-table v-loading="loading" :data="submissionList" style="width: 100%" stripe>
        <el-table-column prop="id" label="记录 ID" width="100" align="center">
          <template #default="{ row }">
            <span class="sub-id">#{{ row.id }}</span>
          </template>
        </el-table-column>

        <el-table-column label="题目" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link
              type="primary"
              :underline="false"
              style="display: inline-block; text-align: left;"
              @click="$router.push(`/problems/${row.problemId}`)"
            >
              <b style="margin-right: 6px;">#{{ row.problemId }}</b>
              <span>{{ row.problemTitle }}</span>
            </el-link>
          </template>
        </el-table-column>

        <el-table-column prop="verdict" label="评测状态" width="180">
          <template #default="{ row }">
            <el-tag :type="getVerdictTagType(row.verdict)" effect="dark" size="small">
              {{ row.verdict }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="maxTimeMs" label="运行时间" width="110" align="center">
          <template #default="{ row }">{{ row.maxTimeMs }} ms</template>
        </el-table-column>

        <el-table-column prop="peakMemoryKb" label="峰值内存" width="120" align="center">
          <template #default="{ row }">{{ row.peakMemoryKb }} KB</template>
        </el-table-column>

        <el-table-column prop="language" label="语言" width="90" align="center">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.language }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="username" label="提交者" min-width="140" align="center">
          <template #default="{ row }">
            <span style="font-weight: 500;">{{ row.username }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="createdAt" label="提交时间" min-width="170" align="center" />

        <el-table-column label="操作" width="110" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openReportDialog(row.id)">
              查看代码
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页工具条 -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSearch"
          @current-change="fetchSubmissions"
        />
      </div>
    </el-card>

    <!-- 统一复用评测报告弹窗 -->
    <SubmissionDetailModal :submission-id="currentReportSubId" v-model="reportDialogVisible"/>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getSubmissionListApi } from '../../api/submission'
import { useUserStore } from '../../stores/user'
import type { Submission, SubmissionQueryParams } from '../../types'
import SubmissionDetailModal from '../../components/SubmissionDetailModal.vue'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const submissionList = ref<Submission[]>([])
const total = ref(0)

const queryParams = reactive<SubmissionQueryParams>({
  pageNum: 1,
  pageSize: 10,
  problemId: undefined,
  username: '',
  verdict: ''
})

const reportDialogVisible = ref(false)
const currentReportSubId = ref<number | null>(null)

const openReportDialog = (id: number) => {
  if (!userStore.token) {
    ElMessage.warning('请先登录后再查看代码与评测明细！')
    router.push('/login')
    return
  }
  currentReportSubId.value = id
  reportDialogVisible.value = true
}

const fetchSubmissions = async () => {
  loading.value = true
  try {
    const data = await getSubmissionListApi({
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize,
      problemId: queryParams.problemId || undefined,
      verdict: queryParams.verdict || undefined,
      username: queryParams.username || undefined
    })
    submissionList.value = data.items
    total.value = data.total
  } catch {
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryParams.pageNum = 1
  fetchSubmissions()
}

const handleReset = () => {
  queryParams.problemId = undefined
  queryParams.verdict = ''
  queryParams.username = ''
  queryParams.pageNum = 1
  fetchSubmissions()
}

const getVerdictTagType = (verdict: string) => {
  switch (verdict) {
    case 'Accepted':
    case 'AC':
      return 'success'
    case 'Wrong Answer':
    case 'Runtime Error':
    case 'System Error':
      return 'danger'
    case 'Time Limit Exceeded':
    case 'Memory Limit Exceeded':
      return 'warning'
    case 'PENDING':
    case 'RUNNING':
      return 'primary'
    default:
      return 'info'
  }
}

onMounted(() => {
  fetchSubmissions()
})
</script>

<style scoped>
.submissions-container {
  max-width: 1300px;
  margin: 0 auto;
}
.box-card {
  border-radius: 8px;
  border: 1px solid #ebeef5;
}
.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 20px;
}
.filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.label {
  font-size: 14px;
  color: #606266;
}
.sub-id {
  font-weight: bold;
  color: #909399;
}
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
}
</style>