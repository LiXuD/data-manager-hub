import { request } from '@/utils/request'

export interface LoginResponse {
  token: string
  username: string
  userId: number
  tenantId?: number
  permissions?: string[]
  roles?: string[]
}

export const login = (data: { username: string; password: string }) => {
  return request.post<{ data: LoginResponse }>('/auth/login', data)
}
