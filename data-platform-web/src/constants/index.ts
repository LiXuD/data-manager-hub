/**
 * 全局常量定义
 */

// 用户状态
export const USER_STATUS = {
  ACTIVE: 'active',
  INACTIVE: 'inactive'
} as const

// 厂商类型
export const VENDOR_TYPE = {
  DATA_PROVIDER: 'data_provider',
  DATA_CONSUMER: 'data_consumer',
  BOTH: 'both'
} as const

// 厂商类型标签映射
export const VENDOR_TYPE_LABELS: Record<string, string> = {
  [VENDOR_TYPE.DATA_PROVIDER]: '数据提供方',
  [VENDOR_TYPE.DATA_CONSUMER]: '数据消费方',
  [VENDOR_TYPE.BOTH]: '双方'
}

// 厂商类型标签颜色
export const VENDOR_TYPE_TAGS: Record<string, string> = {
  [VENDOR_TYPE.DATA_PROVIDER]: 'primary',
  [VENDOR_TYPE.DATA_CONSUMER]: 'success',
  [VENDOR_TYPE.BOTH]: 'warning'
}

// 通用状态标签
export const STATUS_LABELS: Record<string, string> = {
  [USER_STATUS.ACTIVE]: '启用',
  [USER_STATUS.INACTIVE]: '禁用'
}

// 主题模式
export const THEME_MODE = {
  DARK: 'dark',
  LIGHT: 'light',
  AUTO: 'auto'
} as const

// 本地存储键名
export const STORAGE_KEYS = {
  TOKEN: 'token',
  USERNAME: 'username',
  USER_INFO: 'userInfo',
  THEME: 'theme',
  LANGUAGE: 'language',
  TIMEZONE: 'timezone',
  EMAIL_NOTIFY: 'emailNotify'
} as const
