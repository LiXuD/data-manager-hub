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
  return request.get<{ data: ApiInterface[] }>(`/interface/by-data-type/${dataTypeId}`)
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

// 获取接口调用统计
export const getInterfaceStats = (id: number, params?: { startTime?: string; endTime?: string }) => {
  return request.get<InterfaceStats>(`/interface/${id}/stats`, { params })
}

// 获取接口每日调用统计
export const getInterfaceDailyStats = (id: number, params?: { startTime?: string; endTime?: string }) => {
  return request.get<DailyStatItem[]>(`/interface/${id}/stats/daily`, { params })
}

// 接口统计信息
export interface InterfaceStats {
  interfaceId: number
  interfaceCode: string
  interfaceName: string
  totalCalls: number
  successCalls: number
  avgLatency: number
  slowCalls: number
  startTime: string
  endTime: string
  // 计算字段
  failedCalls?: number
  successRate?: number
}

// 每日统计项
export interface DailyStatItem {
  date: string
  total_calls: number
  success_calls: number
  avg_latency: number
}
