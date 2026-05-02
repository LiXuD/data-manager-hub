/**
 * 全局常量定义
 */

// 从状态常量文件导出
export * from './status'
export * from './dataType'

// 导入用于本地定义
import { COMMON_STATUS } from './status'

// 用户状态（兼容旧代码，推荐使用 COMMON_STATUS）
export const USER_STATUS = {
  ACTIVE: COMMON_STATUS.ACTIVE,
  INACTIVE: COMMON_STATUS.INACTIVE
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
