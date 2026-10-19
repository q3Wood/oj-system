<template>
  <div class="admin-user-list">
    <el-card shadow="never">
      <!-- 顶部搜索工具栏 -->
      <div class="toolbar">
        <div class="search-box">
          <el-input
            v-model="keyword"
            placeholder="按用户名搜索用户..."
            prefix-icon="Search"
            clearable
            style="width: 260px;"
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          />
          <el-button type="primary" icon="Search" @click="handleSearch">搜索</el-button>
          <el-button icon="Refresh" @click="handleReset">重置</el-button>
        </div>

        <div class="tip-text">
          <el-tag type="warning" size="small" effect="dark">ROOT 专属控制台</el-tag>
          <span style="font-size: 13px; color: #909399; margin-left: 8px;">
            仅超级管理员拥有权限调整人员梯队
          </span>
        </div>
      </div>

      <!-- 用户数据表格 -->
      <el-table :data="userList" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="id" label="用户 ID" width="100" align="center">
          <template #default="{ row }">
            <span class="id-text">#{{ row.id }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="username" label="用户名" min-width="180">
          <template #default="{ row }">
            <span class="username-text">
              <el-avatar :size="24" style="margin-right: 8px; vertical-align: middle;">
                {{ row.username[0]?.toUpperCase() }}
              </el-avatar>
              <b>{{ row.username }}</b>
              <el-tag v-if="row.id === userStore.userInfo?.id" size="small" type="success" style="margin-left: 8px;">
                当前登录
              </el-tag>
            </span>
          </template>
        </el-table-column>

        <!-- 角色标签 -->
        <el-table-column prop="role" label="当前角色" width="140" align="center">
          <template #default="{ row }">
            <el-tag :type="getRoleTagType(row.role)" effect="dark" size="small">
              {{ row.role }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="createdAt" label="注册时间" min-width="180" align="center">
          <template #default="{ row }">
            {{ row.createdAt ? row.createdAt.replace('T', ' ') : '-' }}
          </template>
        </el-table-column>

        <!-- 角色调整操作 -->
        <el-table-column label="权限操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <!-- 保护一：ROOT 根账号拥有代码级豁免，禁止任何操作 -->
            <template v-if="row.role === 'ROOT'">
              <el-tag type="info" size="small">不可变更 (ROOT)</el-tag>
            </template>

            <!-- 保护二：不能修改当前登录人自己 (防误降级) -->
            <template v-else-if="row.id === userStore.userInfo?.id">
              <el-tag type="info" size="small">当前用户 (锁定)</el-tag>
            </template>

            <!-- 正常的升级与降级 -->
            <template v-else>
              <el-button 
                v-if="row.role === 'USER'" 
                type="primary" 
                size="small" 
                link
                @click="handleChangeRole(row, 'ADMIN')"
              >
                提拔为管理员
              </el-button>

              <el-button 
                v-else-if="row.role === 'ADMIN'" 
                type="warning" 
                size="small" 
                link
                @click="handleChangeRole(row, 'USER')"
              >
                撤销管理员
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页工具条 -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          layout="total, prev, pager, next"
          :total="total"
          @current-change="fetchUsers"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getAdminUserListApi, updateUserRoleApi } from '../../api/user'
import { useUserStore } from '../../stores/user'
import type { User, PageResult } from '../../types'

const userStore = useUserStore()
const loading = ref(false)
const userList = ref<User[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const keyword = ref('')

const fetchUsers = async () => {
  loading.value = true
  try {
    const data = await getAdminUserListApi({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim() || undefined
    })
    userList.value = data.items
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pageNum.value = 1
  fetchUsers()
}

const handleReset = () => {
  keyword.value = ''
  pageNum.value = 1
  fetchUsers()
}

// 调整角色 (带二次防误触弹窗)
const handleChangeRole = (row: User, targetRole: 'USER' | 'ADMIN') => {
  const actionText = targetRole === 'ADMIN' ? `提拔【${row.username}】为系统管理员 (ADMIN)` : `将【${row.username}】降级为普通用户 (USER)`
  
  ElMessageBox.confirm(
    `确定要${actionText}吗？`,
    '权限调整确认',
    {
      confirmButtonText: '确定调整',
      cancelButtonText: '取消',
      type: targetRole === 'ADMIN' ? 'success' : 'warning'
    }
  ).then(async () => {
    try {
      await updateUserRoleApi(row.id, targetRole)
      ElMessage.success('用户权限调整成功！')
      fetchUsers() // 刷新列表
    } catch {}
  })
}

const getRoleTagType = (role: string) => {
  switch (role) {
    case 'ROOT': return 'warning'
    case 'ADMIN': return 'danger'
    case 'USER': return 'info'
    default: return 'info'
  }
}

onMounted(() => {
  fetchUsers()
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
.tip-text {
  display: flex;
  align-items: center;
}
.id-text {
  font-weight: bold;
  color: #909399;
}
.username-text {
  display: flex;
  align-items: center;
}
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>