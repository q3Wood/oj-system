import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { User } from '../types'

export const useUserStore = defineStore(
  'user',
  () => {
    const token = ref<string>('')
    const userInfo = ref<User | null>(null)

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
      setToken,
      setUserInfo,
      clearUserOptions
    }
  },
  {
    persist: true // 开启自动持久化到本地存储
  }
)