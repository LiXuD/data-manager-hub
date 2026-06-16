import { request } from '@/utils/request'
import type { PageParams, UserDTO, Role, CallerDTO, ListResponse } from '@/types'

export const getUserList = (params: PageParams & { keyword?: string; status?: string; tenantId?: number }) => {
  return request.get<ListResponse<UserDTO>>('/user/list', { params })
}

export const getUserById = (id: number) => {
  return request.get<UserDTO>(`/user/${id}`)
}

export const createUser = (data: Partial<UserDTO> & { password?: string }) => {
  return request.post<UserDTO>('/user', data)
}

export const updateUser = (id: number, data: Partial<UserDTO>) => {
  return request.put<UserDTO>(`/user/${id}`, data)
}

export const deleteUser = (id: number) => {
  return request.delete<void>(`/user/${id}`)
}

export const updateUserStatus = (id: number, status: string) => {
  return request.patch<void>(`/user/${id}/status`, { status })
}

export const resetPassword = (id: number, newPassword: string) => {
  return request.post<void>(`/user/${id}/reset-password`, { password: newPassword })
}

export const getUserRoles = (userId: number) => {
  return request.get<{ data: Role[] }>(`/user/${userId}/roles`)
}

export const assignUserRoles = (userId: number, roleIds: number[]) => {
  return request.post<void>(`/user/${userId}/roles`, { roleIds })
}

export const getRoleList = (params: PageParams) => {
  return request.get<ListResponse<Role>>('/role/list', { params })
}

export const getUserCallers = (userId: number) => {
  return request.get<{ data: CallerDTO[] }>(`/user/${userId}/callers`)
}

export const assignUserCallers = (userId: number, callerIds: number[]) => {
  return request.post<void>(`/user/${userId}/callers`, { callerIds })
}

export const getCallerList = (params: PageParams) => {
  return request.get<ListResponse<CallerDTO>>('/caller/list', { params })
}
