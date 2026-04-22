# 数据管理平台 API 测试用例文档

## 1. 测试概述

| 项目 | 数量 |
|------|------|
| 测试模块 | 12 个 |
| 接口总数 | 79+ 个 |
| 测试用例数 | 150+ 个 |

## 2. 测试模块列表

### 2.1 User 模块 (用户管理)

| 接口 | 方法 | 测试用例数 | 测试场景 |
|------|------|-----------|---------|
| /user/list | GET | 2 | 正常查询、未授权 |
| /user/{id} | GET | 2 | 正常查询、不存在 |
| /user | POST | 4 | 正常创建、重复用户名、必填参数缺失、密码强度不足 |
| /user/{id} | PUT | 2 | 正常更新、不存在 |
| /user/{id} | DELETE | 2 | 正常删除、不存在 |
| /user/{id}/status | PATCH | 2 | 正常修改、无效状态值 |
| /user/{id}/reset-password | POST | 3 | 正常重置、弱密码、不存在 |
| /user/{id}/roles | GET | 2 | 正常获取、不存在 |
| /user/{id}/roles | POST | 2 | 正常分配、不存在 |

**小计：21 个测试用例**

---

### 2.2 Tenant 模块 (租户管理)

| 接口 | 方法 | 测试用例数 | 测试场景 |
|------|------|-----------|---------|
| /tenant/list | GET | 2 | 正常查询、未授权 |
| /tenant/{id} | GET | 2 | 正常查询、不存在 |
| /tenant | POST | 3 | 正常创建、必填参数缺失、重复代码 |
| /tenant/{id} | PUT | 2 | 正常更新、不存在 |
| /tenant/{id} | DELETE | 2 | 正常删除、不存在 |
| /tenant/{id}/status | PATCH | 2 | 正常修改、无效状态值 |

**小计：13 个测试用例**

---

### 2.3 Role 模块 (角色管理)

| 接口 | 方法 | 测试用例数 | 测试场景 |
|------|------|-----------|---------|
| /role/list | GET | 2 | 正常查询、未授权 |
| /role/{id} | GET | 2 | 正常查询、不存在 |
| /role | POST | 3 | 正常创建、必填参数缺失、重复代码 |
| /role/{id} | PUT | 2 | 正常更新、不存在 |
| /role/{id} | DELETE | 2 | 正常删除、不存在 |
| /role/{id}/status | PATCH | 2 | 正常修改、无效状态值 |
| /role/{id}/permissions | GET | 2 | 正常获取、不存在 |
| /role/{id}/permissions | POST | 2 | 正常分配、不存在 |

**小计：17 个测试用例**

---

### 2.4 Vendor 模块 (供应商管理)

| 接口 | 方法 | 测试用例数 | 测试场景 |
|------|------|-----------|---------|
| /vendor/list | GET | 2 | 正常查询、未授权 |
| /vendor/{id} | GET | 2 | 正常查询、不存在 |
| /vendor | POST | 3 | 正常创建、必填参数缺失、重复代码 |
| /vendor/{id} | PUT | 2 | 正常更新、不存在 |
| /vendor/{id} | DELETE | 2 | 正常删除、不存在 |
| /vendor/{id}/status | PATCH | 2 | 正常修改、无效状态值 |
| /vendor/{id}/test | POST | 2 | 正常测试、不存在 |

**小计：15 个测试用例**

---

### 2.5 Billing 模块 (计费管理)

| 接口 | 方法 | 测试用例数 | 测试场景 |
|------|------|-----------|---------|
| /billing/list | GET | 2 | 正常查询、未授权 |
| /billing/{id} | GET | 2 | 正常查询、不存在 |
| /billing/stats | GET | 2 | 正常统计、未授权 |
| /billing/export | GET | 2 | 正常导出、未授权 |
| /billing/rule/list | GET | 2 | 正常查询、未授权 |
| /billing/rule | POST | 3 | 正常创建、必填参数缺失、重复代码 |
| /billing/rule/{id} | PUT | 2 | 正常更新、不存在 |
| /billing/rule/{id} | DELETE | 2 | 正常删除、不存在 |

**小计：17 个测试用例**

---

### 2.6 Call 模块 (通话记录)

| 接口 | 方法 | 测试用例数 | 测试场景 |
|------|------|-----------|---------|
| /call-record/list | GET | 2 | 正常查询、未授权 |
| /call-record/{id} | GET | 2 | 正常查询、不存在 |
| /call-record/stats | GET | 2 | 正常统计、未授权 |
| /call-record/query | POST | 2 | 正常查询、未授权 |
| /call-record/export | GET | 2 | 正常导出、未授权 |

**小计：10 个测试用例**

---

### 2.7 Caller 模块 (调用方管理)

| 接口 | 方法 | 测试用例数 | 测试场景 |
|------|------|-----------|---------|
| /caller/list | GET | 2 | 正常查询、未授权 |
| /caller/{id} | GET | 2 | 正常查询、不存在 |
| /caller | POST | 3 | 正常创建、必填参数缺失、重复代码 |
| /caller/{id} | PUT | 2 | 正常更新、不存在 |
| /caller/{id} | DELETE | 2 | 正常删除、不存在 |
| /caller/{id}/api-key/list | GET | 2 | 正常查询、不存在 |
| /caller/{id}/api-key | POST | 2 | 正常创建、不存在 |
| /caller/api-key/{id}/status | PATCH | 2 | 正常修改、无效状态 |
| /caller/api-key/{id} | DELETE | 2 | 正常删除、不存在 |

