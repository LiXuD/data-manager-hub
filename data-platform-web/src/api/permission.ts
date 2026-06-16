import { request } from '@/utils/request'
import type { PageParams, Permission, ListResponse } from '@/types'

export const getPermissionList = (params?: PageParams) => {
  return request.get<ListResponse<Permission>>('/permission/list', { params })
}

export const getPermissionById = (id: number) => {
  return request.get<Permission>(`/permission/${id}`)
}

export const createPermission = (data: Partial<Permission>) => {
  return request.post<Permission>('/permission', data)
}

export const updatePermission = (id: number, data: Partial<Permission>) => {
  return request.put<Permission>(`/permission/${id}`, data)
}

export const deletePermission = (id: number) => {
  return request.delete<void>(`/permission/${id}`)
}

export const getAllPermissions = () => {
  return request.get<{ data: Permission[] }>('/permission/all')
}
