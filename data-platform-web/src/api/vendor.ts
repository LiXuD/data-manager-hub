import { request } from '@/utils/request'
import type { Vendor, PageResponse, ApiResponse } from '@/types'

export const getVendorList = (params: {
  page: number
  pageSize: number
  keyword?: string
  status?: string
}) => {
  return request.get<ApiResponse<PageResponse<Vendor>>>('/api/v1/vendor/list', { params })
}

export const getVendorDetail = (id: string) => {
  return request.get<ApiResponse<Vendor>>(`/api/v1/vendor/${id}`)
}

export const createVendor = (data: Partial<Vendor>) => {
  return request.post<ApiResponse<Vendor>>('/api/v1/vendor', data)
}

export const updateVendor = (id: string, data: Partial<Vendor>) => {
  return request.put<ApiResponse<Vendor>>(`/api/v1/vendor/${id}`, data)
}

export const deleteVendor = (id: string) => {
  return request.delete<ApiResponse<void>>(`/api/v1/vendor/${id}`)
}

export const updateVendorStatus = (id: string, status: 'enabled' | 'disabled') => {
  return request.patch<ApiResponse<void>>(`/api/v1/vendor/${id}/status`, { status })
}

export const testVendorConnection = (id: string) => {
  return request.post<ApiResponse<{ success: boolean; message: string }>>(`/api/v1/vendor/${id}/test`)
}