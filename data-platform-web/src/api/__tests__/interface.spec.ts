import { beforeEach, describe, expect, it, vi } from 'vitest'

const mockedRequest = vi.hoisted(() => ({ put: vi.fn() }))

vi.mock('@/utils/request', () => ({ request: mockedRequest }))

import { updateVendorRouting } from '../interface'

describe('接口路由 API 契约', () => {
  beforeEach(() => vi.clearAllMocks())

  it('调用显式主备路由接口并允许备用配置为空', () => {
    const payload = { primaryVendorConfigId: 11, fallbackVendorConfigId: null }
    updateVendorRouting(42, payload)

    expect(mockedRequest.put).toHaveBeenCalledWith('/interface/42/vendor-routing', payload)
  })
})
