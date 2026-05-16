import { request } from '@/utils/request'
import type { PageParams, ListResponse, CallerDTO, ApiKeyDTO } from '@/types'

export type Caller = CallerDTO
export type ApiKey = ApiKeyDTO

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
  return request.get<ListResponse<ApiKeyDTO>>('/caller/' + callerId + '/api-key/list')
}

export const createApiKey = (callerId: number) => {
  return request.post<ApiKeyDTO>('/caller/' + callerId + '/api-key', { keyName: 'default' })
}

export const updateApiKeyStatus = (id: number, status: 'active' | 'inactive' | 'expired') => {
  return request.patch<void>(`/caller/api-key/${id}/status`, { status })
}

export const deleteApiKey = (id: number) => {
  return request.delete<void>(`/caller/api-key/${id}`)
}

export const getApiKeyInterfaces = (apiKeyId: number) => {
  return request.get<{ data: number[] }>(`/caller/api-key/${apiKeyId}/interfaces`)
}

export const assignApiKeyInterfaces = (apiKeyId: number, interfaceIds: number[]) => {
  return request.post(`/caller/api-key/${apiKeyId}/interfaces`, { interfaceIds })
}
