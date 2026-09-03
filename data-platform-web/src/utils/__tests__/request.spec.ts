import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError } from 'axios'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'

const elMessageError = vi.fn()
const logout = vi.fn()

vi.mock('element-plus', () => ({
  ElMessage: {
    error: (...args: unknown[]) => elMessageError(...args)
  }
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ logout })
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

function stubHttpError(body: unknown, status: number) {
  instance.defaults.adapter = async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
    const response: AxiosResponse = { data: body, status, statusText: 'Conflict', headers: {}, config }
    throw new AxiosError('Request failed', AxiosError.ERR_BAD_REQUEST, config, undefined, response)
  }
}

describe('utils/request 冒烟', () => {
  beforeEach(() => {
    const store = new Map<string, string>()
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => void store.set(key, value),
      removeItem: (key: string) => void store.delete(key)
    })
    vi.stubGlobal('window', {
      location: { pathname: '/dashboard', href: '/dashboard' }
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    elMessageError.mockClear()
    logout.mockClear()
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

  it('api.Result 的 msg 字段可作为业务错误提示', async () => {
    stubAdapter({ code: 4001, msg: '接口参数错误' })
    await expect(request.post('/ping', {})).rejects.toThrow('接口参数错误')
    expect(elMessageError).toHaveBeenCalledWith('接口参数错误')
  })

  it('HTTP 409 草稿 CAS 冲突给出明确刷新提示', async () => {
    stubHttpError({}, 409)

    await expect(request.put('/vendor/config/42/connector/draft', {
      expectedDraftVersion: 3,
      pipelineSnapshot: []
    })).rejects.toBeInstanceOf(AxiosError)

    expect(elMessageError).toHaveBeenCalledWith('数据已被其他用户修改，请刷新后重试')
  })

  it('请求异常只由拦截器提示一次', async () => {
    stubHttpError({}, 500)

    await expect(request.get('/interface/42/vendor-routing')).rejects.toBeInstanceOf(AxiosError)

    expect(elMessageError).toHaveBeenCalledTimes(1)
  })

  it('业务 401 清理登录态并跳转登录页', async () => {
    localStorage.setItem('token', 'expired-token')
    stubAdapter({ code: 401, msg: '登录已过期' })

    await expect(request.get('/ping')).rejects.toThrow('登录已过期')

    expect(logout).toHaveBeenCalledOnce()
    expect(window.location.href).toBe('/login')
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

  it('空业务响应不会在错误提示阶段再次抛出空引用异常', async () => {
    stubAdapter(null)

    await expect(request.get('/ping')).rejects.toThrow('请求失败')
    expect(elMessageError).toHaveBeenCalledWith('请求失败')
  })
})
