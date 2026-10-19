<template>
  <div class="problem-list-container">
    <el-card class="box-card" shadow="never">
      <!-- 顶部搜索工具栏 -->
      <div class="filter-bar">
        <div class="search-input">
          <el-input
            v-model="keyword"
            placeholder="输入题目标题搜索..."
            prefix-icon="Search"
            clearable
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          />
        </div>
        <el-button type="primary" icon="Search" @click="handleSearch">搜索</el-button>
        <el-button icon="Refresh" @click="handleReset">重置</el-button>
        <el-button type="success" icon="Plus" style="margin-left: auto;" @click="$router.push('/admin/problem/create')">创建新题目</el-button>
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

      <!-- 底部底部分页条 -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="fetchProblems"
          @current-change="fetchProblems"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getProblemListApi } from '../../api/problem'
import type { Problem } from '../../types'

const router = useRouter()

const loading = ref(false)
const problemList = ref<Problem[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const keyword = ref('')

// 获取题目列表
const fetchProblems = async () => {
  loading.value = true
  try {
    const data = await getProblemListApi({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim() || undefined,
      isPublic: 1 // 只获取公开题目
    })
    problemList.value = data.items
    total.value = data.total
  } catch (error) {
    // 错误由 request.ts 统一拦截提示
  } finally {
    loading.value = false
  }
}

// 搜索与重置
const handleSearch = () => {
  pageNum.value = 1
  fetchProblems()
}

const handleReset = () => {
  keyword.value = ''
  pageNum.value = 1
  fetchProblems()
}

// 跳转到做题详情页
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
  gap: 12px;
  margin-bottom: 20px;
}
.search-input {
  width: 280px;
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