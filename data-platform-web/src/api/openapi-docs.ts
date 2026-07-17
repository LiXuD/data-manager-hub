import { request } from '@/utils/request'
import type { OpenApiDocument } from '@/types'

export const getManagedOpenApiDocument = async (interfaceId: number) => {
  const response = await request.get<{ data: OpenApiDocument }>(`/openapi-docs/interfaces/${interfaceId}`)
  return response.data
}

export const downloadManagedOpenApi = async (interfaceId: number, format: 'json' | 'yaml') => {
  return request.get<Blob>(`/openapi-docs/interfaces/${interfaceId}/openapi`, {
    params: { format },
    responseType: 'blob'
  })
}
