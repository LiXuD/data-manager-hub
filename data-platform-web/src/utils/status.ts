// Status type mapping configurations for different domains
export const statusTypeMap = {
  // Billing status
  billing: {
    pending: 'warning',
    settled: 'success',
    overdue: 'danger',
    paid: 'success'
  } as Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'>,

  // Call status
  call: {
    success: 'success',
    failed: 'danger',
    timeout: 'warning',
    rate_limited: 'info'
  } as Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'>,

  // Active/Inactive status (vendors, graylog configs)
  active: {
    active: 'success',
    inactive: 'info',
    enabled: 'success',
    disabled: 'danger',
    expired: 'warning',
    pending: 'warning'
  } as Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'>,

  // Health status (monitor)
  health: {
    healthy: 'success',
    unhealthy: 'danger',
    unknown: 'warning'
  } as Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'>,

  // Enable/Disabled status
  enabled: {
    enabled: 'success',
    disabled: 'danger',
    success: 'success',
    failed: 'danger'
  } as Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'>,

  // Alert status
  alert: {
    pending: 'warning',
    resolved: 'success',
    firing: 'danger'
  } as Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'>,

  // HTTP methods
  httpMethod: {
    GET: 'success',
    POST: 'primary',
    PUT: 'warning',
    DELETE: 'danger',
    PATCH: 'info'
  } as Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'>
} as const

// Status text labels for different domains
export const statusLabels = {
  billing: { pending: '待结算', settled: '已结算', overdue: '逾期' },
  call: { success: '成功', failed: '失败', timeout: '超时', rate_limited: '限流' },
  active: { active: '启用中', inactive: '已禁用', expired: '已过期', pending: '待生效' },
  health: { healthy: '正常', unhealthy: '异常', unknown: '未知' },
  enabled: { success: '成功', failed: '失败', enabled: '启用', disabled: '禁用' },
  level: { info: '信息', warning: '警告', error: '错误', critical: '严重' },
  conditionType: { random: '随机流量', header: '请求头', caller: '调用方', ip: 'IP段' }
} as const

export type StatusDomain = keyof typeof statusTypeMap

/**
 * Get el-tag type for a status value based on domain context
 */
export function getStatusType(domain: StatusDomain, status: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map = statusTypeMap[domain]
  return map[status] || 'info'
}

/**
 * Get status text label from predefined labels
 */
export function getStatusText(
  domain: keyof typeof statusLabels,
  status: string
): string {
  const labels = statusLabels[domain]
  return (labels as Record<string, string>)[status] || status
}
