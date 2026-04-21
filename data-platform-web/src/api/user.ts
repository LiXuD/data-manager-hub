import request from '@/utils/request'
import type { PageParams } from '@/types'

export interface User {
  id: number
  username: string
  realName?: string
  phone?: string
  email?: string
  tenantId?: number
  tenantName?: string
  status: string
  createdAt: string
  updatedAt: string
}

export const getUserList = (params: PageParams & { keyword?: string; status?: string; tenantId?: number }) => {
  return request.get('/user/list', { params })
}

export const getUserById = (id: number) => {
  return request.get(`/user/${id}`)
}

export const createUser = (data: Partial<User> & { password: string }) => {
  return request.post('/user', data)
}

export const updateUser = (id: number, data: Partial<User>) => {
  return request.put(`/user/${id}`, data)
}

export const deleteUser = (id: number) => {
  return request.delete(`/user/${id}`)
}

export const updateUserStatus = (id: number, status: string) => {
  return request.patch(`/user/${id}/status`, { status })
}

export const resetPassword = (id: number, newPassword: string) => {
  return request.post(`/user/${id}/reset-password`, { password: newPassword })
}

export const getUserRoles = (userId: number) => {
  return request.get(`/user/${userId}/roles`)
}

export const assignUserRoles = (userId: number, roleIds: number[]) => {
  return request.post(`/user/${userId}/roles`, { roleIds })
}