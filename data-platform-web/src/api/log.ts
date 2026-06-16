import { request } from '@/utils/request'
import type { PageParams, LogRecord } from '@/types'

export const getLogList = (params: PageParams & {
  keyword?: string
  module?: string
  operation?: string
  startTime?: string
  endTime?: string
  status?: string
}) => {
  return request.get('/log/list', { params })
}

export const getLogById = (id: number) => {
  return request.get<{ data: LogRecord }>(`/log/${id}`)
}

export const exportLogs = (params: {
  startTime: string
  endTime: string
  module?: string
  operation?: string
}) => {
  return request.get<void>('/log/export', { params })
}

export const getLogStats = (params: { startTime?: string; endTime?: string }) => {
  return request.get<{ data: { totalCount: number; successCount: number; failCount: number; avgDuration: number } }>('/log/stats', { params })
}