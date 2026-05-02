// 告警规则
export interface AlertRule {
  id: number
  ruleName: string
  ruleType: string
  targetType: string
  targetId?: number
  conditionType: string
  thresholdValue: number
  timeWindowMinutes: number
  notifyChannels: string
  status: 'active' | 'inactive'
  createdBy?: number
  createdAt: string
  updatedAt: string
  // 兼容字段
  metric?: string
  threshold?: number
  condition?: string
  level?: string
  operator?: string
}

// 告警记录
export interface AlertRecord {
  id: number
  ruleId: number
  tenantId?: number
  alertType: string
  alertTitle: string
  alertTime: string
  level: string
  alertMessage: string
  triggeredValue?: number
  status: 'pending' | 'resolved'
  resolvedAt?: string
  resolvedBy?: number
  createdAt: string
}

// 租户相关类型
export interface Tenant {
  id: number
  tenantCode: string
  tenantName: string
  tenantType: 'enterprise' | 'personal'
  status: 'active' | 'disabled'
  contactPerson: string
  contactPhone?: string
  contactEmail: string
  maxApiKeys: number
  maxCallers: number
  createdBy?: number
  createdAt: string
  updatedAt: string
}

// 厂商相关类型
export interface Vendor {
  id: string | number
  vendorName: string
  vendorCode: string
  vendorType: string
  contactPerson: string
  contactPhone: string
  contactEmail: string
  url?: string
  authType?: string
  status: 'active' | 'inactive' | 'enabled' | 'disabled'
  version?: string
  contractStart?: string
  contractEnd?: string
  createdAt?: string
  updatedAt?: string
}

// 调用方相关类型
export interface Caller {
  id: string
  name: string
  code: string
  tenantId: string
  tenantName: string
  contact: string
  email: string
  apiKey: string
  status: 'enabled' | 'disabled'
  budget: number
  quota: number
  usedQuota: number
  description?: string
  createdAt: string
  updatedAt: string
}

// API Key相关类型
export interface ApiKey {
  id: string
  callerId: string
  key: string
  name: string
  status: 'active' | 'revoked' | 'expired'
  expireTime: string
  lastUsedTime?: string
  createdAt: string
}

export interface CreateApiKeyRequest {
  callerId: string
  name: string
  expireDays: number
}

export interface ApiKeyListResponse {
  list: ApiKey[]
  total: number
}

// 计费相关类型
export interface BillingRule {
  id: string
  vendorId: string
  vendorName: string
  dataTypeId: string
  dataTypeName: string
  pricePerCall: number
  minPrice: number
  maxPrice: number
  discountThreshold: number
  discountRate: number
  status: 'enabled' | 'disabled'
  createdAt: string
  updatedAt: string
}

export interface BillingRecord {
  id: string
  tenantId: string
  tenantName: string
  callerId: string
  callerName: string
  vendorId: string
  vendorName: string
  dataType: string
  callCount: number
  unitPrice: number
  totalAmount: number
  billingDate: string
  status: 'pending' | 'paid' | 'overdue'
}

export interface BillingSummary {
  totalAmount: number
  paidAmount: number
  pendingAmount: number
  overdueAmount: number
}

// API接口相关类型
export interface ApiInterface {
  id: number
  interfaceCode: string
  interfaceName: string
  dataTypeId: number
  dataTypeName?: string
  vendorId?: number
  vendorName?: string
  path: string
  description?: string
  requestSchema?: string
  responseSchema?: string
  sort: number
  status: 'active' | 'inactive'
  hasConfig?: boolean
  createdAt: string
  updatedAt: string
}

// 数据查询请求
export interface DataQueryRequest {
  vendorCode: string
  dataTypeCode: string
  interfaceCode?: string
  params: Record<string, any>
}

// 数据查询响应
export interface DataQueryResponse {
  success: boolean
  data?: any
  errorCode?: string
  errorMsg?: string
  latency?: number
  cached?: boolean
}

// 调用记录相关类型
export interface CallRecord {
  id: number
  requestId: string
  tenantId: number
  callerId: number
  apiKeyId?: number
  vendorId: number
  vendorCode: string
  dataType: string
  requestParams: string
  responseData: string
  success: boolean
  errorCode?: string
  errorMsg?: string
  latency: number
  cost: number
  cached?: boolean
  callTime: string
}

// 分页响应
export interface PageResponse<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

// 通用API响应
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

// 分页参数
export interface PageParams {
  page?: number
  pageSize?: number
  keyword?: string
  status?: string
}

// 告警规则查询参数
export interface AlertRuleQueryParams {
  page?: number
  pageSize?: number
  keyword?: string
  status?: string
}

// 告警记录查询参数
export interface AlertRecordQueryParams {
  page?: number
  pageSize?: number
  status?: string
  level?: string
}

// 分页响应
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

