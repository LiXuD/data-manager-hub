import request from "@/utils/request"
import type { AlertRule, AlertRecord, AlertRuleQueryParams, AlertRecordQueryParams, PageResult } from '@/types'

export const getAlertRuleList = (params: AlertRuleQueryParams) => {
  return request.get<PageResult<AlertRule>>("/alert/rule/list", { params })
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
  return request.get<PageResult<AlertRecord>>("/alert/record/list", { params })
}

export const getAlertRecord = (id: number) => {
  return request.get<AlertRecord>(`/alert/record/${id}`)
}

export const resolveAlertRecord = (id: number, resolution?: string) => {
  return request.patch<void>(`/alert/record/${id}/resolve`, { resolution })
}
