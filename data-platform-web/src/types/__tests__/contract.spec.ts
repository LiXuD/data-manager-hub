import { describe, expect, it } from 'vitest'
import type {
  DataType,
  PageParams,
  Tenant
} from '@/types'
import { extractPageData } from '@/utils/pagination'

// 后端目前有 com.dataplatform.common.result 与 com.dataplatform.api 两套结果契约。
// 前端通过统一的分页提取器兼容两者，避免把二者误写成不存在的混合结构。
describe('后端结果契约兼容', () => {
  it('提取 common PageResult 的 data/total/page/pageSize', () => {
    const response = {
      code: 200,
      message: 'success',
      data: [{ id: 1 }],
      total: 1,
      page: 1,
      pageSize: 10
    }

    expect(extractPageData<{ id: number }>(response)).toEqual({
      list: [{ id: 1 }],
      total: 1
    })
  })

  it('提取 api PageResult 的 data/list/total/pageNum/pageSize', () => {
    const response = {
      code: 200,
      msg: 'success',
      data: [{ id: 1 }],
      list: [{ id: 1 }],
      total: 1,
      pageNum: 1,
      pageSize: 10,
      totalPages: 1
    }

    expect(extractPageData<{ id: number }>(response)).toEqual({
      list: [{ id: 1 }],
      total: 1
    })
  })

  it('分页 data 为空时仍保留后端 total', () => {
    expect(extractPageData({ code: 200, message: 'success', data: [], total: 3 })).toEqual({
      list: [],
      total: 3
    })
  })
})

describe('前端实体与查询类型', () => {
  it('PageParams 查询参数全部可选', () => {
    const empty: PageParams = {}
    const full: PageParams = { page: 1, pageSize: 20, keyword: 'k', status: 'active' }
    expect(empty).toEqual({})
    expect(full.pageSize).toBe(20)
  })

  it('Tenant 实体保留后端必填字段与状态枚举', () => {
    const tenant: Tenant = {
      id: 1,
      tenantCode: 'T001',
      tenantName: '测试租户',
      tenantType: 'enterprise',
      status: 'active',
      contactPerson: '张三',
      contactEmail: 'zhangsan@example.com',
      maxApiKeys: 10,
      maxCallers: 5,
      createdAt: '2026-07-27T00:00:00',
      updatedAt: '2026-07-27T00:00:00'
    }
    expect(tenant.tenantType).toBe('enterprise')
    expect(['active', 'disabled']).toContain(tenant.status)
  })

  it('DataType 字段名与后端 DataType 实体对齐', () => {
    const dataType: DataType = {
      id: 1,
      dataTypeCode: 'programmer_history',
      dataTypeName: '程序员历史',
      status: 'active',
      createdAt: '2026-07-27T00:00:00',
      updatedAt: '2026-07-27T00:00:00'
    }
    expect(dataType.dataTypeCode).toBe('programmer_history')
    expect(dataType.dataTypeName).toBe('程序员历史')
  })
})
