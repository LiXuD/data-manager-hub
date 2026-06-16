import { request } from '@/utils/request'
import type { PageParams } from '@/types'

export interface GraylogConfig {
  id: number
  configName: string
  configKey: string
  configValue: string
  description?: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface GraylogStream {
  id: number
  streamId: string
  streamName: string
  description?: string
  rules: string
  indexSetId?: string
  status: string
  createdAt: string
}

export const getGraylogConfigList = (params: PageParams & { keyword?: string; status?: string }) => {
  return request.get('/graylog/config/list', { params })
}

export const getGraylogConfigById = (id: number) => {
  return request.get<GraylogConfig>(`/graylog/config/${id}`)
}

export const createGraylogConfig = (data: Partial<GraylogConfig>) => {
  return request.post<GraylogConfig>('/graylog/config', data)
}

export const updateGraylogConfig = (id: number, data: Partial<GraylogConfig>) => {
  return request.put<GraylogConfig>(`/graylog/config/${id}`, data)
}

export const deleteGraylogConfig = (id: number) => {
  return request.delete<void>(`/graylog/config/${id}`)
}

export const testGraylogConnection = (id: number) => {
  return request.post<{ success: boolean; message?: string }>(`/graylog/config/${id}/test`)
}

// Stream管理
export const getStreamList = (params: PageParams & { keyword?: string }) => {
  return request.get('/graylog/stream/list', { params })
}

export const createStream = (data: Partial<GraylogStream>) => {
  return request.post<GraylogStream>('/graylog/stream', data)
}

export const updateStream = (id: number, data: Partial<GraylogStream>) => {
  return request.put<GraylogStream>(`/graylog/stream/${id}`, data)
}

export const deleteStream = (id: number) => {
  return request.delete<void>(`/graylog/stream/${id}`)
}

// 灰度规则
export interface GrayRule {
  id: number
  ruleName: string
  serviceName: string
  version: string
  weight: number
  conditionType: string
  conditionValue: string
  description: string
  status: string
  startTime: string
  endTime: string
  createdAt: string
}

export const getGrayRuleList = (params: PageParams & { serviceName?: string; status?: string }) => {
  return request.get('/graylog/list', { params })
}

export const createGrayRule = (data: Partial<GrayRule>) => {
  return request.post<GrayRule>('/graylog', data)
}

export const updateGrayRule = (id: number, data: Partial<GrayRule>) => {
  return request.put<GrayRule>(`/graylog/${id}`, data)
}

export const deleteGrayRule = (id: number) => {
  return request.delete<void>(`/graylog/${id}`)
}

export const updateGrayRuleStatus = (id: number, status: string) => {
  return request.patch<void>(`/graylog/${id}/status`, { status })
}