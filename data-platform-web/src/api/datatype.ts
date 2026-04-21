import request from '@/utils/request'
import type { PageParams } from '@/types'

export interface DataType {
  id: number
  typeCode: string
  typeName: string
  description: string
  vendorId: number
  vendorName: string
  schema?: string
  status: string
  createdAt: string
  updatedAt: string
}

export const getDataTypeList = (params: PageParams & { keyword?: string; vendorId?: number; status?: string }) => {
  return request.get('/datatype/list', { params })
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