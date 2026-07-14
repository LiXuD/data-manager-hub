import { request } from '@/utils/request'

export interface LoginResponse {
  token: string
  username: string
  userId: number
  tenantId?: number
  permissions?: string[]
  roles?: string[]
}

export const login = (data: { username: string; password: string }) => {
  return request.post<{ data: LoginResponse }>('/auth/login', data)
}

export interface ProfileInfo {
  userId: number
  username: string
  nickname?: string
  email?: string
  phone?: string
  tenantId?: number
  tenantName?: string
  lastLoginTime?: string
  roles: string[]
  permissions: string[]
}

export const getProfile = () => {
  return request.get<{ data: ProfileInfo }>('/auth/userinfo')
}

export const updateProfile = (data: { nickname?: string; email?: string; phone?: string }) => {
  return request.put<{ data: ProfileInfo }>('/auth/profile', data)
}

export const changePassword = (data: { oldPassword: string; newPassword: string }) => {
  return request.put<void>('/auth/password', data)
}
