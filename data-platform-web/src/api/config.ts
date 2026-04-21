import request from '@/utils/request'
import type { PageParams } from '@/types'

export interface Config {
  id: number
  vendorId: number
  vendorName: string
  configKey: string
  configValue: string
  configType: string
  description?: string
  isEncrypted: boolean
  isActive: boolean
  status?: string
  createdAt: string
  updatedAt: string
}

export const getConfigList = (params: PageParams & { vendorId?: number; keyword?: string }) => {
  return request.get('/config/list', { params })
}

export const getConfigById = (id: number) => {
  return request.get(`/config/${id}`)
}

export const createConfig = (data: Partial<Config>) => {
  return request.post('/config', data)
}

export const updateConfig = (id: number, data: Partial<Config>) => {
  return request.put(`/config/${id}`, data)
}

export const deleteConfig = (id: number) => {
  return request.delete(`/config/${id}`)
}

export const getConfigByVendor = (vendorId: number) => {
  return request.get(`/config/vendor/${vendorId}`)
}

export const updateConfigStatus = (id: number, status: string) => {
  return request.patch(`/config/${id}/status`, { status })
}