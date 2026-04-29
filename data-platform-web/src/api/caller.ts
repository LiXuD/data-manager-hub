import { request } from '@/utils/request'
import type { PageParams, ListResponse } from '@/types'

export interface Caller {
  id?: number
  callerCode: string
  callerName: string
  tenantId?: number
  callerType?: string
  description?: string
  contactPerson?: string
  contactPhone?: string
  status?: 'active' | 'inactive'
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
  status?: 'active' | 'inactive' | 'expired'
  expireTime?: string
  createdAt?: string
}

export const getCallerList = (params: PageParams & { keyword?: string; status?: 'active' | 'inactive' }) => {
  return request.get<ListResponse<Caller>>('/caller/list', { params })
}

export const getCaller = (id: number) => {
  return request.get<Caller>(`/caller/${id}`)
}

export const createCaller = (data: Caller) => {
  return request.post<Caller>('/caller', data)
}

export const updateCaller = (id: number, data: Caller) => {
  return request.put<Caller>(`/caller/${id}`, data)
}

export const deleteCaller = (id: number) => {
  return request.delete<void>(`/caller/${id}`)
}

export const getApiKeyList = (callerId: number) => {
  return request.get<ListResponse<ApiKey>>('/caller/' + callerId + '/api-key/list')
}

export const createApiKey = (callerId: number) => {
  return request.post<ApiKey>('/caller/' + callerId + '/api-key', { keyName: 'default' })
}

export const updateApiKeyStatus = (id: number, status: 'active' | 'inactive' | 'expired') => {
  return request.patch<void>(`/caller/api-key/${id}/status`, { status })
}

export const deleteApiKey = (id: number) => {
  return request.delete<void>(`/caller/api-key/${id}`)
}
