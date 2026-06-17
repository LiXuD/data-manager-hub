/**
 * 状态常量定义
 * 与后端 com.dataplatform.common.enums 包下的枚举保持同步
 */

// ==================== 通用状态 ====================
/** 通用状态 - 用于厂商、数据类型、调用方、用户、角色、接口等 */
export const COMMON_STATUS = {
  ACTIVE: 'active',
  INACTIVE: 'inactive'
} as const
export type CommonStatus = typeof COMMON_STATUS[keyof typeof COMMON_STATUS]

export const COMMON_STATUS_OPTIONS = [
  { label: '启用', value: COMMON_STATUS.ACTIVE },
  { label: '禁用', value: COMMON_STATUS.INACTIVE }
] as const

// ==================== 启用状态 ====================
/** 启用状态 - 用于配置项、开关等 */
export const ENABLE_STATUS = {
  ENABLED: 'enabled',
  DISABLED: 'disabled'
} as const
export type EnableStatus = typeof ENABLE_STATUS[keyof typeof ENABLE_STATUS]

export const ENABLE_STATUS_OPTIONS = [
  { label: '已启用', value: ENABLE_STATUS.ENABLED },
  { label: '已禁用', value: ENABLE_STATUS.DISABLED }
] as const

// ==================== 调用状态 ====================
/** 调用状态 - 用于调用记录 */
export const CALL_STATUS = {
  SUCCESS: 'success',
  FAILED: 'failed',
  TIMEOUT: 'timeout',
  RATE_LIMITED: 'rate_limited'
} as const
export type CallStatus = typeof CALL_STATUS[keyof typeof CALL_STATUS]

export const CALL_STATUS_OPTIONS = [
  { label: '成功', value: CALL_STATUS.SUCCESS },
  { label: '失败', value: CALL_STATUS.FAILED },
  { label: '超时', value: CALL_STATUS.TIMEOUT },
  { label: '限流', value: CALL_STATUS.RATE_LIMITED }
] as const

// ==================== 灰度规则状态 ====================
/** 灰度规则状态 */
export const GRAY_RULE_STATUS = {
  ACTIVE: 'active',
  INACTIVE: 'inactive',
  EXPIRED: 'expired',
  PENDING: 'pending'
} as const
export type GrayRuleStatus = typeof GRAY_RULE_STATUS[keyof typeof GRAY_RULE_STATUS]

export const GRAY_RULE_STATUS_OPTIONS = [
  { label: '启用中', value: GRAY_RULE_STATUS.ACTIVE },
  { label: '已禁用', value: GRAY_RULE_STATUS.INACTIVE },
  { label: '已过期', value: GRAY_RULE_STATUS.EXPIRED },
  { label: '待生效', value: GRAY_RULE_STATUS.PENDING }
] as const

// ==================== API Key 状态 ====================
/** API Key 状态 */
export const API_KEY_STATUS = {
  ACTIVE: 'active',
  EXPIRED: 'expired',
  REVOKED: 'revoked'
} as const
export type ApiKeyStatus = typeof API_KEY_STATUS[keyof typeof API_KEY_STATUS]

export const API_KEY_STATUS_OPTIONS = [
  { label: '有效', value: API_KEY_STATUS.ACTIVE },
  { label: '已过期', value: API_KEY_STATUS.EXPIRED },
  { label: '已吊销', value: API_KEY_STATUS.REVOKED }
] as const

// ==================== 账单状态 ====================
/** 账单状态 */
export const BILLING_STATUS = {
  PENDING: 'pending',
  SETTLED: 'settled',
  OVERDUE: 'overdue',
  PAID: 'paid'
} as const
export type BillingStatus = typeof BILLING_STATUS[keyof typeof BILLING_STATUS]

export const BILLING_STATUS_OPTIONS = [
  { label: '待结算', value: BILLING_STATUS.PENDING },
  { label: '已结算', value: BILLING_STATUS.SETTLED },
  { label: '逾期', value: BILLING_STATUS.OVERDUE },
  { label: '已支付', value: BILLING_STATUS.PAID }
] as const

// ==================== 告警状态 ====================
/** 告警规则状态 */
export const ALERT_STATUS = {
  ACTIVE: 'active',
  INACTIVE: 'inactive',
  FIRING: 'firing',
  RESOLVED: 'resolved'
} as const
export type AlertStatus = typeof ALERT_STATUS[keyof typeof ALERT_STATUS]

export const ALERT_STATUS_OPTIONS = [
  { label: '启用中', value: ALERT_STATUS.ACTIVE },
  { label: '已禁用', value: ALERT_STATUS.INACTIVE },
  { label: '触发中', value: ALERT_STATUS.FIRING },
  { label: '已恢复', value: ALERT_STATUS.RESOLVED }
] as const

// ==================== 条件类型 ====================
/** 灰度规则条件类型 */
export const CONDITION_TYPE = {
  RANDOM: 'random',
  HEADER: 'header',
  CALLER: 'caller',
  IP: 'ip'
} as const
export type ConditionType = typeof CONDITION_TYPE[keyof typeof CONDITION_TYPE]

export const CONDITION_TYPE_OPTIONS = [
  { label: '随机流量', value: CONDITION_TYPE.RANDOM },
  { label: '请求头', value: CONDITION_TYPE.HEADER },
  { label: '调用方', value: CONDITION_TYPE.CALLER },
  { label: 'IP段', value: CONDITION_TYPE.IP }
] as const
