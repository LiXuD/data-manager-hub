import { describe, expect, it } from 'vitest'
import type { Vendor, VendorConfigSummary } from '@/types'
import {
  buildVendorRoutingPayload,
  dataTypeDisplayName,
  filterUnboundVendors,
  interfaceRoutingSummary,
  OPENAPI_QUERY_ENTRY,
  syncRoutingSelection,
  vendorDisplayName
} from '../interface-flow'

const vendor = (id: number, vendorName: string): Vendor => ({
  id,
  vendorName,
  vendorCode: `V-${id}`,
  vendorType: 'EXTERNAL',
  contactPerson: '',
  contactPhone: '',
  contactEmail: '',
  status: 'active'
})

const config = (id: number, vendorId: number, routingRole?: string): VendorConfigSummary => ({
  id,
  vendorId,
  dataTypeId: 3,
  interfaceId: 11,
  timeout: 30000,
  retryCount: 3,
  circuitThreshold: 5,
  circuitTimeout: 60,
  runtimeMode: 'PLUGIN',
  connectorVersion: 1,
  status: 'inactive',
  createdAt: '',
  updatedAt: '',
  routingRole
})

describe('接口—厂商连接器流程', () => {
  it('固定使用 OpenAPI 查询入口', () => {
    expect(OPENAPI_QUERY_ENTRY).toBe('POST /openapi/v1/query')
  })

  it('绑定厂商时过滤已绑定厂商，编辑时保留当前厂商', () => {
    const vendors = [vendor(1, '主厂商'), vendor(2, '备用厂商'), vendor(3, '未绑定厂商')]
    const configs = [config(11, 1), config(12, 2)]

    expect(filterUnboundVendors(vendors, configs).map(item => item.id)).toEqual([3])
    expect(filterUnboundVendors(vendors, configs, 2).map(item => item.id)).toEqual([2, 3])
  })

  it('数据类型名称只从接口数据展示，缺失时给出稳定兜底', () => {
    expect(dataTypeDisplayName('企业信息')).toBe('企业信息')
    expect(dataTypeDisplayName()).toBe('数据类型名称未加载')
  })

  it('生成主备路由 payload，并拒绝相同主备配置', () => {
    expect(buildVendorRoutingPayload(11, 12)).toEqual({
      primaryVendorConfigId: 11,
      fallbackVendorConfigId: 12
    })
    expect(buildVendorRoutingPayload(11)).toEqual({
      primaryVendorConfigId: 11,
      fallbackVendorConfigId: null
    })
    expect(() => buildVendorRoutingPayload(11, 11)).toThrow('FALLBACK_VENDOR_CONFIG_MUST_DIFFER')
  })

  it('卡片只显示真实名称，缺名不把 ID 当名称', () => {
    expect(vendorDisplayName('供应商 A')).toBe('供应商 A')
    expect(vendorDisplayName()).toBe('厂商名称未加载')
    expect(interfaceRoutingSummary({
      primaryVendorName: '主厂商',
      fallbackVendorName: '备用厂商'
    })).toBe('主厂商 → 备用厂商')
  })

  it('保存后优先使用配置角色，避免旧接口属性覆盖新主备', () => {
    expect(syncRoutingSelection([
      config(21, 1, 'FALLBACK'),
      config(22, 2, 'PRIMARY')
    ], {
      primaryVendorConfigId: 11,
      fallbackVendorConfigId: 12
    })).toEqual({
      primaryVendorConfigId: 22,
      fallbackVendorConfigId: 21
    })
  })

  it('配置角色没有备用时保持未选择状态', () => {
    expect(syncRoutingSelection([
      config(22, 2, 'PRIMARY')
    ], {
      primaryVendorConfigId: 11,
      fallbackVendorConfigId: 12
    })).toEqual({
      primaryVendorConfigId: 22,
      fallbackVendorConfigId: undefined
    })
  })
})
