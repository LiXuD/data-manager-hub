# 数据管理平台 API 文档

**版本**: 2026-07-10
**Base URL**: `http://localhost:8888/api/v1`
**认证方式**: Bearer Token
**Content-Type**: `application/json`

---

## 目录

- [认证](#认证)
- [厂商管理 (/vendor)](#厂商管理-vendor)
- [数据类型 (/datatype)](#数据类型-datatype)
- [厂商配置 (/vendor-config)](#厂商配置-vendor-config)
- [接口管理 (/interface)](#接口管理-interface)
- [灰度规则 (/graylog)](#灰度规则-graylog)
- [调用方管理 (/caller)](#调用方管理-caller)
- [API Key (/api-key)](#api-key-api-key)
- [数据调用 (/data)](#数据调用-data)
- [调用记录 (/call-record)](#调用记录-call-record)
- [计费管理 (/billing)](#计费管理-billing)
- [用户管理 (/user)](#用户管理-user)
- [角色管理 (/role)](#角色管理-role)
- [认证 (/auth)](#认证-auth)
- [租户管理 (/tenant)](#租户管理-tenant)
- [监控告警 (/alert)](#监控告警-alert)
- [操作日志 (/log)](#操作日志-log)
- [数据质量 (/quality)](#数据质量-quality)
- [数据血缘 (/trace)](#数据血缘-trace)
- [数据安全 (/security)](#数据安全-security)
- [错误码说明](#错误码说明)

---

## 认证

本文件仅描述外部 HTTP API。服务间调用使用 `/internal/v1/**` 专用契约和短期 Service JWT，这些路径不经 Gateway 暴露，也不能使用用户 Token 或 API Key 访问。

### 请求头

| 头部 | 必填 | 说明 |
|------|------|------|
| `Authorization` | 是 | Bearer Token，通过 `/auth/login` 获取 |
| `X-Trace-Id` | 否 | 链路追踪 ID。如不传，Gateway 自动生成 UUID。响应头中会回传此值，可用于关联调用记录 (`call_record.trace_id`) 和 SkyWalking trace。 |

**示例**:
```http
GET /api/v1/datatype/list
Authorization: Bearer xxx-token
X-Trace-Id: my-trace-id-12345
```

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
  "code": 200,
  "message": "success",
  "data": {
    "token": "xxx-uuid-token",
    "userId": 1,
    "username": "admin"
  }
}
```

### 验证 Token

```http
GET /auth/verify
Authorization: Bearer {token}
```

### 获取用户信息

```http
GET /auth/userinfo
Authorization: Bearer {token}
```

---

## 厂商管理 (/vendor)

### 获取厂商列表

```http
GET /vendor?page=1&pageSize=10
Authorization: Bearer {token}
```

### 获取厂商详情

```http
GET /vendor/{id}
Authorization: Bearer {token}
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

### 更新厂商

```http
PUT /vendor/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "vendorName": "测试厂商v2"
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

**状态值**: `active` (启用), `inactive` (禁用)

### 测试厂商连通性

```http
POST /vendor/{id}/test
Authorization: Bearer {token}
```

### 删除厂商

```http
DELETE /vendor/{id}
Authorization: Bearer {token}
```

---

## 数据类型 (/datatype)

### 获取数据类型列表

```http
GET /datatype?page=1&pageSize=10
Authorization: Bearer {token}
```

### 获取数据类型详情

```http
GET /datatype/{id}
Authorization: Bearer {token}
```

### 创建数据类型

```http
POST /datatype
Authorization: Bearer {token}
Content-Type: application/json

{
  "dataTypeCode": "PERSONAL_INFO",
  "dataTypeName": "个人信息",
  "description": "个人基本信息查询"
}
```

### 更新数据类型

```http
PUT /datatype/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "dataTypeName": "个人信息查询"
}
```

### 更新数据类型状态

```http
PATCH /datatype/{id}/status
Authorization: Bearer {token}
Content-Type: application/json

{
  "status": "active"
}
```

### 删除数据类型

```http
DELETE /datatype/{id}
Authorization: Bearer {token}
```

---

## 厂商配置 (/vendor-config)

### 获取厂商配置列表

```http
GET /vendor-config?page=1&pageSize=10&vendorId=1
Authorization: Bearer {token}
```

### 按接口查询配置

```http
GET /vendor-config/interface/{interfaceId}
Authorization: Bearer {token}
```

### 创建厂商配置

```http
POST /vendor-config
Authorization: Bearer {token}
Content-Type: application/json

{
  "vendorId": 1,
  "interfaceId": 1,
  "apiUrl": "https://api.vendor.com/v1/query",
  "method": "POST",
  "timeout": 30000,
  "retryCount": 3,
  "circuitThreshold": 5,
  "circuitTimeout": 60,
  "signType": "HMAC_SHA256",
  "encryptType": "AES",
  "authType": "NONE",
  "status": "active"
}
```

### 更新厂商配置

```http
PUT /vendor-config/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "apiUrl": "https://api.vendor.com/v2/query",
  "timeout": 60000
}
```

### 更新配置状态

```http
PATCH /vendor-config/{id}/status
Authorization: Bearer {token}
Content-Type: application/json

{
  "status": "inactive"
}
```

### 测试连接

```http
POST /vendor-config/{id}/test
Authorization: Bearer {token}
```

### 删除配置

```http
DELETE /vendor-config/{id}
Authorization: Bearer {token}
```

---

## 接口管理 (/interface)

### 获取接口列表

```http
GET /interface?page=1&pageSize=10&dataTypeId=1
Authorization: Bearer {token}
```

### 获取接口详情

```http
GET /interface/{id}
Authorization: Bearer {token}
```

### 获取接口参数定义

```http
GET /interface/{id}/params
Authorization: Bearer {token}
```

返回参数按 `sort` 升序排列。每项包含 `paramName`、`description`、`paramType`（`string`、`number`、`boolean`、`object` 或 `array`）、`required`、`defaultValue`、`validationRule` 和 `sort`。

### 新增接口参数定义

```http
POST /interface/{id}/params
Authorization: Bearer {token}
Content-Type: application/json

{
  "paramName": "companyName",
  "description": "企业名称",
  "paramType": "string",
  "required": true,
  "defaultValue": null,
  "validationRule": null,
  "sort": 1
}
```

### 批量保存接口参数定义

```http
PUT /interface/{id}/params/batch
Authorization: Bearer {token}
Content-Type: application/json

[
  {
    "paramName": "companyName",
    "paramType": "string",
    "required": true,
    "sort": 1
  }
]
```

批量保存会以本次请求内容替换该接口现有的参数定义；同一接口内参数名不能重复。

### 更新或删除接口参数定义

```http
PUT /interface/params/{paramId}
DELETE /interface/params/{paramId}
Authorization: Bearer {token}
```

### 创建接口

```http
POST /interface
Authorization: Bearer {token}
Content-Type: application/json

{
  "interfaceCode": "PERSONAL_QUERY",
  "interfaceName": "个人信息查询",
  "dataTypeId": 1,
  "description": "查询个人基本信息"
}
```

### 更新接口

```http
PUT /interface/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "interfaceName": "个人信息查询v2"
}
```

### 获取接口Schema

```http
GET /interface/{id}/schema
Authorization: Bearer {token}
```

### 更新接口Schema

```http
PUT /interface/{id}/schema
Authorization: Bearer {token}
Content-Type: application/json

{
  "requestSchema": "{\"type\":\"object\"}",
  "responseSchema": "{\"type\":\"object\"}"
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

### 获取接口调用统计

```http
GET /interface/{id}/stats?startTime=2026-04-01&endTime=2026-05-16
Authorization: Bearer {token}
```

### 删除接口

```http
DELETE /interface/{id}
Authorization: Bearer {token}
```

---

## 灰度规则 (/graylog)

### 获取灰度规则列表

```http
GET /graylog?page=1&pageSize=10&status=active
Authorization: Bearer {token}
```

### 获取灰度规则详情

```http
GET /graylog/{id}
Authorization: Bearer {token}
```

### 创建灰度规则

```http
POST /graylog
Authorization: Bearer {token}
Content-Type: application/json

{
  "ruleName": "灰度规则A",
  "vendorCode": "VENDOR_001",
  "dataTypeCode": "PERSONAL_INFO",
  "weight": 10,
  "conditionType": "header",
  "conditionValue": "X-Gray-Test: true",
  "description": "测试灰度",
  "status": "active"
}
```

### 更新灰度规则

```http
PUT /graylog/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "weight": 50
}
```

### 更新灰度规则状态

```http
PATCH /graylog/{id}/status
Authorization: Bearer {token}
Content-Type: application/json

{
  "status": "inactive"
}
```

### 删除灰度规则

```http
DELETE /graylog/{id}
Authorization: Bearer {token}
```

---

## 调用方管理 (/caller)

### 获取调用方列表

```http
GET /caller?page=1&pageSize=10
Authorization: Bearer {token}
```

### 获取调用方详情

```http
GET /caller/{id}
Authorization: Bearer {token}
```

### 创建调用方

```http
POST /caller
Authorization: Bearer {token}
Content-Type: application/json

{
  "callerName": "测试调用方",
  "contactPerson": "李四",
  "contactPhone": "13900139000",
  "rateLimit": 1000
}
```

### 更新调用方

```http
PUT /caller/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "callerName": "测试调用方v2"
}
```

### 更新调用方状态

```http
PATCH /caller/{id}/status
Authorization: Bearer {token}
Content-Type: application/json

{
  "status": "active"
}
```

### 删除调用方

```http
DELETE /caller/{id}
Authorization: Bearer {token}
```

---

## API Key (/api-key)

### 获取API Key列表

```http
GET /api-key?callerId=1
Authorization: Bearer {token}
```

### 创建API Key

```http
POST /api-key
Authorization: Bearer {token}
Content-Type: application/json

{
  "callerId": 1,
  "keyName": "生产环境Key",
  "expireTime": "2027-12-31",
  "rateLimit": 1000
}
```

### 更新API Key

```http
PUT /api-key/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "keyName": "生产环境Key v2",
  "status": "active"
}
```

### 刷新API Key

```http
POST /api-key/{id}/refresh
Authorization: Bearer {token}
```

### 删除API Key

```http
DELETE /api-key/{id}
Authorization: Bearer {token}
```

---

## 外部系统统一调用 (/openapi/v1)

外部系统通过 Gateway 统一入口调用，认证方式为 `X-Api-Key` 或 `Authorization: Bearer {apiKey}`。

### 单条查询

```http
POST /openapi/v1/query
X-Api-Key: {apiKey}
Content-Type: application/json

{
  "requestId": "risk-20260517-0001",
  "apiCode": "PERSONAL_QUERY",
  "apiVersion": "v1",
  "productCode": "loan-risk",
  "sceneCode": "pre-loan-review",
  "useCache": true,
  "cacheDays": 3,
  "params": {
    "name": "张三",
    "idCard": "110101199001011234"
  }
}
```

**响应**:
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "requestId": "risk-20260517-0001",
    "platformRequestId": "req_abc123",
    "apiCode": "PERSONAL_QUERY",
    "apiVersion": "v1",
    "productCode": "loan-risk",
    "sceneCode": "pre-loan-review",
    "success": true,
    "cached": true,
    "cacheSourceRecordId": 123,
    "requestTime": "2026-05-17T12:00:00",
    "responseTime": "2026-05-17T12:00:00.120",
    "durationMs": 120,
    "cost": 0,
    "data": {
      "name": "张三",
      "age": 35
    }
  }
}
```

说明：
- `productCode` 必须属于调用方已配置且启用的产品。
- API Key 必须被授权访问该产品。
- `sceneCode` 必须是启用的公共调用场景。
- `useCache=true` 时 `cacheDays` 必须大于 0；平台会查询指定天数内同 `apiCode + params` 的最新成功记录，命中后不调用厂商且本次费用为 0。

### 批量查询

```http
POST /openapi/v1/batch-query
X-Api-Key: {apiKey}
Content-Type: application/json

{
  "requestId": "risk-batch-20260517-0001",
  "apiCode": "PERSONAL_QUERY",
  "apiVersion": "v1",
  "productCode": "loan-risk",
  "sceneCode": "pre-loan-review",
  "useCache": true,
  "cacheDays": 3,
  "items": [
    {
      "itemId": "1",
      "params": {"name": "张三"}
    },
    {
      "itemId": "2",
      "params": {"name": "李四"}
    }
  ]
}
```

### 调用方产品配置

```http
POST /caller/{callerId}/products
Authorization: Bearer {token}
Content-Type: application/json

{
  "productCode": "loan-risk",
  "productName": "信贷风控",
  "cacheScope": "GLOBAL"
}
```

`cacheScope` 支持 `GLOBAL` 和 `CALLER`。`GLOBAL` 表示同接口同参数可复用平台历史结果；`CALLER` 表示只复用本调用方历史结果。

### API Key 产品授权

```http
POST /caller/apikey/{id}/products
Authorization: Bearer {token}
Content-Type: application/json

[1, 2, 3]
```

### 公共调用场景

```http
POST /call-scene
Authorization: Bearer {token}
Content-Type: application/json

{
  "sceneCode": "pre-loan-review",
  "sceneName": "贷前审批"
}
```

### 多维调用统计

```http
GET /call-record/dimension-stats?callerId=1&productCode=loan-risk&sceneCode=pre-loan-review
Authorization: Bearer {token}
```

---

## 数据调用 (/data)

### 数据查询

```http
POST /data/query
Authorization: Bearer {token}
Content-Type: application/json

{
  "interfaceCode": "PERSONAL_QUERY",
  "params": {
    "name": "张三",
    "idCard": "110101199001011234"
  }
}
```

**响应**:
```json
{
  "code": 200,
  "data": {
    "success": true,
    "data": {
      "name": "张三",
      "age": 35
    },
    "latency": 245,
    "callRecordId": 12345
  }
}
```

### 批量查询

```http
POST /data/batch-query
Authorization: Bearer {token}
Content-Type: application/json

{
  "queries": [
    {
      "interfaceCode": "PERSONAL_QUERY",
      "params": {"name": "张三"}
    },
    {
      "interfaceCode": "PERSONAL_QUERY",
      "params": {"name": "李四"}
    }
  ]
}
```

### 清除缓存

```http
POST /data/cache/clear
Authorization: Bearer {token}
Content-Type: application/json

{
  "interfaceCode": "PERSONAL_QUERY"
}
```

### 缓存统计

```http
GET /data/cache/stats
Authorization: Bearer {token}
```

---

## 调用记录 (/call-record)

### 获取调用记录列表

```http
GET /call-record?page=1&pageSize=10&startTime=2026-05-01&endTime=2026-05-16
Authorization: Bearer {token}
```

### 获取调用记录详情

```http
GET /call-record/{id}
Authorization: Bearer {token}
```

### 获取调用统计

```http
GET /call-record/stats?days=7
Authorization: Bearer {token}
```

### 多维统计

```http
GET /call-record/dimension-stats?vendorCode=tianyancha&dataType=company_info&startTime=2026-06-01&endTime=2026-06-22
Authorization: Bearer {token}
```

返回按 caller、product、scene、vendor、dataType 等维度分组的调用统计（总数、成功/失败数、费用、延迟）。

### 接口质量报表

```http
GET /call-record/quality-report?startTime=2026-06-01&endTime=2026-06-22
Authorization: Bearer {token}
```

按 vendor + dataType + apiCode 分组返回接口质量指标：成功率、失败率、平均/P50/P95/P99 延迟、总费用。默认查询最近 90 天。

---

## 计费管理 (/billing)

### 模板化计费方案（推荐）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/billing/template/list` | 查询启用的计费模板 |
| GET | `/billing/plan/list` | 查询全部方案及版本 |
| GET | `/billing/plan/{id}` | 查询方案详情 |
| POST | `/billing/plan` | 创建草稿 |
| PUT | `/billing/plan/{id}` | 更新草稿/待复核版本 |
| POST | `/billing/plan/{id}/next-version` | 基于历史版本创建新草稿 |
| POST | `/billing/plan/{id}/validate` | 按当前接口契约校验 |
| POST | `/billing/plan/{id}/simulate` | 使用计量事实和账期已有用量模拟 |
| POST | `/billing/plan/{id}/publish` | 发布版本 |
| DELETE | `/billing/plan/{id}` | 删除未发布草稿 |
| POST | `/billing/plan/review-contracts` | 检查响应契约漂移 |
| POST | `/billing/plan/accrue` | 补提套餐/包周期固定费用 |
| GET | `/billing/event/list` | 分页查询事件账本 |
| GET | `/billing/event/stats` | 查询净金额、净数量和待复核数 |
| POST | `/billing/event/{id}/reverse` | 追加全额冲正事件 |

Access 域内部调用：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/internal/billing/metering-policy` | 按厂商、接口、调用时间固定生效方案与最小字段选择器 |
| POST | `/internal/billing/charge` | 提交最小计量事实并幂等入账 |

详细配置、定价和生命周期语义见 `docs/2026-07-21-billing-plan-implementation.md`。

### 计费规则 CRUD

#### 获取计费规则列表

```http
GET /billing/rule?page=1&pageSize=10
Authorization: Bearer {token}
```

#### 创建计费规则

```http
POST /billing/rule
Authorization: Bearer {token}
Content-Type: application/json

{
  "ruleName": "标准计费规则",
  "dataTypeCode": "PERSONAL_INFO",
  "billingType": "STANDARD",
  "unitPrice": 0.5,
  "tierConfig": null,
  "status": "active"
}
```

**billingType 值**: `STANDARD` (标准计费), `TIERED` (阶梯计费), `DYNAMIC` (动态计费)

#### 阶梯计费规则示例

```json
{
  "ruleName": "阶梯计费规则",
  "dataTypeCode": "PERSONAL_INFO",
  "billingType": "TIERED",
  "tierConfig": {
    "tiers": [
      {"minQuantity": 0, "maxQuantity": 1000, "unitPrice": 0.5},
      {"minQuantity": 1001, "maxQuantity": 5000, "unitPrice": 0.4},
      {"minQuantity": 5001, "maxQuantity": null, "unitPrice": 0.3}
    ]
  }
}
```

#### 更新计费规则

```http
PUT /billing/rule/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "unitPrice": 0.45
}
```

#### 删除计费规则

```http
DELETE /billing/rule/{id}
Authorization: Bearer {token}
```

### 日账单

#### 查询日账单

```http
GET /billing/daily?vendorId=1&startDate=2026-05-01&endDate=2026-05-16
Authorization: Bearer {token}
```

### 账单汇总

```http
GET /billing/summary?month=2026-05
Authorization: Bearer {token}
```

### 预算管理

#### 获取预算列表

```http
GET /billing/budget?page=1&pageSize=10
Authorization: Bearer {token}
```

#### 创建/更新预算

```http
POST /billing/budget
Authorization: Bearer {token}
Content-Type: application/json

{
  "tenantId": 1,
  "monthlyLimit": 10000,
  "alertThreshold": 0.8,
  "status": "active"
}
```

---

## 用户管理 (/user)

### 获取用户列表

```http
GET /user?page=1&pageSize=10
Authorization: Bearer {token}
```

### 获取用户详情

```http
GET /user/{id}
Authorization: Bearer {token}
```

### 创建用户

```http
POST /user
Authorization: Bearer {token}
Content-Type: application/json

{
  "username": "testuser",
  "password": "password123",
  "email": "test@example.com",
  "phone": "13800138000",
  "roleIds": [1, 2]
}
```

### 更新用户

```http
PUT /user/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "email": "newemail@example.com"
}
```

### 更新用户状态

```http
PATCH /user/{id}/status
Authorization: Bearer {token}
Content-Type: application/json

{
  "status": "active"
}
```

### 删除用户

```http
DELETE /user/{id}
Authorization: Bearer {token}
```

### 分配角色

```http
POST /user/{id}/roles
Authorization: Bearer {token}
Content-Type: application/json

{
  "roleIds": [1, 2, 3]
}
```

---

## 角色管理 (/role)

### 获取角色列表

```http
GET /role?page=1&pageSize=10
Authorization: Bearer {token}
```

### 获取角色详情

```http
GET /role/{id}
Authorization: Bearer {token}
```

### 创建角色

```http
POST /role
Authorization: Bearer {token}
Content-Type: application/json

{
  "roleCode": "ADMIN",
  "roleName": "管理员",
  "description": "系统管理员",
  "permissionIds": [1, 2, 3, 4, 5]
}
```

### 更新角色

```http
PUT /role/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "roleName": "超级管理员"
}
```

### 更新角色状态

```http
PATCH /role/{id}/status
Authorization: Bearer {token}
Content-Type: application/json

{
  "status": "active"
}
```

### 删除角色

```http
DELETE /role/{id}
Authorization: Bearer {token}
```

### 分配权限

```http
POST /role/{id}/permissions
Authorization: Bearer {token}
Content-Type: application/json

{
  "permissionIds": [1, 2, 3]
}
```

---

## 租户管理 (/tenant)

### 获取租户列表

```http
GET /tenant?page=1&pageSize=10
Authorization: Bearer {token}
```

### 获取租户详情

```http
GET /tenant/{id}
Authorization: Bearer {token}
```

### 创建租户

```http
POST /tenant
Authorization: Bearer {token}
Content-Type: application/json

{
  "tenantCode": "TENANT_001",
  "tenantName": "测试租户",
  "contactPerson": "王五",
  "contactPhone": "13700137000",
  "status": "active"
}
```

### 更新租户

```http
PUT /tenant/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "tenantName": "测试租户v2"
}
```

### 更新租户状态

```http
PATCH /tenant/{id}/status
Authorization: Bearer {token}
Content-Type: application/json

{
  "status": "active"
}
```

### 删除租户

```http
DELETE /tenant/{id}
Authorization: Bearer {token}
```

---

## 监控告警 (/alert)

### 获取告警规则列表

```http
GET /alert/rule?page=1&pageSize=10
Authorization: Bearer {token}
```

### 创建告警规则

```http
POST /alert/rule
Authorization: Bearer {token}
Content-Type: application/json

{
  "ruleName": "API响应超时告警",
  "ruleType": "LATENCY",
  "metric": "latency",
  "threshold": 3000,
  "operator": ">",
  "severity": "WARNING",
  "notifyChannels": ["email", "sms"],
  "status": "active"
}
```

### 更新告警规则

```http
PUT /alert/rule/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "threshold": 5000
}
```

### 删除告警规则

```http
DELETE /alert/rule/{id}
Authorization: Bearer {token}
```

### 获取告警记录

```http
GET /alert/record?page=1&pageSize=10&status=TRIGGERED
Authorization: Bearer {token}
```

### 确认告警

```http
POST /alert/record/{id}/ack
Authorization: Bearer {token}
```

### 获取熔断状态

```http
GET /alert/circuit-breaker?vendorId=1
Authorization: Bearer {token}
```

---

## 操作日志 (/log)

### 获取日志列表

```http
GET /log?page=1&pageSize=10&module=vendor&startTime=2026-05-01&endTime=2026-05-16
Authorization: Bearer {token}
```

### 获取日志详情

```http
GET /log/{id}
Authorization: Bearer {token}
```

---

## 数据质量 (/quality)

### 获取质量规则列表

```http
GET /quality/rule?page=1&pageSize=10
Authorization: Bearer {token}
```

### 创建质量规则

```http
POST /quality/rule
Authorization: Bearer {token}
Content-Type: application/json

{
  "ruleName": "数据完整性检查",
  "dataTypeCode": "PERSONAL_INFO",
  "checkType": "COMPLETENESS",
  "threshold": 0.99,
  "status": "active"
}
```

### 更新质量规则

```http
PUT /quality/rule/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "threshold": 0.95
}
```

### 删除质量规则

```http
DELETE /quality/rule/{id}
Authorization: Bearer {token}
```

### 获取质量评分

```http
GET /quality/score?dataTypeCode=PERSONAL_INFO&startTime=2026-05-01&endTime=2026-05-16
Authorization: Bearer {token}
```

---

## 数据血缘 (/trace/lineage)

### 创建血缘关系

```http
POST /trace/lineage
Authorization: Bearer {token}
Content-Type: application/json

{
  "sourceType": "vendor",
  "sourceId": 1,
  "sourceName": "厂商A",
  "targetType": "interface",
  "targetId": 1,
  "targetName": "企业工商查询",
  "relationType": "PROVIDES",
  "transformRule": "SkyWalking traceId=trace-1"
}
```

### 获取上游血缘

```http
GET /trace/lineage/upstream?type=interface&id=1
Authorization: Bearer {token}
```

### 获取下游血缘

```http
GET /trace/lineage/downstream?type=vendor&id=1
Authorization: Bearer {token}
```

### 获取完整上游血缘

```http
GET /trace/lineage/full?type=interface&id=1
Authorization: Bearer {token}
```

---

## 数据安全 (/security)

### 加密数据

```http
POST /security/encrypt
Authorization: Bearer {token}
Content-Type: application/json

{
  "algorithm": "AES",
  "data": "敏感数据内容"
}
```

### 解密数据

```http
POST /security/decrypt
Authorization: Bearer {token}
Content-Type: application/json

{
  "algorithm": "AES",
  "encryptedData": "加密后的数据"
}
```

---

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 资源冲突 |
| 500 | 服务器内部错误 |

---

## 通用响应格式

### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

### 分页响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [...],
    "total": 100,
    "page": 1,
    "pageSize": 10
  }
}
```

### 错误响应

```json
{
  "code": 400,
  "message": "参数错误: xxx字段不能为空",
  "data": null
}
```

---

**文档版本**: 2026-05-16
**最后更新**: 与五域收敛架构同步
