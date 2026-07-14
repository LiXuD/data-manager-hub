import { request } from "@/utils/request"
import type { AlertRule, AlertRecord, AlertRuleQueryParams, AlertRecordQueryParams, PageResponse } from '@/types'

export const getAlertRuleList = (params: AlertRuleQueryParams) => {
  return request.get<PageResponse<AlertRule>>("/alert/rule/list", { params })
}

export const getAlertRule = (id: number) => {
  return request.get<AlertRule>(`/alert/rule/${id}`)
}

export const createAlertRule = (data: Partial<AlertRule>) => {
  return request.post<AlertRule>("/alert/rule", data)
}

export const updateAlertRule = (id: number, data: Partial<AlertRule>) => {
  return request.put<AlertRule>(`/alert/rule/${id}`, data)
}

export const deleteAlertRule = (id: number) => {
  return request.delete<void>(`/alert/rule/${id}`)
}

export const updateAlertRuleStatus = (id: number, status: string) => {
  return request.patch<void>(`/alert/rule/${id}/status`, { status })
}

export const getAlertRecordList = (params: AlertRecordQueryParams) => {
  return request.get<PageResponse<AlertRecord>>("/alert/record/list", { params })
}

export const getAlertRecord = (id: number) => {
  return request.get<AlertRecord>(`/alert/record/${id}`)
}

export const resolveAlertRecord = (id: number, resolution: string) => {
  return request.post<void>(`/alert/record/${id}/resolve`, { resolution })
}

export interface ServiceHealth {
  serviceName: string
  status: 'healthy' | 'unhealthy' | 'unknown'
  responseTime: number
  uptime: number
  instanceCount: number
  lastCheck: string
}

export interface HealthStats {
  totalServices: number
  healthyCount: number
  unhealthyCount: number
  avgResponseTime: number
}

export const getServiceHealth = (params?: { serviceName?: string; status?: string }) => {
  return request.get<{ data: { list: ServiceHealth[]; stats: HealthStats } }>('/alert/health/list', { params })
}

export const checkServiceHealth = (serviceName: string) => {
  return request.post<{ data: ServiceHealth }>(`/alert/health/${encodeURIComponent(serviceName)}/check`)
}
