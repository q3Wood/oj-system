<template>
  <div class="contest-list-container">
    <el-card class="box-card" shadow="never">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="header-title">🔥 算法竞赛大厅</span>
            <span class="header-desc">参加官方算法对抗赛，检验你的编程实战能力</span>
          </div>
          <div class="header-search">
            <el-input
              v-model.trim="queryParams.keyword"
              placeholder="搜索比赛名称..."
              :prefix-icon="Search"
              clearable
              style="width: 240px;"
              @clear="handleSearch"
              @keyup.enter="handleSearch"
            />
            <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          </div>
        </div>
      </template>

      <!-- 比赛数据表格 -->
      <el-table v-loading="loading" :data="contestList" style="width: 100%" stripe>
        <el-table-column prop="id" label="#" width="70" align="center" />

        <el-table-column prop="title" label="比赛名称" min-width="260">
          <template #default="{ row }">
            <div class="contest-title-cell">
              <el-link
                type="primary"
                :underline="false"
                class="contest-title"
                @click="$router.push(`/contests/${row.id}`)"
              >
                {{ row.title }}
              </el-link>
              <el-tag v-if="row.isRegistered" type="success" size="small" effect="plain">
                已报名
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="当前状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)" effect="dark" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="problemCount" label="题数" width="90" align="center">
          <template #default="{ row }">{{ row.problemCount ?? 0 }} 题</template>
        </el-table-column>

        <el-table-column prop="registeredCount" label="报名人数" width="110" align="center">
          <template #default="{ row }">{{ row.registeredCount ?? 0 }} 人</template>
        </el-table-column>

        <el-table-column prop="startTime" label="开始时间" width="175" align="center" />
        <el-table-column prop="endTime" label="结束时间" width="175" align="center" />

        <el-table-column label="操作" width="130" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              :type="row.status === 'RUNNING' ? 'success' : 'primary'"
              size="small"
              @click="$router.push(`/contests/${row.id}`)"
            >
              {{ row.status === 'RUNNING' ? '进入比赛' : '查看详情' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 底部分页 -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          @size-change="handleSearch"
          @current-change="fetchContests"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { getContestListApi } from '../../api/contest'
import type { Contest, ContestQueryParams } from '../../types'

const loading = ref(false)
const contestList = ref<Contest[]>([])
const total = ref(0)

const queryParams = reactive<ContestQueryParams>({
  pageNum: 1,
  pageSize: 10,
  keyword: ''
})

const fetchContests = async () => {
  loading.value = true
  try {
    const data = await getContestListApi({
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize,
      keyword: queryParams.keyword || undefined
    })
    contestList.value = data.items
    total.value = data.total
  } catch {
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryParams.pageNum = 1
  fetchContests()
}

const getStatusTagType = (status: string) => {
  switch (status) {
    case 'RUNNING':
      return 'success'
    case 'NOT_STARTED':
      return 'warning'
    default:
      return 'info'
  }
}

const getStatusText = (status: string) => {
  switch (status) {
    case 'RUNNING':
      return '进行中'
    case 'NOT_STARTED':
      return '未开始'
    case 'ENDED':
      return '已结束'
    default:
      return '未知'
  }
}

onMounted(() => {
  fetchContests()
})
</script>

<style scoped>
.contest-list-container {
  max-width: 1200px;
  margin: 0 auto;
}
.box-card {
  border-radius: 8px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.header-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.header-search {
  display: flex;
  gap: 10px;
}
.header-title {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
}
.header-desc {
  font-size: 13px;
  color: #909399;
}
.contest-title-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.contest-title {
  font-size: 15px;
  font-weight: 500;
}
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>