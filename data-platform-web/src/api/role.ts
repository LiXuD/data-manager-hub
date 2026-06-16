import { request } from '@/utils/request'
import type { PageParams, Role, Permission } from '@/types'

export const getRoleList = (params: PageParams & { keyword?: string; status?: string }) => {
  return request.get('/role/list', { params })
}

export const getRoleById = (id: number) => {
  return request.get<{ data: Role }>(`/role/${id}`)
}

export const createRole = (data: Partial<Role>) => {
  return request.post<{ data: Role }>('/role', data)
}

export const updateRole = (id: number, data: Partial<Role>) => {
  return request.put<{ data: Role }>(`/role/${id}`, data)
}

export const deleteRole = (id: number) => {
  return request.delete<void>(`/role/${id}`)
}

export const updateRoleStatus = (id: number, status: string) => {
  return request.patch<void>(`/role/${id}/status`, { status })
}

// 权限管理
export const getPermissionList = (params?: PageParams) => {
  return request.get('/role/permission/list', { params })
}

export const getRolePermissions = (roleId: number) => {
  return request.get<{ data: Permission[] }>(`/role/${roleId}/permissions`)
}

export const assignPermissions = (roleId: number, permissionIds: number[]) => {
  return request.post<void>(`/role/${roleId}/permissions`, { permissionIds })
}

export const getRolePermissionIds = (roleId: number) => {
  return request.get<{ data: number[] }>(`/role/${roleId}/permissionIds`)
}
