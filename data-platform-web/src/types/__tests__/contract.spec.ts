import { describe, expect, it } from 'vitest'
import type {
  ApiResponse,
  DataType,
  PageParams,
  PageResponse,
  Tenant
} from '@/types'

// 后端统一返回结构见 data-platform-common-contract 的 Result/PageResult
describe('types 契约映射冒烟', () => {
  it('ApiResponse 与后端 Result 的 code/message/data 对齐', () => {
    const res: ApiResponse<{ id: number }> = {
      code: 200,
      message: 'success',
      data: { id: 1 }
    }
    expect(Object.keys(res)).toEqual(expect.arrayContaining(['code', 'message', 'data']))
    expect(res.code).toBe(200)
    expect(res.data.id).toBe(1)
  })

  it('PageResponse 携带 list/total/page/pageSize 分页字段', () => {
    const page: PageResponse<Tenant> = {
      list: [],
      total: 0,
      page: 1,
      pageSize: 10
    }
    expect(page.list).toHaveLength(0)
    expect(page.total).toBe(0)
    expect(page.page).toBe(1)
    expect(page.pageSize).toBe(10)
  })

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
