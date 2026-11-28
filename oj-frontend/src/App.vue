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
        <el-menu-item index="/changelog">
          <span>更新日志</span>
          <span v-if="hasNewChangelog" class="nav-new-badge">NEW</span>
        </el-menu-item>
      </el-menu>

      <div class="user-box">
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
      <!-- 页面主体内容区域 (自动撑满剩余高度) -->
      <div class="main-content">
        <router-view />
      </div>

      <!-- 全站底部页脚：工信部 ICP 主体备案号（广东省规范） -->
      <footer class="site-footer">
        <span>Copyright © 2026 Core OJ</span>
        <span class="footer-divider">|</span>
        <a
          href="https://beian.miit.gov.cn/"
          target="_blank"
          rel="noopener noreferrer"
          class="beian-link"
        >
          粤ICP备2026192995号
        </a>
        <!-- 后续公安联网备案通过后，可在此取消注释并替换为公安备案图标和编号 -->
        <!--
        <span class="footer-divider">|</span>
        <a
          href="https://beian.mps.gov.cn/#/query/webSearch"
          target="_blank"
          rel="noopener noreferrer"
          class="beian-link"
        >
          粤公网安备 4401XXXXXXXXXX号
        </a>
        -->
      </footer>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Platform, Setting, SwitchButton } from '@element-plus/icons-vue'
import { useUserStore } from './stores/user'
import { userLogoutApi } from './api/user'
import { getLatestChangelogApi } from './api/changelog'

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

const hasNewChangelog = ref(false)

const checkChangelogBadge = async () => {
  try {
    const latest = await getLatestChangelogApi()
    if (!latest || !latest.id || !latest.releaseDate) {
      hasNewChangelog.value = false
      return
    }
    const releaseMs = new Date(latest.releaseDate.replace(/-/g, '/')).getTime()
    const within7Days = !Number.isNaN(releaseMs) && Date.now() - releaseMs <= 7 * 24 * 60 * 60 * 1000
    const lastReadId = Number(localStorage.getItem('oj_read_changelog_id') || '0')
    hasNewChangelog.value = within7Days && latest.id > lastReadId
  } catch {
    hasNewChangelog.value = false
  }
}

const handleChangelogRead = () => {
  hasNewChangelog.value = false
}

onMounted(() => {
  checkChangelogBadge()
  window.addEventListener('oj-changelog-read', handleChangelogRead)
})

onUnmounted(() => {
  window.removeEventListener('oj-changelog-read', handleChangelogRead)
})
</script>

<!-- 1. 全局基础样式：彻底干掉浏览器默认的 body 8px 边距和最外层双滚动条 -->
<style>
html, body, #app {
  margin: 0;
  padding: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  box-sizing: border-box;
}
</style>

<!-- 2. 布局组件局部样式 -->
<style scoped>
.layout-container {
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background-color: #f5f7fa;
}
.header {
  height: 60px;
  background-color: #fff;
  border-bottom: 1px solid #dcdfe6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 40px;
  flex-shrink: 0;
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
  flex: 1;
  height: calc(100vh - 60px);
  overflow-y: auto;
  overflow-x: hidden;
  padding: 20px 32px 0;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}
.main-content {
  flex: 1;
}
.site-footer {
  margin-top: 28px;
  padding: 16px 0 20px;
  border-top: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  font-size: 12.5px;
  color: #909399;
}
.footer-divider {
  margin: 0 10px;
  color: #dcdfe6;
}
.beian-link {
  color: #909399;
  text-decoration: none;
  transition: color 0.2s;
}
.beian-link:hover {
  color: #409eff;
}

.nav-new-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-left: 5px;
  padding: 0 5px;
  height: 16px;
  line-height: 16px;
  font-size: 10px;
  font-weight: 700;
  color: #ffffff;
  background: linear-gradient(90deg, #f43f5e, #ef4444);
  border-radius: 8px;
  transform: translateY(-2px);
  animation: badge-pulse 2s infinite;
}
@keyframes badge-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.72; }
}
</style>