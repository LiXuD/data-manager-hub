import { request } from '@/utils/request'
import type { CallSceneDTO } from '@/types'

export type CallScene = CallSceneDTO

export const getCallSceneList = () => {
  return request.get<{ data: CallSceneDTO[] }>('/call-scene/list')
}

export const createCallScene = (data: CallSceneDTO) => {
  return request.post<{ data: CallSceneDTO }>('/call-scene', data)
}
