import { request } from '@/utils/request'
import type { DataQueryRequest, DataQueryResponse } from '@/types'

export const executeQuery = (data: DataQueryRequest) => {
  return request.post<DataQueryResponse>('/data/query', data)
}

export const executeBatchQuery = (data: DataQueryRequest[]) => {
  return request.post<DataQueryResponse[]>('/data/batch-query', data)
}

export const getCacheStats = () => {
  return request.get<Record<string, any>>('/data/cache/stats')
}

export const clearCache = (params: { vendorCode: string; dataType: string; interfaceCode?: string }) => {
  return request.post<void>('/data/cache/clear', params)
}
