<template>
  <el-container class="layout-container">
    <el-header class="header">
      <div class="logo" @click="$router.push('/')">
        <el-icon :size="24" color="#409eff" style="margin-right: 8px;"><Platform /></el-icon>
        <span>Core OJ</span>
      </div>
      
      <el-menu mode="horizontal" :router="true" :default-active="$route.path" class="menu">
        <el-menu-item index="/problems">题库大厅</el-menu-item>
        <el-menu-item index="/contests">竞赛</el-menu-item>
        <el-menu-item index="/submissions">评测状态</el-menu-item>
      </el-menu>

      <div class="user-box">
        <!-- 响应式绑定 Pinia，一旦登录成功自动变成头像 -->
        <template v-if="userStore.token">
          <el-dropdown>
            <span class="user-dropdown">
              <el-avatar :size="30" style="margin-right: 8px;">
                {{ (userStore.userInfo?.username || 'U')[0].toUpperCase() }}
              </el-avatar>
              {{ userStore.userInfo?.username || '用户' }}
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                 <!-- 仅对 ADMIN 和 ROOT 角色展示后台入口 -->
                        <el-dropdown-item 
                          v-if="userStore.userInfo?.role === 'ADMIN' || userStore.userInfo?.role === 'ROOT'" 
                          :icon="Setting" 
                          @click="$router.push('/admin/problems')"
                        >
                          进入管理后台
                        </el-dropdown-item>
                <el-dropdown-item :icon="SwitchButton" @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-button type="primary" size="small" @click="$router.push('/login')">登录 / 注册</el-button>
        </template>
      </div>
    </el-header>

    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Platform, Setting, SwitchButton } from '@element-plus/icons-vue'
import { useUserStore } from './stores/user'
import { userLogoutApi } from './api/user'

const router = useRouter()
const userStore = useUserStore()

const logout = async () => {
  try {
    await userLogoutApi()
  } catch (error) {
    // 错误由 request.ts 统一拦截提示
  } finally {
    userStore.clearUserOptions()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout-container {
  min-height: 100vh;
  background-color: #f5f7fa;
}
.header {
  background-color: #fff;
  border-bottom: 1px solid #dcdfe6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 40px;
}
.logo {
  display: flex;
  align-items: center;
  font-size: 20px;
  font-weight: bold;
  cursor: pointer;
  color: #303133;
}
.menu {
  flex: 1;
  margin-left: 40px;
  border-bottom: none;
}
.user-dropdown {
  display: flex;
  align-items: center;
  cursor: pointer;
  font-weight: 500;
}
.main {
  padding: 24px 40px;
}
</style>