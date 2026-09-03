import { request } from '@/utils/request'
import type { CallSceneDTO } from '@/types'

export type CallScene = CallSceneDTO

export const getCallSceneList = () => {
  return request.get<{ data: CallSceneDTO[] }>('/call-scene/list')
}

export const createCallScene = (data: CallSceneDTO) => {
  return request.post<{ data: CallSceneDTO }>('/call-scene', data)
}

export const updateCallScene = (id: number, data: Pick<CallSceneDTO, 'sceneName' | 'description'>) => {
  return request.put<{ data: CallSceneDTO }>(`/call-scene/${id}`, data)
}

export const updateCallSceneStatus = (id: number, status: 'active' | 'inactive') => {
  return request.patch<{ data: CallSceneDTO }>(`/call-scene/${id}/status`, { status })
}
