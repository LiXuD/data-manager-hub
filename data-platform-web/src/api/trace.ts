import request from '@/utils/request'

export interface DataLineage {
  id: number
  sourceType: string
  sourceId: number
  sourceName: string
  targetType: string
  targetId: number
  targetName: string
  relationType: string
  transformRule?: string
  createdAt: string
}

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
  return request.post('/trace/lineage', data)
}

export const getUpstream = (type: string, id: number) => {
  return request.get('/trace/lineage/upstream', { params: { type, id } })
}

export const getDownstream = (type: string, id: number) => {
  return request.get('/trace/lineage/downstream', { params: { type, id } })
}

export const getFullLineage = (type: string, id: number) => {
  return request.get('/trace/lineage/full', { params: { type, id } })
}