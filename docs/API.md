# 数据管理平台 API 文档

## 基础信息

- **Base URL**: `http://localhost:8888/api/v1`
- **认证方式**: Bearer Token
- **Content-Type**: `application/json`

---

## 认证

### 登录

```http
POST /auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

**响应**:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userId": 1,
    "username": "admin"
  }
}
```

---

## 数据类型管理 (/data-type)

> 数据类型功能已合并到厂商服务 (vendor-service)

### 获取数据类型列表

```http
GET /data-type/list?page=1&pageSize=10
Authorization: Bearer {token}
```

### 获取数据类型详情

```http
GET /data-type/{id}
Authorization: Bearer {token}
```

### 创建数据类型

```http
POST /data-type
Authorization: Bearer {token}
Content-Type: application/json

{
  "datatypeCode": "PERSONAL_INFO",
  "datatypeName": "个人信息",
  "description": "个人基本信息查询"
}
```

### 更新数据类型

```http
PUT /data-type/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "datatypeName": "个人信息查询"
}
```

### 删除数据类型

```http
DELETE /data-type/{id}
Authorization: Bearer {token}
```

### 更新数据类型状态

```http
PATCH /data-type/{id}/status
Authorization: Bearer {token}
Content-Type: application/json

{
  "status": "inactive"
}
```

---

## 配置管理 (/config)

> 配置中心功能已合并到厂商服务 (vendor-service)

### 获取配置列表

```http
GET /config/list?page=1&pageSize=10
Authorization: Bearer {token}
```

### 创建配置

```http
POST /config
Authorization: Bearer {token}
Content-Type: application/json

{
  "vendorId": 1,
  "configKey": "api_endpoint",
  "configValue": "https://api.example.com",
  "configType": "string",
  "description": "API端点配置"
}
```

### 按厂商查询配置

```http
GET /config/vendor/{vendorId}
Authorization: Bearer {token}
```

---

## 厂商管理 (/vendor)

### 获取厂商列表

```http
GET /vendor/list?page=1&pageSize=10
Authorization: Bearer {token}
```

**响应**:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [...],
    "total": 100,
    "page": 1,
    "pageSize": 10
  }
}
```

### 创建厂商

```http
POST /vendor
Authorization: Bearer {token}
Content-Type: application/json

{
  "vendorCode": "VENDOR_001",
  "vendorName": "测试厂商",
  "vendorType": "HTTP",
  "contactPerson": "张三",
  "contactPhone": "13800138000"
}
```

### 更新厂商状态

```http
PATCH /vendor/{id}/status
Authorization: Bearer {token}
Content-Type: application/json

{
  "status": "active"
}
```

**状态值**:
- `active`: 启用
- `inactive`: 禁用

---

## 厂商接口配置 (/vendor/config)

> 为接口配置厂商实现，支持多厂商路由、熔断降级等高级配置

### 按接口查询配置

```http
GET /vendor/config/interface/{interfaceId}
Authorization: Bearer {token}
```

**响应**:
```json
{
  "code": 0,
  "data": [
    {
      "id": 1,
      "vendorId": 1,
      "interfaceId": 1,
      "apiUrl": "https://api.vendor.com/v1/query",
      "method": "POST",
      "timeout": 30000,
      "retryCount": 3,
      "circuitThreshold": 5,
      "circuitTimeout": 60,
      "status": "active"
    }
  ]
}
```

### 创建配置

```http
POST /vendor/config
Authorization: Bearer {token}
Content-Type: application/json

{
  "vendorId": 1,
  "dataTypeId": 1,
  "interfaceId": 1,
  "apiUrl": "https://api.vendor.com/v1/query",
  "method": "POST",
  "timeout": 30000,
  "retryCount": 3,
  "circuitThreshold": 5,
  "circuitTimeout": 60,
  "signType": "HMAC_SHA256",
  "encryptType": "AES",
  "status": "active"
}
```

**参数说明**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| vendorId | long | 是 | 厂商ID |
| interfaceId | long | 是 | 接口ID |
| apiUrl | string | 是 | API地址 |
| method | string | 否 | 请求方法，默认POST |
| timeout | int | 否 | 超时时间(ms)，默认30000 |
| retryCount | int | 否 | 重试次数，默认3 |
| circuitThreshold | int | 否 | 熔断阈值，默认5 |
| circuitTimeout | int | 否 | 熔断时间(s)，默认60 |
| signType | string | 否 | 签名类型: MD5, SHA256, HMAC_SHA256 |
| encryptType | string | 否 | 加密类型: AES, RSA |
| fallbackVendorId | long | 否 | 降级厂商ID |

### 更新配置

```http
PUT /vendor/config/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "apiUrl": "https://api.vendor.com/v2/query",
  "timeout": 60000
}
```

### 更新配置状态

```http
PATCH /vendor/config/{id}/status
Authorization: Bearer {token}
Content-Type: application/json

