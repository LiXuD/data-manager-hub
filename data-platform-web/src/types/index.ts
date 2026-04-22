// 租户相关类型
export interface Tenant {
  id: string
  name: string
  code: string
  contact: string
  email: string
  phone: string
  status: 'enabled' | 'disabled'
  budget: number
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
  id: string
  name: string
  path: string
  method: 'GET' | 'POST' | 'PUT' | 'DELETE'
  vendorId: string
  vendorName: string
  dataTypeId: string
  dataTypeName: string
  version: string
  deprecated: boolean
  status: 'enabled' | 'disabled'
  createdAt: string
  updatedAt: string
}

// 调用记录相关类型
export interface CallRecord {
  id: string
  callerId: string
  callerName: string
  tenantId: string
  tenantName: string
  apiId: string
  apiName: string
  vendorId: string
  vendorName: string
  requestParams: Record<string, any>
  responseData: Record<string, any>
  status: 'success' | 'failed'
  errorCode?: string
  errorMessage?: string
  cost: number
  callTime: string
  responseTime: number
  traceId: string
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

// 分页响应
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

// 数据类型
export interface DataType {
  id: number
  typeCode: string
  typeName: string
  description: string
  vendorId: number
  vendorName: string
  schema?: string
  status: string
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