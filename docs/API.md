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
  "status": "1"
}
```

**状态值**:
- `1`: 启用
- `0`: 禁用

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
