import { request } from '@/utils/request'
import axios from 'axios'
import type { DataQueryRequest, DataQueryResponse, OpenApiQueryRequest } from '@/types'

export const executeQuery = (data: DataQueryRequest) => {
  return request.post<DataQueryResponse>('/data/query', data)
}

export const executeBatchQuery = (data: DataQueryRequest[]) => {
  return request.post<DataQueryResponse[]>('/data/batch-query', data)
}

export const getCacheStats = () => {
  return request.get<Record<string, any>>('/data/cache/stats')
}

export const clearCache = (params: { vendorCode: string; dataType: string; interfaceCode?: string }) => {
  return request.post<void>('/data/cache/clear', params)
}

const openApiClient = axios.create({
  baseURL: import.meta.env.PROD ? (import.meta.env.VITE_OPENAPI_BASE_URL || '') : '',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

export const executeOpenApiQuery = async (apiKey: string, data: OpenApiQueryRequest) => {
  const response = await openApiClient.post('/openapi/v1/query', data, {
    headers: {
      'X-Api-Key': apiKey
    }
  })
  const res = response.data
  if (res?.code === 200) {
    return res.data as DataQueryResponse
  }
  throw new Error(res?.message || res?.msg || '请求失败')
}
