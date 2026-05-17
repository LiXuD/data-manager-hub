import { request } from '@/utils/request'
import type { PageParams, CallRecord } from '@/types'

export interface CallStats {
  totalCount: number
  successCount: number
  failCount: number
  successRate: number
  avgResponseTime?: number
  averageDurationMs?: number
  todayCost?: number
  totalCost?: number
  cacheHitCount?: number
  realTimeCount?: number
  maxDurationMs?: number
  minDurationMs?: number
  byCaller?: Record<string, any>[]
  byCallerProduct?: Record<string, any>[]
  byScene?: Record<string, any>[]
  byCallerProductScene?: Record<string, any>[]
}

export const getCallRecordList = (params: PageParams & {
  callerId?: number
  vendorId?: number
  dataType?: string
  apiCode?: string
  productCode?: string
  sceneCode?: string
  cacheHit?: boolean
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

export const getCallDimensionStats = (params: {
  callerId?: number
  productCode?: string
  sceneCode?: string
  apiCode?: string
  vendorCode?: string
  dataType?: string
  cacheHit?: boolean
  startTime?: string
  endTime?: string
}) => {
  return request.get<{ data: CallStats }>('/call-record/dimension-stats', { params })
}

export const exportCallRecords = (params: {
  callerId?: number
  startTime?: string
  endTime?: string
}) => {
  return request.get<void>('/call-record/export', { params })
}
