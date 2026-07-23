# 数据管理平台 HTTP API

> 当前契约索引，最后核对日期：2026-07-23。本文只描述经 Gateway 暴露的 HTTP API；跨域 Feign 契约位于各域 `*-api` 模块，并统一使用 `/internal/v1/**`。

## 1. 入口与认证

- 管理端基地址：`http://<gateway>:8888/api/v1`
- 外部调用基地址：`http://<gateway>:8888/openapi/v1`
- 管理端请求：`Authorization: Bearer <token>`
- 外部调用请求：`X-Api-Key: <api-key>`
- JSON 请求：`Content-Type: application/json`
- Gateway 不路由 `/internal/**`，并会清理外部请求中的可信身份头。

通用成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

分页接口可能直接返回 `PageResult`：

```json
{
  "code": 200,
  "message": "success",
  "data": [],
  "total": 0,
  "page": 1,
  "pageSize": 20
}
```

## 2. 身份与租户

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/auth/login` | 登录并获取用户 Token |
| POST | `/auth/logout` | 注销当前会话 |
| GET | `/auth/verify` | 验证当前 Token |
| GET | `/auth/userinfo` | 当前用户信息 |
| PUT | `/auth/profile` | 更新个人资料 |
| PUT | `/auth/password` | 修改密码 |
| GET/POST | `/user/list`、`/user` | 用户列表、创建用户 |
| GET/PUT/DELETE | `/user/{id}` | 用户详情、更新、删除 |
| PATCH | `/user/{id}/status` | 更新用户状态 |
| POST | `/user/{id}/reset-password` | 重置密码 |
| GET/POST | `/user/{id}/roles` | 查询或分配角色 |
| GET/POST | `/user/{id}/callers` | 查询或分配调用方 |
| GET/POST | `/role/list`、`/role` | 角色列表、创建角色 |
| GET/PUT/DELETE | `/role/{id}` | 角色详情、更新、删除 |
| PATCH | `/role/{id}/status` | 更新角色状态 |
| GET | `/role/{id}/permissions`、`/role/{id}/permissionIds` | 角色权限 |
| POST | `/role/{id}/permissions` | 分配权限 |
| GET | `/permission/list`、`/permission/all` | 权限列表 |
| GET/PUT/DELETE | `/permission/{id}` | 权限详情、更新、删除 |
| POST | `/permission` | 创建权限 |
| GET/POST | `/tenant/list`、`/tenant` | 租户列表、创建租户 |
| GET/PUT/DELETE | `/tenant/{id}` | 租户详情、更新、删除 |
| PATCH | `/tenant/{id}/status` | 更新租户状态 |
| POST | `/security/encryption/encrypt`、`/decrypt` | 管理端字段加解密 |
| POST | `/security/encryption/rotate/{tableName}` | 轮换指定表密文 |

密码只接受 BCrypt 存储值；历史明文不会再被登录逻辑接受。

## 3. 主数据

### 3.1 厂商与数据类型

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/vendor/list`、`/vendor/all` | 厂商分页列表、全部选项 |
| GET | `/vendor/{id}`、`/vendor/code/{vendorCode}` | 厂商详情 |
| POST | `/vendor` | 创建厂商 |
| PUT/DELETE | `/vendor/{id}` | 更新、删除厂商 |
| PATCH | `/vendor/{id}/status` | 更新状态 |
| POST | `/vendor/{id}/test` | 连通性测试 |
| GET | `/datatype/list`、`/datatype/all` | 数据类型列表、全部选项 |
| GET | `/datatype/{id}`、`/datatype/code/{code}` | 数据类型详情 |
| POST | `/datatype` | 创建数据类型 |
| PUT/DELETE | `/datatype/{id}` | 更新、删除数据类型 |
| PATCH | `/datatype/{id}/status` | 更新状态 |

