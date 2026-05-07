import request from '@/utils/request'
import type { PageParams } from '@/types'

export interface Permission {
  id: number
  permissionCode: string
  permissionName: string
  resource: string
  action: string
  description?: string
  createdAt?: string
  updatedAt?: string
}

export const getPermissionList = (params?: PageParams) => {
  return request.get('/permission/list', { params })
}

export const getPermissionById = (id: number) => {
  return request.get(`/permission/${id}`)
}

export const createPermission = (data: Partial<Permission>) => {
  return request.post('/permission', data)
}

export const updatePermission = (id: number, data: Partial<Permission>) => {
  return request.put(`/permission/${id}`, data)
}

export const deletePermission = (id: number) => {
  return request.delete(`/permission/${id}`)
}
