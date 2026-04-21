import request from '@/utils/request'
import type { PageParams } from '@/types'

export interface LogRecord {
  id: number
  userId: number
  username: string
  operation: string
  module: string
  method: string
  params?: string
  result?: string
  ip?: string
  userAgent?: string
  duration?: number
  status: string
  errorMsg?: string
  createdAt: string
}

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
  return request.get(`/log/${id}`)
}

export const exportLogs = (params: {
  startTime: string
  endTime: string
  module?: string
  operation?: string
}) => {
  return request.get('/log/export', { params })
}

export const getLogStats = (params: { startTime?: string; endTime?: string }) => {
  return request.get('/log/stats', { params })
}