### 3.2 厂商接口配置

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/vendor/config/list` | 按厂商、数据类型、接口和状态筛选 |
| GET | `/vendor/config/{id}` | 配置详情 |
| GET | `/vendor/config/vendor/{vendorId}` | 厂商配置列表 |
| GET | `/vendor/config/interface/{interfaceId}` | 接口配置列表 |
| POST | `/vendor/config` | 创建配置 |
| PUT/DELETE | `/vendor/config/{id}` | 更新、删除配置 |
| PATCH | `/vendor/config/{id}/status` | 更新状态 |
| POST | `/vendor/config/{id}/test` | 连接与调用测试 |
| GET/PUT | `/vendor/config/{id}/mapping` | 参数映射 |
| GET | `/vendor/config/security-capabilities` | 安全步骤能力 |
| GET/PUT | `/vendor/config/{configId}/security-steps` | 查询或替换安全流水线 |
| PUT | `/vendor/config/{configId}/security-steps/order` | 调整步骤顺序 |
| POST | `/vendor/config/{configId}/security-preview` | 脱敏预览 |
| POST | `/vendor/config/{configId}/security-test` | 安全流水线测试 |
| GET | `/vendor/config/{configId}/security-versions` | 版本历史 |
| POST | `/vendor/config/{configId}/security-versions/{versionId}/rollback` | 回滚版本 |

`signType`、`encryptType` 和简单签名回退已移除；运行时只执行已启用的安全流水线。敏感扩展配置必须以平台 `v1:<keyVersion>:<ciphertext>` 格式存储，否则读取失败关闭。

### 3.3 扩展配置

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/vendor/extended-config/list` | 扩展配置列表 |
| GET/PUT/DELETE | `/vendor/extended-config/{id}` | 详情、更新、删除 |
| POST | `/vendor/extended-config` | 创建扩展配置 |
| GET | `/vendor/extended-config/vendor/{vendorId}` | 按厂商查询 |
| PATCH | `/vendor/extended-config/{id}/status` | 更新状态 |
| GET/POST/PUT/DELETE | `/config/**` | 平台配置管理、发布、版本和缓存管理 |

### 3.4 接口契约

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/interface/list`、`/interface/options` | 接口列表、选择项 |
| GET | `/interface/{id}` | 接口详情 |
| GET | `/interface/by-data-type/{dataTypeId}` | 按数据类型查询 |
| POST | `/interface` | 创建接口 |
| PUT/DELETE | `/interface/{id}` | 更新、删除接口 |
| PATCH | `/interface/{id}/status` | 更新状态 |
| GET | `/interface/{id}/contract` | 获取完整请求/响应字段树及生成快照 |
| PUT | `/interface/{id}/contract` | 原子替换完整契约 |
| GET | `/interface/{id}/stats`、`/stats/daily` | 调用统计 |

`interface_param` 字段树是唯一契约数据源。`requestSchema` 和 `responseSchema` 由字段树生成，不能通过普通接口或独立 Schema API 写入；旧 `/schema`、`/params` 和 `import-schema` 端点已删除。约束只使用 JSON `constraintConfig`。

### 3.5 灰度与管理端调用测试

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST | `/graylog/list`、`/graylog` | 灰度规则列表、创建 |
| GET/PUT/DELETE | `/graylog/{id}` | 详情、更新、删除 |
| PATCH | `/graylog/{id}/status` | 更新状态 |
| GET | `/graylog/active/{serviceName}` | 生效规则 |
| POST | `/data/query` | 管理端按厂商配置执行一次测试调用 |

## 4. 调用方与 API Key

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/caller/list`、`/caller/{id}` | 调用方列表、详情 |
| POST | `/caller` | 创建调用方 |
| PUT/DELETE | `/caller/{id}` | 更新、删除调用方 |
| PATCH | `/caller/{id}/status` | 更新状态 |
| GET/POST | `/caller/{callerId}/products` | 查询或创建调用产品 |
| GET | `/caller/apikey/list?callerId={id}` | API Key 列表 |
| GET | `/caller/apikey/{id}` | API Key 详情 |
| POST | `/caller/apikey` | 创建 API Key；正文包含 `callerId`、`name` |
| PUT | `/caller/apikey/{id}/status` | 状态：`active`、`expired`、`revoked` |
| PUT | `/caller/apikey/{id}/rate-limit` | 限流开关与每分钟上限 |
| DELETE | `/caller/apikey/{id}` | 删除 API Key |
| GET/POST | `/caller/apikey/{id}/interfaces` | 查询或分配接口权限 |
| GET/POST | `/caller/apikey/{id}/products` | 查询或分配产品权限 |
| GET/POST | `/call-scene/list`、`/call-scene` | 公共调用场景 |

创建 API Key 时完整密钥只在创建响应中返回；后续列表不应作为密钥恢复通道。

## 5. OpenAPI 调用与文档

### 单条调用

`POST /openapi/v1/query`

