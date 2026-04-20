import request from '@/utils/request'
import type { PageParams } from '@/types'

export interface Caller {
  id?: number
  callerCode: string
  callerName: string
  tenantId?: number
  callerType?: string
  description?: string
  contactPerson?: string
  contactPhone?: string
  status?: string
  createdAt?: string
  updatedAt?: string
}

export interface ApiKey {
  id?: number
  callerId: number
  apiKey: string
  apiSecret?: string
  rateLimit?: number
  quotaLimit?: number
  quotaUsed?: number
  status?: string
  expireTime?: string
  createdAt?: string
}

export const getCallerList = (params: PageParams & { keyword?: string; status?: string }) => {
  return request.get('/api/v1/caller/list', { params })
}

export const getCaller = (id: number) => {
  return request.get(`/api/v1/caller/${id}`)
}

export const createCaller = (data: Caller) => {
  return request.post('/api/v1/caller', data)
}

export const updateCaller = (data: Caller) => {
  return request.put('/api/v1/caller', data)
}

export const deleteCaller = (id: number) => {
  return request.delete(`/api/v1/caller/${id}`)
}

export const getApiKeyList = (callerId: number) => {
  return request.get('/api/v1/api-key/caller/' + callerId)
}

export const createApiKey = (callerId: number) => {
  return request.post('/api/v1/api-key/caller/' + callerId)
}

export const updateApiKeyStatus = (id: number, status: string) => {
  return request.put(`/api/v1/api-key/${id}/status?status=${status}`)
}

export const deleteApiKey = (id: number) => {
  return request.delete(`/api/v1/api-key/${id}`)
}
