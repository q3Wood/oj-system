import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User } from '../types'

export const useUserStore = defineStore(
  'user',
  () => {
    const token = ref<string>('')
    const userInfo = ref<User | null>(null)

    // ==================== Getters (计算属性，方便页面直接做权限判断) ====================
    // 是否已登录
    const isLoggedIn = computed(() => !!token.value)
    // 是否拥有后台管理权限 (ADMIN 或 ROOT)
    const isAdmin = computed(() => userInfo.value?.role === 'ADMIN' || userInfo.value?.role === 'ROOT')
    // 是否为最高权限 ROOT
    const isRoot = computed(() => userInfo.value?.role === 'ROOT')

    // ==================== Actions ====================
    const setToken = (newToken: string) => {
      token.value = newToken
    }

    const setUserInfo = (info: User) => {
      userInfo.value = info
    }

    const clearUserOptions = () => {
      token.value = ''
      userInfo.value = null
    }

    return {
      token,
      userInfo,
      isLoggedIn,
      isAdmin,
      isRoot,
      setToken,
      setUserInfo,
      clearUserOptions
    }
  },
  {
    persist: true // 开启自动持久化到 localStorage
  }
)