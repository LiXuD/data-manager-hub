import { request } from '@/utils/request'

export interface EncryptedField {
  id: number
  tableName: string
  fieldName: string
  fieldType: string
  algorithm: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export const encryptText = (plainText: string, tableName: string) => {
  return request.post<{ data: string }>('/security/encryption/encrypt', null, { params: { plainText, tableName } })
}

export const decryptText = (encryptedText: string, tableName: string) => {
  return request.post<{ data: string }>('/security/encryption/decrypt', null, { params: { encryptedText, tableName } })
}

export const rotateKey = (tableName: string) => {
  return request.post<void>(`/security/encryption/rotate/${tableName}`)
}