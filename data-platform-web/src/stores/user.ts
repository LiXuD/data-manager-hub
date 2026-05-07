import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { STORAGE_KEYS } from '@/constants'

interface UserInfo {
  id: string
  username: string
  nickname: string
  email?: string
  phone?: string
  roles: string[]
  tenantId?: number
  permissions?: string[]
}

const loadUserInfo = (): UserInfo | null => {
  const stored = localStorage.getItem(STORAGE_KEYS.USER_INFO)
  return stored ? JSON.parse(stored) : null
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem(STORAGE_KEYS.TOKEN) || '')
  const userInfo = ref<UserInfo | null>(loadUserInfo())
  const username = computed(() => userInfo.value?.username || '')
  const tenantId = computed(() => userInfo.value?.tenantId)
  const permissions = computed(() => userInfo.value?.permissions || [])
  const isLoggedIn = computed(() => !!token.value)

  const hasPermission = (permission: string): boolean => {
    return permissions.value.includes(permission)
  }

  const setToken = (newToken: string) => {
    if (newToken === token.value) return
    token.value = newToken
    if (newToken) {
      localStorage.setItem(STORAGE_KEYS.TOKEN, newToken)
    } else {
      localStorage.removeItem(STORAGE_KEYS.TOKEN)
    }
  }

  const setUserInfo = (info: UserInfo | null) => {
    if (info === userInfo.value) return
    userInfo.value = info
    if (info) {
      localStorage.setItem(STORAGE_KEYS.USER_INFO, JSON.stringify(info))
    } else {
      localStorage.removeItem(STORAGE_KEYS.USER_INFO)
    }
  }

  const login = (newToken: string, info?: UserInfo) => {
    setToken(newToken)
    if (info) {
      setUserInfo(info)
    }
  }

  const logout = () => {
    setToken('')
    setUserInfo(null)
  }

  return {
    token,
    userInfo,
    username,
    tenantId,
    permissions,
    isLoggedIn,
    setToken,
    setUserInfo,
    login,
    logout,
    hasPermission
  }
})
