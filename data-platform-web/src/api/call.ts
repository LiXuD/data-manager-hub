import request from '@/utils/request'
import type { PageParams } from '@/types'

export interface CallRecord {
  id: number
  callerId: number
  callerName: string
  vendorId: number
  vendorName: string
  dataType: string
  success: boolean
  errorCode?: string
  errorMessage?: string
  cost: number
  callTime: string
  responseTime: number
}

export interface CallStats {
  totalCount: number
  successCount: number
  failCount: number
  successRate: number
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
  return request.get(`/call-record/${id}`)
}

export const getCallStats = (params: { startTime?: string; endTime?: string }) => {
  return request.get('/call-record/stats', { params })
}

export const exportCallRecords = (params: {
  callerId?: number
  startTime?: string
  endTime?: string
}) => {
  return request.get('/call-record/export', { params })
}