import { request } from '@/utils/request'
import type { PageParams, DataType } from '@/types'

export const getDataTypeList = (params: PageParams & { keyword?: string; vendorId?: number; status?: string }) => {
  return request.get('/datatype/list', { params })
}

export const getDataTypeAll = () => {
  return request.get<{ data: DataType[] }>('/datatype/all')
}

export const getDataTypeById = (id: number) => {
  return request.get<{ data: DataType }>(`/datatype/${id}`)
}

export const createDataType = (data: Partial<DataType>) => {
  return request.post<{ data: DataType }>('/datatype', data)
}

export const updateDataType = (id: number, data: Partial<DataType>) => {
  return request.put<{ data: DataType }>(`/datatype/${id}`, data)
}

export const deleteDataType = (id: number) => {
  return request.delete<void>(`/datatype/${id}`)
}

export const updateDataTypeStatus = (id: number, status: string) => {
  return request.patch<void>(`/datatype/${id}/status`, { status })
}
