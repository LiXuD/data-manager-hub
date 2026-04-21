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
  return request.get('/caller/list', { params })
}

export const getCaller = (id: number) => {
  return request.get(`/caller/${id}`)
}

export const createCaller = (data: Caller) => {
  return request.post('/caller', data)
}

export const updateCaller = (id: number, data: Caller) => {
  return request.put(`/caller/${id}`, data)
}

export const deleteCaller = (id: number) => {
  return request.delete(`/caller/${id}`)
}

export const getApiKeyList = (callerId: number) => {
  return request.get('/caller/' + callerId + '/api-key/list')
}

export const createApiKey = (callerId: number) => {
  return request.post('/caller/' + callerId + '/api-key', { keyName: 'default' })
}

export const updateApiKeyStatus = (id: number, status: string) => {
  return request.patch(`/caller/api-key/${id}/status`, { status })
}

export const deleteApiKey = (id: number) => {
  return request.delete(`/caller/api-key/${id}`)
}
