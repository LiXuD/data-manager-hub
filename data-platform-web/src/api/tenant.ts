import { request } from '@/utils/request'
import type { Tenant, ListResponse } from '@/types'

export const getTenantList = (params: {
  page: number
  pageSize: number
  keyword?: string
  status?: 'active' | 'disabled'
}) => {
  return request.get<ListResponse<Tenant>>('/tenant/list', { params })
}

export const getTenantDetail = (id: string) => {
  return request.get<Tenant>(`/tenant/${id}`)
}

export const createTenant = (data: Partial<Tenant>) => {
  return request.post<Tenant>('/tenant', data)
}

export const updateTenant = (id: string | number, data: Partial<Tenant>) => {
  return request.put<Tenant>(`/tenant/${id}`, data)
}

export const deleteTenant = (id: string | number) => {
  return request.delete<void>(`/tenant/${id}`)
}

export const updateTenantStatus = (id: string, status: 'active' | 'disabled') => {
  return request.patch<void>(`/tenant/${id}/status`, { status })
}