```json
{
  "requestId": "caller-idempotency-key",
  "apiCode": "PROGRAMMER_HISTORY_TODAY",
  "apiVersion": "v1",
  "productCode": "RISK",
  "sceneCode": "DEFAULT",
  "useCache": false,
  "params": {}
}
```

调用链依次执行 API Key 认证、接口/产品授权、请求契约校验、限流、配额、厂商代理、响应契约校验、调用记录和版本化计费。任何 Billing 空响应或安全流水线加载失败均失败关闭。

### 批量调用与文档

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/openapi/v1/batch-query` | 批量调用 |
| GET | `/openapi/v1/docs/interfaces` | API Key 已授权接口文档 |
| GET | `/openapi/v1/docs/interfaces/{apiCode}` | 接口说明 |
| GET | `/openapi/v1/docs/interfaces/{apiCode}/openapi` | OpenAPI 3.1 |
| GET | `/openapi-docs/interfaces/{id}` | 管理端接口文档 |
| GET | `/openapi-docs/interfaces/{id}/openapi` | 管理端 OpenAPI 3.1 |

## 6. 调用记录

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/call-record/list` | 分页列表 |
| POST | `/call-record/query` | 复杂条件查询 |
| GET | `/call-record/{id}` | 详情 |
| GET | `/call-record/stats` | 汇总统计 |
| GET | `/call-record/dimension-stats` | 多维统计 |
| GET | `/call-record/quality-report` | 接口质量报表 |
| GET | `/call-record/export` | 导出 |

## 7. 计费

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/billing/list`、`/billing/{id}` | 账单列表、详情 |
| GET | `/billing/stats`、`/billing/export` | 统计、导出 |
| POST | `/billing/reconciliation/import` | 导入对账文件 |
| POST | `/billing/reconciliation/run` | 执行对账 |
| GET | `/billing/reconciliation/list`、`/diffs` | 对账结果 |
| GET | `/billing/template/list` | 计费模板 |
| GET | `/billing/plan/list`、`/plan/{id}` | 方案列表、详情 |
| POST | `/billing/plan` | 创建草稿 |
| PUT/DELETE | `/billing/plan/{id}` | 更新、删除草稿 |
| POST | `/billing/plan/{id}/next-version` | 创建下一版本 |
| POST | `/billing/plan/{id}/validate`、`/simulate`、`/publish` | 校验、模拟、发布 |
| POST | `/billing/plan/accrue` | 补提周期费用 |
| POST | `/billing/plan/review-contracts` | 检查契约漂移 |
| GET | `/billing/event/list`、`/event/stats` | 事件账本 |
| POST | `/billing/event/{id}/reverse` | 追加冲正事件 |

计费以 `billing_plan` 版本和 `billing_event` 不可变账本为准，不存在旧规则或默认价格回退。

## 8. 治理

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST | `/alert/rule/list`、`/alert/rule` | 告警规则列表、创建 |
| GET/PUT/DELETE | `/alert/rule/{id}` | 详情、更新、删除 |
| PATCH | `/alert/rule/{id}/status` | 更新状态 |
| GET | `/alert/record/list`、`/alert/record/{id}` | 告警记录 |
| POST | `/alert/record/{id}/resolve` | 处理告警 |
| GET | `/alert/health/list` | 服务健康 |
| POST | `/alert/health/{serviceName}/check` | 立即检查 |
| GET | `/log/list`、`/log/{id}`、`/log/stats` | 操作日志 |
| GET | `/log/export` | 导出日志 |
| POST/GET | `/quality/rules` | 创建、查询质量规则 |
| POST | `/quality/check` | 执行质量检查 |
| GET | `/quality/history` | 质量历史 |
| POST | `/trace/lineage` | 创建血缘关系 |
| GET | `/trace/lineage/upstream`、`/downstream`、`/full` | 血缘查询 |

## 9. 错误语义

| HTTP/业务码 | 含义 |
|---|---|
| 400 | 请求结构、状态、约束或配置无效 |
| 401 | 登录 Token 或 API Key 无效 |
| 403 | 权限、产品、接口或内部 scope 不足 |
| 404 | 资源不存在 |
| 409 | 版本冲突或并发更新 |
| 429 | 限流或配额拒绝 |
| 500 | 未处理的服务异常 |
| 502 | 厂商或下游依赖失败 |
| 503 | 服务暂不可用 |

调用方必须同时检查 HTTP 状态和响应 `code`，不得把非 200 业务码当作成功。