{
  "status": "inactive"
}
```

### 测试连接

```http
POST /vendor/config/{id}/test
Authorization: Bearer {token}
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "success": true,
    "latency": 245
  }
}
```

### 删除配置

```http
DELETE /vendor/config/{id}
Authorization: Bearer {token}
```

---

## 接口管理 (/interface)

### 获取接口列表

```http
GET /interface/list?page=1&pageSize=10&dataTypeId=1
Authorization: Bearer {token}
```

**参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码，默认1 |
| pageSize | int | 否 | 每页数量，默认10 |
| dataTypeId | long | 否 | 数据类型ID |
| status | string | 否 | 状态筛选 |

### 创建接口

```http
POST /interface
Authorization: Bearer {token}
Content-Type: application/json

{
  "interfaceCode": "PERSONAL_QUERY",
  "interfaceName": "个人信息查询",
  "dataTypeId": 1,
  "description": "查询个人基本信息",
  "requestSchema": "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}",
  "responseSchema": "{\"type\":\"object\",\"properties\":{\"code\":{\"type\":\"integer\"}}}"
}
```

### 获取接口Schema

```http
GET /interface/{id}/schema
Authorization: Bearer {token}
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "id": 1,
    "interfaceCode": "PERSONAL_QUERY",
    "requestSchema": "{...}",
    "responseSchema": "{...}"
  }
}
```

### 更新接口Schema

```http
PUT /interface/{id}/schema
Authorization: Bearer {token}
Content-Type: application/json

{
  "requestSchema": "{\"type\":\"object\",\"properties\":{\"idCard\":{\"type\":\"string\"}}}",
  "responseSchema": "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}"
}
```

### 验证Schema格式

```http
POST /interface/schema/validate
Authorization: Bearer {token}
Content-Type: application/json

{
  "schema": "{\"type\":\"object\"}"
}
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "valid": true
  }
}
```

### 获取接口调用统计

```http
GET /interface/{id}/stats?startTime=2026-04-01&endTime=2026-04-26
Authorization: Bearer {token}
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "interfaceId": 1,
    "interfaceCode": "PERSONAL_QUERY",
    "totalCalls": 15000,
    "successCalls": 14850,
    "avgLatency": 245.5,
    "slowCalls": 120
  }
}
```

---

## 数据调用 (/call)

### 数据查询

```http
POST /call/query
Authorization: Bearer {token}
Content-Type: application/json

{
  "dataType": "PERSONAL_INFO",
  "params": {
    "name": "张三",
    "idCard": "110101199001011234"
  }
}
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "success": true,
    "data": {
      "name": "张三",
      "age": 35,
      "address": "北京市..."
    },
    "latency": 245
  }
}
```

### 调用记录查询

```http
GET /call/record?startTime=2026-04-01&endTime=2026-04-26&page=1&pageSize=20
Authorization: Bearer {token}
```

### 调用统计

```http
GET /call/statistics?days=7
Authorization: Bearer {token}
```

---

## 计费管理 (/billing)

### 日账单查询

```http
GET /billing/daily?vendorId=1&startDate=2026-04-01&endDate=2026-04-26
Authorization: Bearer {token}
```

### 账单汇总

```http
GET /billing/summary?vendorId=1&month=2026-04
Authorization: Bearer {token}
```

---

## 监控告警 (/monitor)

### 告警规则列表

```http
GET /monitor/alert-rule
Authorization: Bearer {token}
```

### 创建告警规则

```http
POST /monitor/alert-rule
Authorization: Bearer {token}
Content-Type: application/json

{
  "ruleName": "API响应超时告警",
  "metric": "latency",
  "threshold": 3000,
  "operator": ">",
  "severity": "WARNING",
  "notifyChannels": ["email", "sms"]
}
```

### 熔断记录查询

```http
GET /monitor/circuit-breaker?vendorId=1
Authorization: Bearer {token}
```

---

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| 0 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 资源冲突 |
| 500 | 服务器内部错误 |

---

## 通用响应格式

```json
{
  "code": 0,
  "message": "success",
  "data": { ... }
}
```

分页响应:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [...],
    "total": 100,
    "page": 1,
    "pageSize": 10
  }
}
```
