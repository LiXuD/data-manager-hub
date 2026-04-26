import { request } from '@/utils/request'
import type { ApiInterface, ListResponse } from '@/types'

export const getInterfaceList = (params: {
  page: number
  pageSize: number
  vendorId?: number
  dataTypeId?: number
  status?: string
}) => {
  return request.get<ListResponse<ApiInterface>>('/interface/list', { params })
}

export const getInterfaceById = (id: number) => {
  return request.get<ApiInterface>(`/interface/${id}`)
}

export const getInterfacesByDataType = (dataTypeId: number) => {
  return request.get<ApiInterface[]>(`/interface/by-data-type/${dataTypeId}`)
}

export const createInterface = (data: Partial<ApiInterface>) => {
  return request.post<ApiInterface>('/interface', data)
}

export const updateInterface = (id: number, data: Partial<ApiInterface>) => {
  return request.put<ApiInterface>(`/interface/${id}`, data)
}

export const deleteInterface = (id: number) => {
  return request.delete<void>(`/interface/${id}`)
}

export const updateInterfaceStatus = (id: number, status: 'active' | 'inactive') => {
  return request.patch<void>(`/interface/${id}/status`, { status })
}
