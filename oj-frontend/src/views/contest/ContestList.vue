<template>
  <div class="contest-list-container">
    <el-card class="box-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">🔥 算法竞赛大厅</span>
          <span class="header-desc">参加官方算法对抗赛，检验你的编程实战能力</span>
        </div>
      </template>

      <!-- 比赛数据表格 -->
      <el-table
        v-loading="loading"
        :data="contestList"
        style="width: 100%"
        stripe
      >
        <el-table-column prop="id" label="#" width="70" align="center" />

        <el-table-column prop="title" label="比赛名称" min-width="260">
          <template #default="{ row }">
            <el-link 
              type="primary" 
              :underline="false" 
              class="contest-title" 
              @click="$router.push(`/contests/${row.id}`)"
            >
              {{ row.title }}
            </el-link>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="当前状态" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)" effect="dark" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="startTime" label="开始时间" width="180" align="center" />
        <el-table-column prop="endTime" label="结束时间" width="180" align="center" />

        <el-table-column label="操作" width="140" align="center">
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
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          layout="total, prev, pager, next"
          :total="total"
          @current-change="fetchContests"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getContestListApi } from '../../api/contest'
import type { Contest } from '../../types'

const loading = ref(false)
const contestList = ref<Contest[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const fetchContests = async () => {
  loading.value = true
  try {
    const data = await getContestListApi({ pageNum: pageNum.value, pageSize: pageSize.value })
    contestList.value = data.items
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 状态标签
const getStatusTagType = (status: string) => {
  switch (status) {
    case 'RUNNING': return 'success'
    case 'NOT_STARTED': return 'warning'
    case 'ENDED': return 'info'
    default: return 'info'
  }
}

const getStatusText = (status: string) => {
  switch (status) {
    case 'RUNNING': return '进行中'
    case 'NOT_STARTED': return '未开始'
    case 'ENDED': return '已结束'
    default: return '未知'
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
  align-items: baseline;
  gap: 12px;
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