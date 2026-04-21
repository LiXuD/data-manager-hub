import request from '@/utils/request'
import type { PageParams } from '@/types'

export interface Role {
  id: number
  roleCode: string
  roleName: string
  description?: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface Permission {
  id: number
  permissionCode: string
  permissionName: string
  resource: string
  action: string
  description?: string
}

export const getRoleList = (params: PageParams & { keyword?: string; status?: string }) => {
  return request.get('/role/list', { params })
}

export const getRoleById = (id: number) => {
  return request.get(`/role/${id}`)
}

export const createRole = (data: Partial<Role>) => {
  return request.post('/role', data)
}

export const updateRole = (id: number, data: Partial<Role>) => {
  return request.put(`/role/${id}`, data)
}

export const deleteRole = (id: number) => {
  return request.delete(`/role/${id}`)
}

export const updateRoleStatus = (id: number, status: string) => {
  return request.patch(`/role/${id}/status`, { status })
}

// 权限管理
export const getPermissionList = (params?: PageParams) => {
  return request.get('/role/permission/list', { params })
}

export const getRolePermissions = (roleId: number) => {
  return request.get(`/role/${roleId}/permissions`)
}

export const assignPermissions = (roleId: number, permissionIds: number[]) => {
  return request.post(`/role/${roleId}/permissions`, { permissionIds })
}