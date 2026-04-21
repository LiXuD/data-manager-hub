import { request } from '@/utils/request'
import type { Vendor, PageResponse, ApiResponse } from '@/types'

export const getVendorList = (params: {
  page: number
  pageSize: number
  keyword?: string
  status?: string
}) => {
  return request.get<ApiResponse<PageResponse<Vendor>>>('/vendor/list', { params })
}

export const getVendorDetail = (id: string) => {
  return request.get<ApiResponse<Vendor>>(`/vendor/${id}`)
}

export const createVendor = (data: Partial<Vendor>) => {
  return request.post<ApiResponse<Vendor>>('/vendor', data)
}

export const updateVendor = (id: string, data: Partial<Vendor>) => {
  return request.put<ApiResponse<Vendor>>(`/vendor/${id}`, data)
}

export const deleteVendor = (id: string) => {
  return request.delete<ApiResponse<void>>(`/vendor/${id}`)
}

export const updateVendorStatus = (id: string, status: 'enabled' | 'disabled') => {
  return request.patch<ApiResponse<void>>(`/vendor/${id}/status`, { status })
}

export const testVendorConnection = (id: string) => {
  return request.post<ApiResponse<{ success: boolean; message: string }>>(`/vendor/${id}/test`)
}