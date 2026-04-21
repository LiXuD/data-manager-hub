import { request } from '@/utils/request'
import type { Tenant, PageResponse, ApiResponse } from '@/types'

export const getTenantList = (params: {
  page: number
  pageSize: number
  keyword?: string
  status?: string
}) => {
  return request.get<ApiResponse<PageResponse<Tenant>>>('/tenant/list', { params })
}

export const getTenantDetail = (id: string) => {
  return request.get<ApiResponse<Tenant>>(`/tenant/${id}`)
}

export const createTenant = (data: Partial<Tenant>) => {
  return request.post<ApiResponse<Tenant>>('/tenant', data)
}

export const updateTenant = (id: string, data: Partial<Tenant>) => {
  return request.put<ApiResponse<Tenant>>(`/tenant/${id}`, data)
}

export const deleteTenant = (id: string) => {
  return request.delete<ApiResponse<void>>(`/tenant/${id}`)
}

export const updateTenantStatus = (id: string, status: 'enabled' | 'disabled') => {
  return request.patch<ApiResponse<void>>(`/tenant/${id}/status`, { status })
}