// 列表响应 (简化版分页)
export interface ListResponse<T> {
  data: T[]
  total: number
  page?: number
  pageSize?: number
}

// 数据类型 - 字段名与后端 DataType 实体对齐
export interface DataType {
  id: number
  dataTypeCode: string    // 后端: dataTypeCode
  dataTypeName: string    // 后端: dataTypeName
  dataCategory?: string   // 后端: dataCategory
  description?: string
  pricingModel?: string
  unitPrice?: number
  status: string
  createdBy?: number
  createdAt: string
  updatedAt: string
}

// 配置中心
export interface Config {
  id: number
  vendorId: number
  vendorName: string
  configKey: string
  configValue: string
  configType: string
  description?: string
  isEncrypted: boolean
  isActive: boolean
  status?: string
  createdAt: string
  updatedAt: string
}

// 角色
export interface Role {
  id: number
  roleCode: string
  roleName: string
  description?: string
  status: string
  createdAt: string
  updatedAt: string
}

// 权限
export interface Permission {
  id: number
  permissionCode: string
  permissionName: string
  resource: string
  action: string
  description?: string
}

// 日志
export interface LogRecord {
  id: number
  userId: number
  username: string
  operation: string
  module: string
  method: string
  params?: string
  result?: string
  ip?: string
  userAgent?: string
  duration?: number
  status: string
  errorMsg?: string
  createdAt: string
}

// 灰度发布
export interface GraylogConfig {
  id: number
  configName: string
  configKey: string
  configValue: string
  description?: string
  status: string
  createdAt: string
  updatedAt: string
}

// 数据血缘
export interface DataLineage {
  id: number
  sourceType: string
  sourceId: number
  sourceName: string
  targetType: string
  targetId: number
  targetName: string
  relationType: string
  transformRule?: string
  createdAt: string
}

// 数据质量
export interface QualityRule {
  id: number
  ruleName: string
  ruleType: string
  dataType: string
  checkExpression: string
  threshold?: string
  severity: number
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface QualityScore {
  id: number
  dataType: string
  dataId: number
  score: number
  passCount: number
  failCount: number
  issueSummary?: string
  checkedAt: string
}

// ===================== 接口配置相关类型 =====================

// HTTP 方法类型
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE'

// 认证类型
export type AuthType = 'NONE' | 'BASIC' | 'BEARER' | 'API_KEY'

// 签名类型
export type SignType = 'NONE' | 'HMAC_SHA256' | 'MD5'

// Content-Type 类型
export type ContentType = 'application/json' | 'application/x-www-form-urlencoded' | 'text/plain' | 'raw'

// API 配置
export interface ApiConfig {
  url: string
  method: HttpMethod
  timeout: number
  retryCount: number
}

// 请求头配置项
export interface HeaderConfigItem {
  key: string
  value: string
  enabled: boolean
  description?: string
}

// 请求头配置（键值对形式）
export type HeaderConfig = Record<string, string>

// 参数映射配置
export interface ParamsMapping {
  request: Record<string, string>   // 内部字段 -> 厂商字段
  response: Record<string, string>  // 厂商字段 -> 内部字段
}

// 映射项
export interface MappingItem {
  sourceField: string
  targetField: string
  transformType?: 'none' | 'jsonPath' | 'expression'
  transformExpr?: string
  defaultValue?: string
}

// 签名配置
export interface SignConfig {
  type: SignType
  secretKey?: string
  signFields?: string[]
}

// 认证配置
export interface AuthConfig {
  type: AuthType
  // Basic Auth
  username?: string
  password?: string
  // Bearer Token
  token?: string
  // API Key
  apiKeyName?: string
  apiKeyValue?: string
  apiKeyLocation?: 'header' | 'query'
}

// 降级配置
export interface FallbackConfig {
  enabled: boolean
  fallbackVendorId?: number
  fallbackVendorCode?: string
}

// 熔断配置
export interface CircuitBreakerConfig {
  threshold: number      // 熔断阈值（连续失败次数）
  timeout: number        // 熔断时间（秒）
}

// 厂商接口配置 - 关联厂商、数据类型和接口
export interface VendorInterfaceConfig {
  id: number
  vendorId: number
  vendorName?: string
  dataTypeId: number
  dataTypeName?: string
  dataTypeCode?: string
  interfaceId: number
  interfaceName?: string
  apiUrl: string
  method: HttpMethod
  timeout: number
  retryCount: number
  circuitThreshold: number
  circuitTimeout: number
  signType?: string
  encryptType?: string
  headerConfig?: string
  requestTemplate?: string
  responseMapping?: string
  fallbackVendorId?: number
  fallbackVendorName?: string
  // 扩展字段
  authType?: AuthType
  authConfig?: string
  status: 'active' | 'inactive'
  createdAt: string
  updatedAt: string
}

// 字段加密
export interface EncryptedField {
  id: number
  tableName: string
  fieldName: string
  fieldType: string
  algorithm: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}