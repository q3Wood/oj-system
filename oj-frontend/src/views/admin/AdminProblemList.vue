<template>
  <div class="admin-problem-list">
    <el-card shadow="never">
      <!-- 顶部多条件操作栏 -->
      <div class="toolbar">
        <div class="search-box">
          <el-input
            v-model.number="queryParams.id"
            placeholder="题号 ID"
            clearable
            style="width: 130px;"
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          />
          <el-input
            v-model.trim="queryParams.title"
            placeholder="按题目标题搜索..."
            :prefix-icon="Search"
            clearable
            style="width: 200px;"
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          />
          <el-input
            v-model.trim="queryParams.author"
            placeholder="按出题人搜索..."
            clearable
            style="width: 140px;"
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          />
          <el-select
            v-model="queryParams.isPublic"
            placeholder="可见性 (全部)"
            clearable
            style="width: 160px;"
            @change="handleSearch"
          >
            <el-option label="仅看公开题目" :value="1" />
            <el-option label="仅看比赛保密题" :value="0" />
          </el-select>

          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </div>

        <el-button type="success" :icon="Plus" @click="openCreateModal">
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

        <el-table-column prop="title" label="题目标题" min-width="220">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="$router.push(`/problems/${row.id}`)">
              {{ row.title }}
            </el-link>
          </template>
        </el-table-column>

        <!-- 一键切换公开 / 保密状态 (调用轻量级接口) -->
        <el-table-column prop="isPublic" label="可见性状态" width="150" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.isPublic"
              :active-value="1"
              :inactive-value="0"
              active-text="公开"
              inactive-text="保密"
              inline-prompt
              @change="(val: any) => handleToggleVisibility(row.id, Number(val))"
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

        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :icon="Edit" @click="openEditModal(row.id)">
              编辑
            </el-button>
            <el-button link type="danger" size="small" :icon="Delete" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          @size-change="handleSearch"
          @current-change="fetchProblems"
        />
      </div>
    </el-card>

    <!-- 新建 / 编辑共用通用模态框 -->
    <ProblemEditModal
      v-model="modalVisible"
      :problem-id="activeProblemId"
      @success="fetchProblems"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { getProblemListApi, updateProblemVisibilityApi, deleteProblemApi } from '../../api/problem'
import type { Problem, ProblemQueryParams } from '../../types'
import ProblemEditModal from '../../components/ProblemEditModal.vue'

const loading = ref(false)
const problemList = ref<Problem[]>([])
const total = ref(0)

const modalVisible = ref(false)
const activeProblemId = ref<number | null>(null)

const queryParams = reactive<ProblemQueryParams>({
  pageNum: 1,
  pageSize: 10,
  id: undefined,
  title: '',
  author: '',
  isPublic: undefined
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
      isPublic: queryParams.isPublic
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
  queryParams.isPublic = undefined
  queryParams.pageNum = 1
  fetchProblems()
}

// 切换公开/保密开关 (调用新增的轻量级接口)
const handleToggleVisibility = async (id: number, val: number) => {
  try {
    await updateProblemVisibilityApi(id, val)
    ElMessage.success(val === 1 ? '已公开到公共题库！' : '已转为比赛保密题！')
  } catch {
    fetchProblems() // 失败时刷新列表回滚开关状态
  }
}

// 逻辑软删除题目
const handleDelete = async (row: Problem) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除题目【#${row.id} ${row.title}】吗？删除后题库将不再展示该题。`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await deleteProblemApi(row.id)
    ElMessage.success('题目已删除！')
    fetchProblems()
  } catch {}
}

const openEditModal = (id: number) => {
  activeProblemId.value = id
  modalVisible.value = true
}

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
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}
.search-box {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
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