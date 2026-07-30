import { request } from '@/utils/request'
import type { PageParams, ListResponse, CallerDTO, ApiKeyDTO, CallerProductDTO } from '@/types'

export type Caller = CallerDTO
export type ApiKey = ApiKeyDTO & { keyName?: string }
export type CallerProduct = CallerProductDTO

export interface CurrentUserApiKeyOption {
  id: number
  callerId: number
  callerCode: string
  callerName: string
  keyName: string
  maskedApiKey: string
}

export interface CurrentUserApiKeyOptions {
  hasAssociatedCaller: boolean
  options: CurrentUserApiKeyOption[]
}

export const getCallerList = (params: PageParams & { keyword?: string; status?: 'active' | 'inactive' }) => {
  return request.get<ListResponse<CallerDTO>>('/caller/list', { params })
}

export const getCaller = (id: number) => {
  return request.get<CallerDTO>(`/caller/${id}`)
}

export const createCaller = (data: CallerDTO) => {
  return request.post<CallerDTO>('/caller', data)
}

export const updateCaller = (id: number, data: CallerDTO) => {
  return request.put<CallerDTO>(`/caller/${id}`, data)
}

export const deleteCaller = (id: number) => {
  return request.delete<void>(`/caller/${id}`)
}

export const updateCallerStatus = (id: number, status: 'active' | 'inactive') => {
  return request.patch<void>(`/caller/${id}/status`, { status })
}

export const getApiKeyList = (callerId: number) => {
  return request.get<ListResponse<ApiKey>>('/caller/apikey/list', { params: { callerId } })
}

export const getCurrentUserApiKeyOptions = () => {
  return request.get<{ data: CurrentUserApiKeyOptions }>('/caller/apikey/current-user-options')
}

export interface ApiKeyCreateRequest {
  callerId: number
  name: string
  productIds: number[]
}

export const createApiKey = (data: ApiKeyCreateRequest) => {
  return request.post<{ data: ApiKey }>('/caller/apikey', data)
}

export interface ApiKeyRateLimitPolicy {
  rateLimitEnabled: boolean
  rateLimit: number
}

export const updateApiKeyRateLimit = (id: number, data: ApiKeyRateLimitPolicy) => {
  return request.put<{ data: ApiKey }>(`/caller/apikey/${id}/rate-limit`, data)
}

export const updateApiKeyStatus = (id: number, status: 'active' | 'revoked' | 'expired') => {
  return request.put<void>(`/caller/apikey/${id}/status`, { status })
}

export const deleteApiKey = (id: number) => {
  return request.delete<void>(`/caller/apikey/${id}`)
}

export const getApiKeyInterfaces = (apiKeyId: number) => {
  return request.get<{ data: number[] }>(`/caller/apikey/${apiKeyId}/interfaces`)
}

export const assignApiKeyInterfaces = (apiKeyId: number, interfaceIds: number[]) => {
  return request.post<void>(`/caller/apikey/${apiKeyId}/interfaces`, interfaceIds)
}

export const getCallerProducts = (callerId: number) => {
  return request.get<{ data: CallerProductDTO[] }>(`/caller/${callerId}/products`)
}

export const createCallerProduct = (callerId: number, data: CallerProductDTO) => {
  return request.post<{ data: CallerProductDTO }>(`/caller/${callerId}/products`, data)
}

export const getApiKeyProducts = (apiKeyId: number) => {
  return request.get<{ data: number[] }>(`/caller/apikey/${apiKeyId}/products`)
}

export const assignApiKeyProducts = (apiKeyId: number, productIds: number[]) => {
  return request.post<void>(`/caller/apikey/${apiKeyId}/products`, productIds)
}
