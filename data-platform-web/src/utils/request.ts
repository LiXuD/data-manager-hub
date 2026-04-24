import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

// Use relative path when Vite proxy is configured (dev mode)
// All /api/* requests will be proxied to target in vite.config.ts
const baseURL = import.meta.env.PROD ? import.meta.env.VITE_API_BASE_URL : ''

const instance: AxiosInstance = axios.create({
  baseURL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token')
    if (token && config.headers) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
instance.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data
    if (res.code === 0 || res.code === 200 || res.code === null || res.code === undefined) {
      return response
    }

    const msg = res.message || res.msg || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(new Error(msg))
  },
  (error) => {
    let msg = '请求失败'

    if (error.response) {
      const { status, data } = error.response

      switch (status) {
        case 400:
          msg = data?.message || data?.msg || '请求参数错误'
          break
        case 401:
          msg = '登录已过期，请重新登录'
          localStorage.removeItem('token')
          localStorage.removeItem('username')
          window.location.href = '/login'
          break
        case 403:
          msg = '没有权限访问'
          break
        case 404:
          msg = '请求的资源不存在'
          break
        case 408:
          msg = '请求超时'
          break
        case 500:
          msg = '服务器内部错误'
          break
        case 502:
          msg = '网关错误'
          break
        case 503:
          msg = '服务不可用'
          break
        case 504:
          msg = '网关超时'
          break
        default:
          msg = data?.message || data?.msg || '请求失败'
      }
    } else if (error.request) {
      msg = '网络连接失败，请检查网络'
    } else {
      msg = error.message || '请求失败'
    }

    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default instance

export const request = {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return instance.get(url, config).then(res => res.data)
  },
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return instance.post(url, data, config).then(res => res.data)
  },
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return instance.put(url, data, config).then(res => res.data)
  },
  patch<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return instance.patch(url, data, config).then(res => res.data)
  },
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return instance.delete(url, config).then(res => res.data)
  }
}