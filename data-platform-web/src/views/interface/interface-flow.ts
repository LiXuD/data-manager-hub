import type { ApiInterface, Vendor, VendorConfigSummary, VendorRoutingUpdateRequest } from '@/types'

export const OPENAPI_QUERY_ENTRY = 'POST /openapi/v1/query'

export function filterUnboundVendors(
  vendors: Vendor[],
  configs: VendorConfigSummary[],
  editingVendorId?: number
) {
  const boundVendorIds = new Set(configs.map(config => Number(config.vendorId)))
  return vendors.filter(vendor => {
    const vendorId = Number(vendor.id)
    return vendorId === editingVendorId || !boundVendorIds.has(vendorId)
  })
}

export function buildVendorRoutingPayload(
  primaryVendorConfigId?: number,
  fallbackVendorConfigId?: number
): VendorRoutingUpdateRequest {
  if (primaryVendorConfigId == null) {
    throw new Error('PRIMARY_VENDOR_CONFIG_REQUIRED')
  }
  if (fallbackVendorConfigId != null && primaryVendorConfigId === fallbackVendorConfigId) {
    throw new Error('FALLBACK_VENDOR_CONFIG_MUST_DIFFER')
  }
  return {
    primaryVendorConfigId,
    fallbackVendorConfigId: fallbackVendorConfigId ?? null
  }
}

export function vendorDisplayName(name?: string) {
  return name?.trim() || '厂商名称未加载'
}

export function dataTypeDisplayName(name?: string) {
  return name?.trim() || '数据类型名称未加载'
}

export function interfaceRoutingSummary(apiInterface: Pick<ApiInterface, 'primaryVendorName' | 'fallbackVendorName'>) {
  const primary = apiInterface.primaryVendorName || '未配置'
  const fallback = apiInterface.fallbackVendorName || '无备用'
  return `${primary} → ${fallback}`
}

export interface VendorRoutingSelection {
  primaryVendorConfigId?: number
  fallbackVendorConfigId?: number
}

export function syncRoutingSelection(
  configs: VendorConfigSummary[],
  interfaceData?: Pick<ApiInterface, 'primaryVendorConfigId' | 'fallbackVendorConfigId'>
): VendorRoutingSelection {
  const primary = configs.find(config => config.routingRole === 'PRIMARY')?.id
  const fallback = configs.find(config => config.routingRole === 'FALLBACK')?.id
  const hasRoleInfo = configs.some(config => config.routingRole === 'PRIMARY' || config.routingRole === 'FALLBACK')
  return {
    primaryVendorConfigId: hasRoleInfo ? primary : interfaceData?.primaryVendorConfigId,
    fallbackVendorConfigId: hasRoleInfo ? fallback : interfaceData?.fallbackVendorConfigId
  }
}
