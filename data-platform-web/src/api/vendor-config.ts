import { request } from '@/utils/request'
import type {
  VendorInterfaceConfig,
  ApiResponse,
  SecurityDirection,
  VendorSecurityCapability,
  VendorSecurityPreview,
  VendorSecurityStep,
  VendorSecurityStepList,
  VendorSecurityVersion
} from '@/types'

// 获取厂商配置列表
export const getVendorConfigList = (params?: {
  vendorId?: number
  dataTypeId?: number
  interfaceId?: number
  status?: string
}) => {
  return request.get<ApiResponse<VendorInterfaceConfig[]>>('/vendor/config/list', { params })
}

// 获取单个配置
export const getVendorConfigById = (id: number) => {
  return request.get<ApiResponse<VendorInterfaceConfig>>(`/vendor/config/${id}`)
}

// 根据厂商ID获取配置
export const getVendorConfigByVendor = (vendorId: number) => {
  return request.get<ApiResponse<VendorInterfaceConfig[]>>(`/vendor/config/vendor/${vendorId}`)
}

// 根据接口ID获取配置列表
export const getVendorConfigByInterface = (interfaceId: number) => {
  return request.get<ApiResponse<VendorInterfaceConfig[]>>(`/vendor/config/interface/${interfaceId}`)
}

// 创建配置
export const createVendorConfig = (data: Partial<VendorInterfaceConfig>) => {
  return request.post<ApiResponse<VendorInterfaceConfig>>('/vendor/config', data)
}

// 更新配置
export const updateVendorConfig = (id: number, data: Partial<VendorInterfaceConfig>) => {
  return request.put<ApiResponse<VendorInterfaceConfig>>(`/vendor/config/${id}`, data)
}

// 删除配置
export const deleteVendorConfig = (id: number) => {
  return request.delete<ApiResponse<void>>(`/vendor/config/${id}`)
}

// 更新配置状态
export const updateVendorConfigStatus = (id: number, status: 'active' | 'inactive') => {
  return request.patch<ApiResponse<void>>(`/vendor/config/${id}/status`, { status })
}

// 测试配置连接
export const testVendorConfig = (id: number) => {
  return request.post<ApiResponse<{ success: boolean; latency?: number; error?: string }>>(`/vendor/config/${id}/test`)
}

export const getSecurityCapabilities = () => {
  return request.get<ApiResponse<VendorSecurityCapability[]>>('/vendor/config/security-capabilities')
}

export const getVendorSecuritySteps = (configId: number) => {
  return request.get<ApiResponse<VendorSecurityStepList>>(`/vendor/config/${configId}/security-steps`)
}

export const saveVendorSecuritySteps = (configId: number, version: number, steps: VendorSecurityStep[]) => {
  return request.put<ApiResponse<VendorSecurityStepList>>(`/vendor/config/${configId}/security-steps`, { version, steps })
}

export const reorderVendorSecuritySteps = (
  configId: number,
  version: number,
  direction: SecurityDirection,
  orderedStepIds: number[]
) => {
  return request.put<ApiResponse<VendorSecurityStepList>>(`/vendor/config/${configId}/security-steps/order`, {
    version,
    direction,
    orderedStepIds
  })
}

export const previewVendorSecurity = (configId: number, data: {
  direction: SecurityDirection
  params: Record<string, any>
  headers?: Record<string, string>
  query?: Record<string, string>
  body?: string
  steps?: VendorSecurityStep[]
}) => {
  return request.post<ApiResponse<VendorSecurityPreview>>(`/vendor/config/${configId}/security-preview`, data)
}

export const testVendorSecurity = (configId: number) => {
  return request.post<ApiResponse<Record<string, any>>>(`/vendor/config/${configId}/security-test`)
}

export const getVendorSecurityVersions = (configId: number) => {
  return request.get<ApiResponse<VendorSecurityVersion[]>>(`/vendor/config/${configId}/security-versions`)
}

export const rollbackVendorSecurity = (configId: number, versionId: number, version: number) => {
  return request.post<ApiResponse<VendorSecurityStepList>>(
    `/vendor/config/${configId}/security-versions/${versionId}/rollback`,
    undefined,
    { params: { version } }
  )
}
