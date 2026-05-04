import { request } from '@/utils/request'
import type { Vendor, ListResponse } from '@/types'

export const getVendorList = (params: {
  page: number
  pageSize: number
  keyword?: string
  status?: 'active' | 'inactive'
  vendorType?: string
}) => {
  return request.get<ListResponse<Vendor>>('/vendor/list', { params })
}

export const getVendorAll = () => {
  return request.get('/vendor/all')
}

export const getVendorDetail = (id: string | number) => {
  return request.get<Vendor>(`/vendor/${id}`)
}

export const createVendor = (data: Partial<Vendor>) => {
  return request.post<Vendor>('/vendor', data)
}

export const updateVendor = (id: string | number, data: Partial<Vendor>) => {
  return request.put<Vendor>(`/vendor/${id}`, data)
}

export const deleteVendor = (id: string | number) => {
  return request.delete<void>(`/vendor/${id}`)
}

export const updateVendorStatus = (id: string, status: 'active' | 'inactive') => {
  return request.patch<void>(`/vendor/${id}/status`, { status })
}

export const testVendorConnection = (id: string | number) => {
  return request.post<{ success: boolean; message: string }>(`/vendor/${id}/test`)
}