**小计：19 个测试用例**

---

### 2.8 Config 模块 (配置管理)

| 接口 | 方法 | 测试用例数 | 测试场景 |
|------|------|-----------|---------|
| /config/list | GET | 2 | 正常查询、未授权 |
| /config/{id} | GET | 2 | 正常查询、不存在 |
| /config | POST | 3 | 正常创建、必填参数缺失、重复代码 |
| /config/{id} | PUT | 2 | 正常更新、不存在 |
| /config/{id} | DELETE | 2 | 正常删除、不存在 |
| /config/vendor/{vendorId} | GET | 2 | 正常查询、不存在 |
| /config/{id}/status | PATCH | 2 | 正常修改、无效状态值 |

**小计：15 个测试用例**

---

### 2.9 DataType 模块 (数据类型)

| 接口 | 方法 | 测试用例数 | 测试场景 |
|------|------|-----------|---------|
| /datatype/list | GET | 2 | 正常查询、未授权 |
| /datatype/{id} | GET | 2 | 正常查询、不存在 |
| /datatype | POST | 3 | 正常创建、必填参数缺失、重复代码 |
| /datatype/{id} | PUT | 2 | 正常更新、不存在 |
| /datatype/{id} | DELETE | 2 | 正常删除、不存在 |
| /datatype/{id}/status | PATCH | 2 | 正常修改、无效状态值 |

**小计：13 个测试用例**

---

### 2.10 Graylog 模块 (日志配置)

| 接口 | 方法 | 测试用例数 | 测试场景 |
|------|------|-----------|---------|
| /graylog/list | GET | 2 | 正常查询、未授权 |
| /graylog/{id} | GET | 2 | 正常查询、不存在 |
| /graylog | POST | 3 | 正常创建、必填参数缺失、重复代码 |
| /graylog/{id} | PUT | 2 | 正常更新、不存在 |
| /graylog/{id} | DELETE | 2 | 正常删除、不存在 |

**小计：11 个测试用例**

---

### 2.11 Log 模块 (日志查询)

| 接口 | 方法 | 测试用例数 | 测试场景 |
|------|------|-----------|---------|
| /log/list | GET | 3 | 正常查询、筛选条件、未授权 |
| /log/{id} | GET | 2 | 正常查询、不存在 |
| /log/export | GET | 3 | 正常导出、缺少参数、未授权 |
| /log/stats | GET | 3 | 正常统计、无时间范围、未授权 |

**小计：11 个测试用例**

---

### 2.12 Monitor 模块 (监控告警)

| 接口 | 方法 | 测试用例数 | 测试场景 |
|------|------|-----------|---------|
| /alert/rule/list | GET | 3 | 正常查询、筛选条件、未授权 |
| /alert/rule/{id} | GET | 2 | 正常查询、不存在 |
| /alert/rule | POST | 3 | 正常创建、必填参数缺失、重复代码 |
| /alert/rule/{id} | PUT | 2 | 正常更新、不存在 |
| /alert/rule/{id} | DELETE | 2 | 正常删除、不存在 |
| /alert/rule/{id}/status | PATCH | 3 | 正常修改、无效状态值、未授权 |
| /alert/record/list | GET | 3 | 正常查询、筛选条件、未授权 |
| /alert/record/{id}/resolve | PATCH | 3 | 正常解决、未授权、必填参数缺失 |

**小计：21 个测试用例**

---

## 3. 测试数据说明

### 3.1 认证数据

| 字段 | 值 |
|------|-----|
| 用户名 | admin |
| 密码 | admin123 |
| Token 方式 | Bearer Token |

### 3.2 测试数据生成策略

- **唯一标识**: 使用 `System.currentTimeMillis()` 生成唯一用户名/代码
- **测试数据**: 每个测试用例使用独立的测试数据，避免相互干扰
- **数据清理**: 测试完成后根据需要清理创建的测试数据

---

## 4. 预期结果说明

### 4.1 HTTP 状态码

| 场景 | 预期状态码 |
|------|-----------|
| 正常请求 | 200 |
| 创建成功 | 200 / 201 |
| 删除成功 | 200 / 204 |
| 参数错误 | 400 |
| 未授权 | 401 |
| 资源不存在 | 404 |
| 服务异常 | 500 |

### 4.2 响应结构

```json
{
  "code": 0,
  "message": "success",
  "data": {...},
  "timestamp": 1234567890,
  "requestId": "uuid"
}
```

---

## 5. 测试执行方式

### 5.1 前置条件

1. 启动 Docker Compose (Redis, Kafka, Nacos)
2. 启动 PostgreSQL 数据库
3. 启动所有微服务
4. 启动 Gateway 网关 (端口 8080)

### 5.2 执行命令

```bash
# 编译测试模块
mvn clean compile -pl data-platform-test -am

# 运行所有测试
mvn test -pl data-platform-test

# 运行特定模块测试
mvn test -pl data-platform-test -Dtest=UserApiTest

# 生成测试报告
mvn surefire-report:report -pl data-platform-test
```

---

## 6. 测试报告

测试报告生成位置：
- `data-platform-test/target/surefire-reports/index.html`
- `data-platform-test/target/surefire-reports/*.txt`