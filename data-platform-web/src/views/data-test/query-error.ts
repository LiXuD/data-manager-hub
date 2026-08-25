export interface QueryFailure {
  errorCode: string
  errorMsg: string
}

interface HttpErrorLike {
  message?: string
  response?: {
    status?: number
    data?: {
      message?: string
      msg?: string
    }
  }
}

const HTTP_ERROR_MESSAGES: Record<number, string> = {
  400: '查询参数错误',
  401: '登录已过期，请重新登录',
  403: '当前账号无权执行该查询',
  404: '数据查询服务路由不存在，请检查网关配置是否已发布',
  408: '查询请求超时',
  500: '数据查询服务内部错误',
  502: '数据查询网关错误',
  503: '数据查询服务不可用',
  504: '数据查询网关超时'
}

export const toQueryFailure = (error: unknown): QueryFailure => {
  const httpError = error as HttpErrorLike
  const status = httpError?.response?.status
  const responseData = httpError?.response?.data
  const responseMessage = responseData?.message || responseData?.msg

  if (status !== undefined) {
    return {
      errorCode: `HTTP_${status}`,
      errorMsg: responseMessage || HTTP_ERROR_MESSAGES[status] || `查询请求失败（HTTP ${status}）`
    }
  }

  return {
    errorCode: 'NETWORK_ERROR',
    errorMsg: httpError?.message || '网络连接失败，请检查网络'
  }
}
