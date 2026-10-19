<template>
  <div class="submissions-container">
    <el-card class="box-card" shadow="never">
      <!-- 顶部过滤筛选栏 -->
      <div class="filter-bar">
        <div class="filter-item">
          <span class="label">题号:</span>
          <el-input
            v-model="filterProblemId"
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
          v-model="filterUsername"
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
            v-model="filterVerdict"
            placeholder="全部状态"
            style="width: 180px"
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

        <el-button type="primary" icon="Search" @click="handleSearch">筛选</el-button>
        <el-button icon="Refresh" @click="handleReset">重置</el-button>
      </div>

      <!-- 提交记录表格 -->
      <el-table
        v-loading="loading"
        :data="submissionList"
        style="width: 100%"
        stripe
      >
        <el-table-column prop="id" label="记录 ID" width="110" align="center">
          <template #default="{ row }">
            <span class="sub-id">#{{ row.id }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="problemId" label="题目" width="120" align="center">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="$router.push(`/problems/${row.problemId}`)">
              #{{ row.problemId }}
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

        <el-table-column prop="maxTimeMs" label="运行时间" width="120" align="center">
          <template #default="{ row }">{{ row.maxTimeMs }} ms</template>
        </el-table-column>

        <el-table-column prop="peakMemoryKb" label="峰值内存" width="130" align="center">
          <template #default="{ row }">{{ row.peakMemoryKb }} KB</template>
        </el-table-column>

        <el-table-column prop="language" label="语言" width="100" align="center">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.language }}</el-tag>
          </template>
        </el-table-column>

        <!-- <el-table-column prop="userId" label="用户 ID" width="100" align="center" /> -->

        <el-table-column prop="username" label="用户名称" min-width="150" align="center">
        <template #default="{ row }">
          <span style="font-weight: 500;">
            <!-- <el-avatar :size="20" style="vertical-align: middle; margin-right: 6px;">
              {{ (row.username || 'U')[0].toUpperCase() }}
            </el-avatar> -->
            {{ row.username }}
          </span>
        </template>
      </el-table-column>

        <el-table-column prop="createdAt" label="提交时间" min-width="170" align="center" />

        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <!-- 【核心修改】：绑定打开通用报告弹窗函数 openReportDialog -->
            <el-button link type="primary" size="small" @click="openReportDialog(row.id)">
              查看代码
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页工具条 -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="fetchSubmissions"
          @current-change="fetchSubmissions"
        />
      </div>
    </el-card>

    <!-- 统一复用评测报告弹窗 (带代码展示) -->
    <SubmissionDetailModal
      v-model="reportDialogVisible"
      :submission-id="currentReportSubId"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getSubmissionListApi } from '../../api/submission'
import type { Submission, PageResult } from '../../types'
import SubmissionDetailModal from '../../components/SubmissionDetailModal.vue'

const loading = ref(false)
const submissionList = ref<Submission[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

// 筛选条件
const filterProblemId = ref('')
const filterVerdict = ref('')
const filterUsername = ref('') // 新增：用户名筛选

// 复用弹窗控制
const reportDialogVisible = ref(false)
const currentReportSubId = ref<number | null>(null)

// 点击“查看代码”触发的函数
const openReportDialog = (id: number) => {
  currentReportSubId.value = id
  reportDialogVisible.value = true
}

// 获取列表数据 (走 api 封装)
const fetchSubmissions = async () => {
  loading.value = true
  try {
    const data = await getSubmissionListApi({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      problemId: filterProblemId.value.trim() ? Number(filterProblemId.value.trim()) : undefined,
      verdict: filterVerdict.value || undefined,
      username: filterUsername.value.trim() || undefined
    })
    submissionList.value = data.items
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 搜索与重置
const handleSearch = () => {
  pageNum.value = 1
  fetchSubmissions()
}

const handleReset = () => {
  filterProblemId.value = ''
  filterVerdict.value = ''
  filterUsername.value = ''
  pageNum.value = 1
  fetchSubmissions()
}

// 状态标签色彩样式
const getVerdictTagType = (verdict: string) => {
  switch (verdict) {
    case 'Accepted': return 'success'
    case 'Wrong Answer': return 'danger'
    case 'Time Limit Exceeded': return 'warning'
    case 'Memory Limit Exceeded': return 'warning'
    case 'Runtime Error': return 'danger'
    case 'Compile Error': return 'info'
    default: return 'info'
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