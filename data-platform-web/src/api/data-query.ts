import axios from 'axios'
import type { DataQueryResponse, OpenApiQueryRequest } from '@/types'

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
