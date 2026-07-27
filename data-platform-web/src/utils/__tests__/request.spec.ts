import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'

const elMessageError = vi.fn()

vi.mock('element-plus', () => ({
  ElMessage: {
    error: (...args: unknown[]) => elMessageError(...args)
  }
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ logout: vi.fn() })
}))

import instance, { request } from '@/utils/request'

// 用假 adapter 走完整的拦截器链路，不发真实网络请求
function stubAdapter(body: unknown, status = 200) {
  const captured: { config?: InternalAxiosRequestConfig } = {}
  instance.defaults.adapter = async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
    captured.config = config
    return { data: body, status, statusText: 'OK', headers: {}, config }
  }
  return captured
}

describe('utils/request 冒烟', () => {
  beforeEach(() => {
    const store = new Map<string, string>()
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => void store.set(key, value),
      removeItem: (key: string) => void store.delete(key)
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    elMessageError.mockClear()
  })

  it('开发态 baseURL 指向 /api/v1 且默认 JSON 请求头', () => {
    expect(instance.defaults.baseURL).toBe('/api/v1')
    expect(instance.defaults.timeout).toBe(30000)
    expect(instance.defaults.headers['Content-Type']).toBe('application/json')
  })

  it('暴露 get/post/put/patch/delete 五个契约方法', () => {
    for (const method of ['get', 'post', 'put', 'patch', 'delete'] as const) {
      expect(typeof request[method]).toBe('function')
    }
  })

  it('存在 token 时请求拦截器注入 Bearer Authorization', async () => {
    localStorage.setItem('token', 'unit-test-token')
    const captured = stubAdapter({ code: 200, message: 'success', data: null })
    await request.get('/ping')
    expect(captured.config?.headers['Authorization']).toBe('Bearer unit-test-token')
  })

  it('code === 200 时透传完整响应体', async () => {
    stubAdapter({ code: 200, message: 'success', data: { id: 1 } })
    const res = await request.get<{ code: number; data: { id: number } }>('/ping')
    expect(res.code).toBe(200)
    expect(res.data).toEqual({ id: 1 })
  })

  it('业务失败（code !== 200）时提示错误并拒绝 Promise', async () => {
    stubAdapter({ code: 4001, message: '参数错误' })
    await expect(request.post('/ping', {})).rejects.toThrow('参数错误')
    expect(elMessageError).toHaveBeenCalledWith('参数错误')
  })

  it('blob 响应不做统一结果解包', async () => {
    stubAdapter('raw-bytes')
    const res = await request.get<string>('/export', { responseType: 'blob' })
    expect(res).toBe('raw-bytes')
  })

  it('无 code 字段的对象响应原样返回', async () => {
    stubAdapter({ status: 'UP' })
    const res = await request.get<{ status: string }>('/actuator/health')
    expect(res).toEqual({ status: 'UP' })
  })
})
