import { request } from '@/utils/request'
import type { PageParams, DataType } from '@/types'

// 重新导出类型，保持向后兼容
export type { DataType }

export const getDataTypeList = (params: PageParams & { keyword?: string; vendorId?: number; status?: string }) => {
  return request.get('/datatype/list', { params })
}

export const getDataTypeAll = () => {
  return request.get<{ data: DataType[] }>('/datatype/all')
}

export const getDataTypeById = (id: number) => {
  return request.get(`/datatype/${id}`)
}

export const createDataType = (data: Partial<DataType>) => {
  return request.post('/datatype', data)
}

export const updateDataType = (id: number, data: Partial<DataType>) => {
  return request.put(`/datatype/${id}`, data)
}

export const deleteDataType = (id: number) => {
  return request.delete(`/datatype/${id}`)
}

export const updateDataTypeStatus = (id: number, status: string) => {
  return request.patch(`/datatype/${id}/status`, { status })
}