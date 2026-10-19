<template>
  <el-container class="admin-container">
    <!-- 1. 左侧深色侧边栏 -->
    <el-aside width="220px" class="admin-aside">
      <div class="aside-logo" @click="$router.push('/')">
        <el-icon :size="24" color="#409eff" style="margin-right: 8px;"><Platform /></el-icon>
        <span>Core OJ 后台</span>
      </div>

      <el-menu
        :default-active="$route.path"
        :router="true"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
        class="admin-menu"
      >
        <el-menu-item index="/admin/problems">
          <el-icon><Document /></el-icon>
          <span>题目管理</span>
        </el-menu-item>

        <el-menu-item index="/admin/contests">
          <el-icon><Trophy /></el-icon>
          <span>竞赛管理</span>
        </el-menu-item>

        <el-menu-item v-if="userStore.userInfo?.role === 'ROOT'" index="/admin/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 2. 右侧主体区域 -->
    <el-container class="admin-main-container">
      <!-- 顶部 Header -->
      <el-header class="admin-header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>管理中心</el-breadcrumb-item>
            <el-breadcrumb-item>{{ getBreadcrumbTitle() }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <el-button link icon="Back" style="margin-right: 18px;" @click="$router.push('/')">
            返回前台大厅
          </el-button>
          <span class="admin-badge">
            <el-tag :type="userStore.userInfo?.role === 'ROOT' ? 'warning' : 'danger'" size="small" effect="dark" style="margin-right: 8px;">
              {{ userStore.userInfo?.role }}
            </el-tag>
            {{ userStore.userInfo?.username }}
          </span>
        </div>
      </el-header>

      <!-- 核心页面插槽 -->
      <el-main class="admin-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import { Platform, Document, Trophy, User, Back } from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'

const route = useRoute()
const userStore = useUserStore()

const getBreadcrumbTitle = () => {
  if (route.path.includes('problems')) return '题目管理'
  if (route.path.includes('contests')) return '竞赛管理'
  if (route.path.includes('users')) return '用户管理'
  return '概览'
}
</script>

<style scoped>
.admin-container {
  height: 100vh;
  overflow: hidden;
}
.admin-aside {
  background-color: #304156;
  height: 100%;
  display: flex;
  flex-direction: column;
}
.aside-logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 18px;
  font-weight: bold;
  cursor: pointer;
  background-color: #2b2f3a;
}
.admin-menu {
  border-right: none;
  flex: 1;
}
.admin-main-container {
  height: 100%;
  background-color: #f0f2f5;
  display: flex;
  flex-direction: column;
}
.admin-header {
  height: 60px;
  background-color: #ffffff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
}
.header-right {
  display: flex;
  align-items: center;
}
.admin-badge {
  font-size: 14px;
  font-weight: bold;
  color: #303133;
}
.admin-content {
  padding: 24px;
  overflow-y: auto;
}
</style>