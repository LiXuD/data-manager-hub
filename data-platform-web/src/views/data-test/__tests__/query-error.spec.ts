import { describe, expect, it } from 'vitest'
import { toQueryFailure } from '../query-error'

describe('toQueryFailure', () => {
  it('preserves the backend message and exposes the HTTP status', () => {
    expect(toQueryFailure({
      response: {
        status: 403,
        data: { message: '无权使用该API Key' }
      }
    })).toEqual({
      errorCode: 'HTTP_403',
      errorMsg: '无权使用该API Key'
    })
  })

  it('explains a missing gateway route instead of reporting a generic network error', () => {
    expect(toQueryFailure({ response: { status: 404 } })).toEqual({
      errorCode: 'HTTP_404',
      errorMsg: '数据查询服务路由不存在，请检查网关配置是否已发布'
    })
  })

  it('keeps network failures distinct from HTTP failures', () => {
    expect(toQueryFailure({ message: 'Network Error' })).toEqual({
      errorCode: 'NETWORK_ERROR',
      errorMsg: 'Network Error'
    })
  })
})
