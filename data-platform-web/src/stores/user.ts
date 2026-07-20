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
  tenantName?: string
  lastLoginTime?: string
  permissions?: string[]
}

const loadUserInfo = (): UserInfo | null => {
  const stored = localStorage.getItem(STORAGE_KEYS.USER_INFO)
  return stored ? JSON.parse(stored) : null
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem(STORAGE_KEYS.TOKEN) || '')
  const tokenExpiresAt = ref<number | null>(
    localStorage.getItem('token_expires_at')
      ? Number(localStorage.getItem('token_expires_at'))
      : null
  )
  const userInfo = ref<UserInfo | null>(loadUserInfo())
  const username = computed(() => userInfo.value?.username || '')
  const tenantId = computed(() => userInfo.value?.tenantId)
  const permissions = computed(() => userInfo.value?.permissions || [])
  const isLoggedIn = computed(() => !!token.value && !isTokenExpired.value)
  const isTokenExpired = computed(() => {
    if (!tokenExpiresAt.value) return false // 无过期时间则不校验
    return Date.now() > tokenExpiresAt.value
  })

  const hasPermission = (permission: string): boolean => {
    return permissions.value.includes(permission)
  }

  const setToken = (newToken: string, expiresAt?: number) => {
    if (newToken === token.value) return
    token.value = newToken
    if (newToken) {
      localStorage.setItem(STORAGE_KEYS.TOKEN, newToken)
      if (expiresAt) {
        tokenExpiresAt.value = expiresAt
        localStorage.setItem('token_expires_at', String(expiresAt))
      }
    } else {
      localStorage.removeItem(STORAGE_KEYS.TOKEN)
      localStorage.removeItem('token_expires_at')
      tokenExpiresAt.value = null
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

  const login = (newToken: string, info?: UserInfo, expiresAt?: number) => {
    setToken(newToken, expiresAt)
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
    tokenExpiresAt,
    userInfo,
    username,
    tenantId,
    permissions,
    isLoggedIn,
    isTokenExpired,
    setToken,
    setUserInfo,
    login,
    logout,
    hasPermission
  }
})
