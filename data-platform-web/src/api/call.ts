import { request } from '@/utils/request'
import type { PageParams, CallRecord } from '@/types'

export interface CallStats {
  totalCount: number
  successCount: number
  failCount: number
  successRate: number
  avgResponseTime?: number
  todayCost?: number
}

export const getCallRecordList = (params: PageParams & {
  callerId?: number
  vendorId?: number
  dataType?: string
  success?: boolean
  startTime?: string
  endTime?: string
}) => {
  return request.get('/call-record/list', { params })
}

export const getCallRecordById = (id: number) => {
  return request.get<{ data: CallRecord }>(`/call-record/${id}`)
}

export const getCallStats = (params: { startTime?: string; endTime?: string }) => {
  return request.get<{ data: CallStats }>('/call-record/stats', { params })
}

export const exportCallRecords = (params: {
  callerId?: number
  startTime?: string
  endTime?: string
}) => {
  return request.get<void>('/call-record/export', { params })
}