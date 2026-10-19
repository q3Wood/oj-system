<template>
  <div class="admin-problem-list">
    <el-card shadow="never">
      <!-- 顶部操作栏 -->
      <!-- 顶部操作栏 -->
      <div class="toolbar">
        <div class="search-box">
          <el-input
            v-model="keyword"
            placeholder="按题目标题或作者搜索..."
            prefix-icon="Search"
            clearable
            style="width: 240px;"
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          />

          <!-- 【新增】：可见性状态精确筛选 -->
          <el-select 
            v-model="filterPublic" 
            placeholder="可见性状态 (全部)" 
            clearable 
            style="width: 170px;"
            @change="handleSearch"
          >
            <el-option label="全部题目 (公开+保密)" :value="undefined" />
            <el-option label="仅看公开题目" :value="1" />
            <el-option label="仅看比赛保密题" :value="0" />
          </el-select>

          <el-button type="primary" icon="Search" @click="handleSearch">搜索</el-button>
          <el-button icon="Refresh" @click="handleReset">重置</el-button>
        </div>

        <el-button type="success" icon="Plus" @click="openCreateModal">
          发布新题目 (带用例)
        </el-button>
      </div>

      <!-- 全量题目表格 -->
      <el-table :data="problemList" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="90" align="center">
          <template #default="{ row }">
            <span class="id-text">#{{ row.id }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="title" label="题目标题" min-width="240">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="$router.push(`/problems/${row.id}`)">
              {{ row.title }}
            </el-link>
          </template>
        </el-table-column>

        <!-- 一键切换公开 / 保密状态 -->
        <el-table-column prop="isPublic" label="可见性状态" width="160" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.isPublic"
              :active-value="1"
              :inactive-value="0"
              active-text="公开"
              inactive-text="保密"
              inline-prompt
              @change="(val: any) => handleToggleVisibility(row.id, val)"
            />
          </template>
        </el-table-column>

        <el-table-column prop="timeLimitMs" label="时限" width="110" align="center">
          <template #default="{ row }">{{ row.timeLimitMs }} ms</template>
        </el-table-column>

        <el-table-column prop="memoryLimitMb" label="内存限额" width="110" align="center">
          <template #default="{ row }">{{ row.memoryLimitMb }} MB</template>
        </el-table-column>

        <el-table-column prop="author" label="出题人" width="120" align="center" />
        <el-table-column prop="createdAt" label="创建时间" min-width="170" align="center" />

        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" icon="Edit" @click="openEditModal(row.id)">
              编辑
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          layout="total, prev, pager, next"
          :total="total"
          @current-change="fetchProblems"
        />
      </div>
    </el-card>

    <!-- 【核心复用】：新建 / 编辑共用这一个通用模态框！ -->
    <ProblemEditModal
      v-model="modalVisible"
      :problem-id="activeProblemId"
      @success="fetchProblems"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Plus, Edit } from '@element-plus/icons-vue'
import { getProblemListApi, updateProblemApi } from '../../api/problem'
import type { Problem, PageResult } from '../../types'
import ProblemEditModal from '../../components/ProblemEditModal.vue' // 引入通用模态框

const loading = ref(false)
const problemList = ref<Problem[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const keyword = ref('')

// 通用模态框控制
const modalVisible = ref(false)
const activeProblemId = ref<number | null>(null)
const filterPublic = ref<number | undefined>(undefined) // 新增：可见性状态筛选
  
const fetchProblems = async () => {
  loading.value = true
  try {
    const data = await getProblemListApi({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      isPublic: filterPublic.value,
      keyword: keyword.value.trim() || undefined
    })
    problemList.value = data.items
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pageNum.value = 1
  fetchProblems()
}

const handleReset = () => {
  keyword.value = ''
  pageNum.value = 1
  filterPublic.value = undefined
  pageNum.value = 1
  fetchProblems()
}

// 切换开关
const handleToggleVisibility = async (id: number, val: number) => {
  try {
    // 构造简单 FormData
    const formData = new FormData()
    formData.append('isPublic', String(val))
    await updateProblemApi(id, formData)
    ElMessage.success(val === 1 ? '已公开到公共题库！' : '已转为比赛保密题！')
  } catch {
    fetchProblems() // 失败回滚开关状态
  }
}

// 点击编辑：传入 ID 打开同一个模态框
const openEditModal = (id: number) => {
  activeProblemId.value = id
  modalVisible.value = true
}

// 点击发布：不传 ID 打开同一个模态框
const openCreateModal = () => {
  activeProblemId.value = null
  modalVisible.value = true
}

onMounted(() => {
  fetchProblems()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.search-box {
  display: flex;
  gap: 12px;
}
.id-text {
  font-weight: bold;
  color: #909399;
}
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>