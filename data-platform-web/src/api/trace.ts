import { request } from '@/utils/request'
import type { DataLineage } from '@/types'

export const recordLineage = (data: {
  sourceType: string
  sourceId: number
  sourceName: string
  targetType: string
  targetId: number
  targetName: string
  relationType?: string
  transformRule?: string
}) => {
  return request.post<DataLineage>('/trace/lineage', data)
}

export const getUpstream = (type: string, id: number) => {
  return request.get<{ data: DataLineage[] }>('/trace/lineage/upstream', { params: { type, id } })
}

export const getDownstream = (type: string, id: number) => {
  return request.get<{ data: DataLineage[] }>('/trace/lineage/downstream', { params: { type, id } })
}

export const getFullLineage = (type: string, id: number) => {
  return request.get<{ data: DataLineage[] }>('/trace/lineage/full', { params: { type, id } })
}