import { request } from '@/utils/request'
import type { ApiInterface, InterfaceContract, InterfaceParam, ListResponse } from '@/types'

export const getInterfaceList = (params: {
  page: number
  pageSize: number
  vendorId?: number
  dataTypeId?: number
  status?: string
}) => {
  return request.get<ListResponse<ApiInterface>>('/interface/list', { params })
}

export const getInterfaceById = async (id: number) => {
  const response = await request.get<{ data: ApiInterface }>(`/interface/${id}`)
  return response.data
}

export const getInterfacesByDataType = (dataTypeId: number) => {
  return request.get<{ data: ApiInterface[] }>(`/interface/by-data-type/${dataTypeId}`)
}

export const getInterfaceOptions = (params: {
  vendorId?: number
  dataTypeId?: number
  status?: string
}) => {
  return request.get<{ data: ApiInterface[] }>('/interface/options', { params })
}

export const getInterfaceContract = async (interfaceId: number) => {
  const response = await request.get<{ data: InterfaceContract }>(`/interface/${interfaceId}/contract`)
  return response.data
}

export const getInterfaceParams = async (interfaceId: number): Promise<InterfaceParam[]> => {
  const contract = await getInterfaceContract(interfaceId)
  return contract.requestFields
}

export const saveInterfaceContract = async (interfaceId: number, contract: Partial<InterfaceContract>) => {
  const response = await request.put<{ data: InterfaceContract }>(`/interface/${interfaceId}/contract`, contract)
  return response.data
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
export const getInterfaceStats = async (id: number, params?: { startTime?: string; endTime?: string }) => {
  const response = await request.get<{ data: InterfaceStats }>(`/interface/${id}/stats`, { params })
  return response.data
}

// 获取接口每日调用统计
export const getInterfaceDailyStats = async (id: number, params?: { startTime?: string; endTime?: string }) => {
  const response = await request.get<{ data: DailyStatItem[] }>(`/interface/${id}/stats/daily`, { params })
  return response.data
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
