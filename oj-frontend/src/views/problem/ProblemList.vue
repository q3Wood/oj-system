<template>
  <div class="problem-list-container">
    <el-card class="box-card" shadow="never">
      <!-- 顶部多条件搜索工具栏 -->
      <div class="filter-bar">
        <el-input
          v-model.number="queryParams.id"
          placeholder="题号 ID (如 1001)"
          clearable
          style="width: 160px;"
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model.trim="queryParams.title"
          placeholder="输入题目标题搜索..."
          :prefix-icon="Search"
          clearable
          style="width: 240px;"
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model.trim="queryParams.author"
          placeholder="按出题人搜索..."
          clearable
          style="width: 160px;"
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </div>

      <!-- 题目数据表格 -->
      <el-table
        v-loading="loading"
        :data="problemList"
        style="width: 100%"
        stripe
        @row-click="goToDetail"
        row-class-name="clickable-row"
      >
        <!-- 新增：当前用户做题状态列 -->
        <el-table-column label="状态" width="70" align="center">
          <template #default="{ row }">
            <ProblemStatusBadge :status="row.userStatus" />
          </template>
        </el-table-column>

        <el-table-column prop="id" label="题号" width="100" align="center">
          <template #default="{ row }">
            <span class="problem-id">#{{ row.id }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="title" label="题目名称" min-width="250">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" class="problem-title">
              {{ row.title }}
            </el-link>
          </template>
        </el-table-column>

        <el-table-column prop="timeLimitMs" label="时间限制" width="130" align="center">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.timeLimitMs }} ms</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="memoryLimitMb" label="内存限制" width="130" align="center">
          <template #default="{ row }">
            <el-tag type="warning" size="small">{{ row.memoryLimitMb }} MB</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="author" label="出题人" width="120" align="center" />

        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click.stop="goToDetail(row)">
              挑战
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 底部分页条 -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSearch"
          @current-change="fetchProblems"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Refresh } from '@element-plus/icons-vue'
import ProblemStatusBadge from '../../components/ProblemStatusBadge.vue'
import { getProblemListApi } from '../../api/problem'
import type { Problem, ProblemQueryParams } from '../../types'

const router = useRouter()
const loading = ref(false)
const problemList = ref<Problem[]>([])
const total = ref(0)

const queryParams = reactive<ProblemQueryParams>({
  pageNum: 1,
  pageSize: 10,
  id: undefined,
  title: '',
  author: '',
  isPublic: 1
})

const fetchProblems = async () => {
  loading.value = true
  try {
    const data = await getProblemListApi({
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize,
      id: queryParams.id || undefined,
      title: queryParams.title || undefined,
      author: queryParams.author || undefined,
      isPublic: 1
    })
    problemList.value = data.items
    total.value = data.total
  } catch {
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryParams.pageNum = 1
  fetchProblems()
}

const handleReset = () => {
  queryParams.id = undefined
  queryParams.title = ''
  queryParams.author = ''
  queryParams.pageNum = 1
  fetchProblems()
}

const goToDetail = (row: Problem) => {
  router.push(`/problems/${row.id}`)
}

onMounted(() => {
  fetchProblems()
})
</script>

<style scoped>
.problem-list-container {
  max-width: 1200px;
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
  gap: 12px;
  margin-bottom: 20px;
}
.problem-id {
  font-weight: bold;
  color: #909399;
}
.problem-title {
  font-size: 15px;
  font-weight: 500;
}
.clickable-row {
  cursor: pointer;
}
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
}
</style>