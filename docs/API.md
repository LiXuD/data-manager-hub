# 数据管理平台 HTTP API

> 当前契约索引，最后核对日期：2026-08-11。第 1—9 节描述经 Gateway 暴露的 HTTP API；第 10 节单独记录不经 Gateway 的跨域 Internal API。Feign 契约只位于目标域 `*-api` 模块。

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

平台管理员（拥有 `system:admin`）可在“内部系统管理”中维护当前租户的内部系统，并在接口权限申请中选择当前租户全部启用的内部系统；普通用户仍只能选择通过 `/user/{id}/callers` 明确分配给自己的内部系统。该规则不会跨越租户边界。

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
| GET | `/vendor/config/security-capabilities` | 安全步骤能力 |
| GET/PUT | `/vendor/config/{configId}/security-steps` | 查询或替换安全流水线 |
| PUT | `/vendor/config/{configId}/security-steps/order` | 调整步骤顺序 |
| POST | `/vendor/config/{configId}/security-preview` | 脱敏预览 |
| POST | `/vendor/config/{configId}/security-test` | 安全流水线测试 |
| GET | `/vendor/config/{configId}/security-versions` | 版本历史 |
| POST | `/vendor/config/{configId}/security-versions/{versionId}/rollback` | 回滚版本 |

`signType`、`encryptType` 和简单签名回退已移除；运行时只执行已启用的安全流水线。敏感扩展配置必须以平台 `v1:<keyVersion>:<ciphertext>` 格式存储，否则读取失败关闭。
新厂商配置固定创建为 `runtimeMode=PLUGIN` 且 `inactive`；必须发布活动连接器版本后才能启用。
创建厂商配置时正文只需提供 `interfaceId`、`vendorId` 和执行策略；数据类型由接口服务端推导。同一接口重复绑定同一厂商返回 HTTP 409。

### 3.3 连接器插件与版本化厂商流水线

页面配置步骤、`legacy-http` 每个选项、映射/认证/安全流水线示例以及后端解析流程，见
[接口连接器配置与后端解析指南](./CONNECTOR_CONFIGURATION_GUIDE.md)。

插件目录管理需要对应 `connector-plugin:*` 权限。插件导入只接受受信 HTTPS 制品坐标，不接受本地
JAR 上传；导入过程先完成 SHA-256、Ed25519、Manifest、Schema 和入口类静态验证，成功后直接保存
为 `VERIFIED`。相同 `pluginId + version` 不可覆盖。

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/connector-plugin` | `connector-plugin:view` | 插件目录、活动版本及活动厂商绑定数 `bindingCount` |
| GET | `/connector-plugin/{pluginId}` | `connector-plugin:view` | 插件详情，含活动厂商绑定数 `bindingCount` |
| GET | `/connector-plugin/{pluginId}/versions` | `connector-plugin:view` | 全部不可变版本 |
| POST | `/connector-plugin/versions/import` | `connector-plugin:import` | 从受信仓库导入并验证签名版本 |
| POST | `/connector-plugin/{pluginId}/versions/{version}/verify` | `connector-plugin:verify` | 重新下载并执行静态验证 |
| POST | `/connector-plugin/{pluginId}/versions/{version}/stage` | `connector-plugin:activate` | 请求当前 Access 实例集合预加载 |
| GET | `/connector-plugin/{pluginId}/versions/{version}/activation` | `connector-plugin:view` | 查询逐实例加载事实和聚合 `ready` |
| POST | `/connector-plugin/{pluginId}/versions/{version}/activate` | `connector-plugin:activate` | 仅全部实例 READY 时激活 |
| POST | `/connector-plugin/{pluginId}/versions/{version}/disable` | `connector-plugin:disable` | 禁止新绑定；仍被活动连接器引用时返回 409，历史目录保留 |

导入请求：

```json
{
  "artifactUri": "https://artifacts.example.com/connectors/demo/1.0.0/connector-plugin.jar",
  "expectedSha256": "64位小写十六进制",
  "detachedSignature": "Base64 Ed25519 signature",
  "signingKeyId": "connector-signing-2026"
}
```

厂商连接器路径中的 `{configId}` 是 `vendor_config.id`。普通配置使用
`/vendor/config/{configId}/connector-spec/**` 产品接口；请求只提交一个固定插件版本和一份配置，
不会提交 `stageKey/capability/order/enabled/TRANSPORT` 等运行计划字段：

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/vendor/config/{configId}/connector-spec/catalog` | `connector-plugin:view` | 返回与当前 vendor/dataType 兼容的 Manifest v2 SIMPLE 插件目录 |
| GET | `/vendor/config/{configId}/connector-spec/catalog/{pluginId}/versions` | `connector-plugin:view` | 返回固定插件的 STAGING/ACTIVE 可测试版本 |
| GET | `/vendor/config/{configId}/connector-spec/draft` | `connector-plugin:view` | 查询 SIMPLE 草稿；Legacy 只返回模式和空产品字段，不返回原流水线 |
| PUT | `/vendor/config/{configId}/connector-spec/draft` | `connector-plugin:bind` | `expectedDraftVersion` CAS 保存并确定性编译 Spec |
| POST | `/vendor/config/{configId}/connector-spec/validate` | `connector-plugin:bind` | 对当前已保存 SIMPLE 草稿重新编译并检查全部冻结事实，不写数据 |
| GET | `/vendor/config/{configId}/connector-spec/execution-plan?version={version}` | `connector-plugin:view` | 返回当前/历史只读步骤摘要，不返回 stage config 或 SecretRef |
| POST | `/vendor/config/{configId}/connector-spec/test` | `connector-plugin:test` | Access 执行 V2_EMBEDDED 受控测试，并原子写入无 payload 测试事实 |
| POST | `/vendor/config/{configId}/connector-spec/publish` | `connector-plugin:publish` | 成功测试五元组和全部 Access 实例 READY 后发布不可变版本 |
| GET | `/vendor/config/{configId}/connector-spec/versions` | `connector-plugin:view` | 查询 SIMPLE 与 ADVANCED_LEGACY 的安全历史投影 |
| POST | `/vendor/config/{configId}/connector-spec/rollback/{version}` | `connector-plugin:rollback` | 复制历史冻结事实生成新的 ACTIVE 版本 |
| POST | `/vendor/config/{configId}/connector-spec/upgrade-preview` | `connector-plugin:bind` | 对同一插件的显式目标版本做只读 Schema/config/plan 差异预检 |
| POST | `/vendor/config/{configId}/connector-spec/convert-preview` | `connector-plugin:bind` | 只读判断当前 Legacy 草稿能否无损转换 |
| POST | `/vendor/config/{configId}/connector-spec/convert` | `connector-plugin:bind` | 使用 `expectedDraftVersion` CAS 将当前 Legacy 草稿转换为 SIMPLE |
| GET | `/vendor/config/connector-spec/inventory?page={page}&pageSize={pageSize}` | `connector-plugin:view` | 分页清点 Legacy 活动/草稿并安全分类，默认 1/50、最大 100 |

产品草稿保存请求：

```json
{
  "expectedDraftVersion": 3,
  "connectorSpec": {
    "specVersion": "1",
    "plugin": {"pluginId": "generic-http", "pluginVersion": "2.0.0"},
    "config": {
      "endpoint": "https://api.vendor.example.com/company",
      "method": "POST",
      "auth": {"type": "BEARER", "tokenRef": "vendor.apiToken"}
    },
    "responseMapping": [
      {"targetField": "companyName", "sourcePath": "data.name", "sourceType": "field", "transformType": "none"}
    ]
  }
}
```

`upgrade-preview` 请求为
`{"expectedDraftVersion":3,"targetPluginVersion":"2.1.0"}`；它只返回安全的 Schema/config/plan
变化和预览哈希，不写草稿，也不调用 Access。`convert` 请求为 `{"expectedDraftVersion":3}`；
不可转换返回 HTTP 409、`LEGACY_PIPELINE_NOT_CONVERTIBLE` 和固定安全原因列表，零数据写入。
inventory 只返回 config/version/分类/固定原因等摘要，不返回 pipeline、config 或 SecretRef。

产品受控测试事实精确绑定
`vendorConfigId + draftVersion + specHash + snapshotHash + compileHash`，只保存安全结果摘要和启用插件
坐标；发布必须匹配当前五元组的成功事实，并再次确认全部外部插件在所有 Access 实例 READY。
修改 Spec、安全版本或任何编译事实后都必须重新测试。发布与回滚响应不包含完整 pipeline/config。

现有 `/vendor/config/{configId}/connector/**` 是 ADVANCED_LEGACY 兼容面：

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/vendor/config/{configId}/connector` | `connector-plugin:view` | 当前活动不可变版本；未发布时 `data` 可为空 |
| GET | `/vendor/config/{configId}/connector/draft` | `connector-plugin:view` | 当前 Legacy 草稿/只读兼容投影 |
| PUT | `/vendor/config/{configId}/connector/draft` | `connector-plugin:bind` | 仅已有 ADVANCED_LEGACY 草稿可 CAS 保存完整流水线；空白创建返回 409 `LEGACY_DRAFT_REQUIRED`，SIMPLE 返回 409 |
| POST | `/vendor/config/{configId}/connector/validate` | `connector-plugin:bind` | 只读兼容校验；SIMPLE 也可调用，但不写库、不调用 Access、不产生测试/发布事实 |
| POST | `/vendor/config/{configId}/connector/test` | `connector-plugin:test` | 仅 ADVANCED_LEGACY；SIMPLE 在远程调用和事实写入前返回 409 |
| POST | `/vendor/config/{configId}/connector/publish` | `connector-plugin:publish` | 仅发布 ADVANCED_LEGACY；SIMPLE 在任何写入前返回 409 |
| GET | `/vendor/config/{configId}/connector/versions` | `connector-plugin:view` | 查询发布历史 |
| POST | `/vendor/config/{configId}/connector/rollback/{version}` | `connector-plugin:rollback` | 仅复制 Legacy 历史；SIMPLE 目标返回 409，应使用 connector-spec rollback；退役门禁通过后返回 410 |

除只读 `validate` 外，raw 变更接口遇到 SIMPLE 草稿/版本统一返回 HTTP 409 和
`SIMPLE_CONNECTOR_REQUIRES_PRODUCT_API`，不得覆盖 `connector_spec`、编译哈希或快照事实。
raw PUT 也不再允许从空白创建新的高级草稿，返回 HTTP 409 `LEGACY_DRAFT_REQUIRED`；既有 Legacy 草稿在最终退役门禁前继续保留兼容编辑能力。
当 Masterdata 的 `masterdata.connector-plugin.legacy-write-retired=true` 且数据库确认活动 Legacy 绑定、Legacy 草稿和未结束迁移均为 0 时，raw PUT/POST 写、测试、发布和回滚统一返回 HTTP 410 `CONNECTOR_LEGACY_WRITE_RETIRED`；事实未满足返回 HTTP 409 `CONNECTOR_LEGACY_WRITE_RETIREMENT_GATE_NOT_PASSED`，事实查询异常则失败关闭。只读 GET 和 `validate` 不受该开关影响。

Legacy 保存草稿请求的 `pipelineSnapshot` 最多 50 步，每个启用流水线必须恰好一个 `TRANSPORT`：

```json
{
  "expectedDraftVersion": 3,
  "pipelineSnapshot": [
    {
      "stageKey": "build-request",
      "capability": "REQUEST_BUILDER",
      "pluginId": "legacy-http",
      "pluginVersion": "1.0.0",
      "order": 10,
      "enabled": true,
      "config": {},
      "configHash": "由服务端规范化并重算",
      "artifactSha256": "V2由服务端固化",
      "manifestHash": "V2由服务端固化",
      "schemaHash": "V2由服务端固化"
    }
  ]
}
```

发布请求为 `{"expectedDraftVersion": 4}`；回滚请求为
`{"expectedConnectorVersion": 2}`。乐观锁冲突返回 HTTP 409。受控测试请求为
`{"params": {...}}`，结果只包含 `success/errorCategory/errorCode/safeMessage/normalizedData/stageTimings`，
不返回原始厂商报文或解析后的秘密。

每次 Legacy 受控测试都会追加一条不可修改/删除的安全事实，关联
`vendorConfigId + draftVersion + snapshotHash`，但不保存测试参数、原始响应或标准化数据。发布时若没有
与当前草稿版本和快照哈希精确匹配的成功事实，返回 HTTP 409；修改草稿后必须重新测试。
插件版本激活前也必须已有一条包含该 `pluginId + pluginVersion` 的成功草稿测试事实。

发布版本返回 `snapshotHash/hashAlgorithm/integrityHash`。既有历史使用 `V1_DERIVED`：保留原快照和
哈希，由固定目录材料派生完整性；新发布使用 `V2_EMBEDDED`：步骤固化 Artifact/Manifest/Schema
摘要，快照哈希覆盖全部材料。回滚会复制历史内容形成新版本，不修改旧事实。

Schema `type=string` 的 `x-secret-ref` 字段提交 secretRef 字符串；`x-sensitive` 或字段名具有
password/token/secret/privateKey/certificate 语义时提交 `{"secretRef":"..."}`。后端拒绝明文、
missing secretRef 和跨 vendor secretRef；测试和运行只解析当前阶段实际引用的最小集合。

迁移计划由 Masterdata 持有，普通查询和受保护的逐厂商控制动作分开：

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/vendor/connector-migration?state={state}` | `connector-plugin:view` | 查询已完成迁移及观察事实 |
| POST | `/vendor/connector-migration/{vendorConfigId}/prepare` | `connector-plugin:migrate` | 锁定当前 Legacy 活动版本哈希，创建或重置一条迁移记录 |
| POST | `/vendor/connector-migration/{vendorConfigId}/start-observation` | `connector-plugin:migrate` | 校验当前 SIMPLE 活动版本和两个 Access 实例 READY 后开始观察窗口 |
| POST | `/vendor/connector-migration/{vendorConfigId}/observe` | `connector-plugin:migrate` | 聚合 Access CallRecord 与 BillingEvent，更新错误率/P95/缓存/计费门禁 |
| POST | `/vendor/connector-migration/{vendorConfigId}/complete` | `connector-plugin:migrate` | 仅在观察门禁通过后将记录置为 STABLE |
| POST | `/vendor/connector-migration/{vendorConfigId}/rollback` | `connector-plugin:migrate` | 通过不可变历史版本回滚，并将迁移记录置为 ROLLED_BACK |

所有写动作都要求 `expectedRecordVersion` CAS；响应只包含版本、哈希、状态和聚合事实，不包含请求/响应报文或密钥。阶段 5 这些控制动作不等于生产迁移完成，仍需在目标环境执行真实厂商对等和观察。

V049/U049 增加 Manifest v2、SIMPLE Spec/编译投影和发布冻结约束；V050/U050 种入不可覆盖的
`generic-http:2.0.0` 静态目录事实；V051 将 `call_record.error_code` 扩展到 `VARCHAR(64)`，
覆盖完整平台连接器错误类别；V052 为 `call_record` 增加可空 `interface_id`，让观察聚合按规范接口身份过滤；V053—V057 固化管理权限、计费发布锁、操作日志租户范围、配置版本加密元数据和告警类型宽度；V058 修复 API Key 权限目录父级引用，V059 固化调用场景租户所有权。隔离回归入口分别为
`verify-v049-connector-product-spec.sh` 和 `verify-v050-generic-http.sh`；脚本通过只允许匹配
`dataplatform_v049_*_regression`/`dataplatform_v050_*_regression` 的临时数据库验证 fresh、升级、重复、
漂移/HALT 原子性、条件回滚和重新应用。该证据不表示迁移已在生产数据库执行。

### 3.4 扩展配置

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/vendor/extended-config/list` | 扩展配置列表 |
| GET/PUT/DELETE | `/vendor/extended-config/{id}` | 详情、更新、删除 |
| POST | `/vendor/extended-config` | 创建扩展配置 |
| GET | `/vendor/extended-config/vendor/{vendorId}` | 按厂商查询 |
| PATCH | `/vendor/extended-config/{id}/status` | 更新状态 |
| GET/POST/PUT/DELETE | `/config/**` | 平台配置管理、发布、版本和缓存管理 |

### 3.5 接口契约

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/interface/list`、`/interface/options` | 接口列表、选择项 |
| GET | `/interface/{id}` | 接口详情 |
| GET | `/interface/by-data-type/{dataTypeId}` | 按数据类型查询 |
| POST | `/interface` | 创建接口 |
| PUT/DELETE | `/interface/{id}` | 更新、删除接口 |
| PUT | `/interface/{id}/vendor-routing` | 显式保存主、备用 `vendor_config` 引用 |
| PATCH | `/interface/{id}/status` | 更新状态 |
| GET | `/interface/{id}/contract` | 获取完整请求/响应字段树及生成快照 |
| PUT | `/interface/{id}/contract` | 原子替换完整契约 |
| GET | `/interface/{id}/stats`、`/stats/daily` | 调用统计 |

`interface_param` 字段树是唯一契约数据源。`requestSchema` 和 `responseSchema` 由字段树生成，不能通过普通接口或独立 Schema API 写入；旧 `/schema`、`/params` 和 `import-schema` 端点已删除。约束只使用 JSON `constraintConfig`。

接口创建页面只提交 `apiCode`、名称、数据类型、排序和描述；不再以厂商、旧 `path` 或手工状态作为调用配置，新接口默认 `inactive`。接口详情通过 `/interface/{id}/vendor-routing` 最多保存一个主配置和一个备用配置（备用可为空且不能与主相同）；第一个有效绑定自动成为主配置。接口只有在主配置、活动连接器版本和必要配置就绪后才允许启用。旧 `api_interface.vendor_id`、`path` 和 `vendor_config.fallback_vendor_id` 仅为兼容字段，新 OpenAPI 路由不依赖它们。

示例：

```http
POST /interface
Content-Type: application/json

{"interfaceCode":"company-query","interfaceName":"企业查询","dataTypeId":7,"sort":10,"description":"企业基础信息"}
```

```http
PUT /interface/42/vendor-routing
Content-Type: application/json

{"primaryVendorConfigId":101,"fallbackVendorConfigId":102}
```

### 3.6 灰度与管理端调用测试

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
| PUT | `/caller/{callerId}/products/{productId}` | 修改产品名称、复用条件和状态；产品必须属于指定调用方 |
| GET | `/caller/apikey/list?callerId={id}` | API Key 列表 |
| GET | `/caller/apikey/current-user-options` | 当前登录人关联系统下的有效 API Key 选项；仅返回 Key ID 与掩码 |
| GET | `/caller/apikey/{id}` | API Key 详情 |
| POST | `/caller/apikey` | 创建 API Key 并同步授权产品；正文包含 `callerId`、`name`、非空 `productIds` |
| PUT | `/caller/apikey/{id}/status` | 状态：`active`、`expired`、`revoked` |
| PUT | `/caller/apikey/{id}/rate-limit` | 限流开关与每分钟上限 |
| DELETE | `/caller/apikey/{id}` | 删除 API Key |
| GET | `/caller/apikey/{id}/interfaces` | 查询当前有效接口权限 |
| POST | `/caller/apikey/{id}/interfaces` | 已收口，固定返回 409；改用接口权限申请或紧急授权 |
| GET/POST | `/caller/apikey/{id}/products` | 查询或分配产品权限 |
| GET/POST | `/call-scene/list`、`/call-scene` | 当前租户的调用场景；编码创建后不可变，名称/描述/状态可维护，停用代替物理删除 |
| POST | `/data-test/query` | 登录态测试调用；正文使用 `apiKeyId`，服务端校验用户关联后按该 Key 授权、限流、配额和计费 |

创建 API Key 时会在同一事务中写入 `api_key` 与 `api_key_product`；产品必须启用且属于该 Caller。完整密钥只在创建响应中返回；后续列表不应作为密钥恢复通道。

### 4.1 接口权限申请与审批

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST | `/api-permission/applications` | 分页查询可见申请、创建草稿 |
| GET/PUT | `/api-permission/applications/{id}` | 查看详情与动作轨迹、编辑本人草稿 |
| POST | `/api-permission/applications/{id}/submit` | 使用 `Idempotency-Key` 幂等提交并启动 Flowable 流程 |
| POST | `/api-permission/applications/{id}/cancel`、`/copy` | 撤回/取消、复制为新草稿 |
| GET | `/api-permission/eligible-callers` | 当前用户可管理的 Caller |
| GET | `/api-permission/callers/{callerId}/api-keys` | Caller 下可申请的有效 API Key |
| GET | `/api-permission/interface-options?apiKeyId={id}` | 接口选项及已授权/审批中标记 |
| GET | `/api-permission/tasks`、`/tasks/{taskId}` | 当前用户候选或已认领任务、动态节点表单 |
| POST | `/api-permission/tasks/{taskId}/claim`、`/unclaim` | 认领、释放任务 |
| POST | `/api-permission/tasks/{taskId}/complete` | 按节点允许的决定和字段推进审批 |
| GET | `/api-permission/applications/{id}/process-history` | 流程节点历史 |
| GET | `/api-permission/grants` | 本租户授权台账 |
| POST | `/api-permission/grants/{id}/revoke` | 撤销有效授权，原因必填 |
| GET | `/api-permission/emergency-options/callers` | 紧急授权可选的本租户 Caller |
| GET | `/api-permission/emergency-options/callers/{callerId}/api-keys` | 紧急授权可选的有效 API Key |
| GET | `/api-permission/emergency-options/interfaces?apiKeyId={id}` | 紧急授权可选的启用接口及已有授权标记 |
| POST | `/api-permission/emergency-grants` | 最长 24 小时的增量紧急授权，原因和工单号必填 |

申请草稿可按整单选择是否需要结果缓存，并通过 `requestedCacheDays` 申请 1～365 天时效。审批人必须显式决定是否批准缓存，可用 `approvedCacheDays` 下调但不能超过申请值；最终策略随每个“API Key + 接口”授权事实生效。未申请或未获批缓存、请求 `cacheDays` 超过批准上限时，单条和批量调用均返回 403。紧急授权默认不包含缓存能力。

审批通过只开通“API Key + 接口”权限及其获批缓存策略；调用仍需满足 Caller/API Key、产品、场景、限流、配额、计费和厂商路由条件。授权到期或撤销后，单条、批量和调用方文档均按同一权限谓词立即返回 403，缓存能力同时失效。

## 5. OpenAPI 调用与文档

已内置 UAPI 指定日期程序员历史接口 `PROGRAMMER_HISTORY_BY_DATE`。调用时在
`params` 中传入整数 `month`（1～12）和 `day`（1～31）；平台会先执行接口契约校验，
再向 `GET https://uapis.cn/api/v1/history/programmer` 发送查询参数。

### 单条调用

`POST /openapi/v1/query`

```json
{
  "requestId": "caller-idempotency-key",
  "apiCode": "PROGRAMMER_HISTORY_TODAY",
  "apiVersion": "v1",
  "productCode": "RISK",
  "sceneCode": "DEFAULT",
  "useCache": true,
  "cacheDays": 2,
  "params": {}
}
```

指定日期查询示例：

```json
{
  "requestId": "history-04-04",
  "apiCode": "PROGRAMMER_HISTORY_BY_DATE",
  "apiVersion": "v1",
  "productCode": "RISK",
  "sceneCode": "DEFAULT",
  "useCache": false,
  "params": {
    "month": 4,
    "day": 4
  }
}
```

调用链依次执行 API Key 认证、接口/产品授权与缓存策略校验、请求契约校验、限流、配额、缓存查询/厂商代理、响应契约校验、调用记录和版本化计费。任何 Billing 空响应或安全流水线加载失败均失败关闭。

调用者只提交 `apiCode`，不选择厂商。Access 按 `apiCode → 接口 → primaryVendorConfigId/fallbackVendorConfigId → 精确厂商配置 → 活动连接器版本、流水线、配置和 credential/SecretRef` 解析；路由未 `READY` 或主配置不可用时失败关闭，不按列表顺序或灰度规则改选。主调用只有在 `ConnectorErrorPolicy` 判定安全且 delivery 为 `NOT_SENT` 时才调用一次精确备用配置，`SENT`/`MAYBE_SENT` 不回退，备用配置不会继续链式回退。

备用成功时，调用记录和计费事实中的实际厂商、插件版本和 `fallbackFrom` 均来自备用配置。

`cacheDays` 是从原始厂商成功响应时间开始计算的绝对窗口，不会因缓存命中而续期。可复用记录同时匹配租户、接口代码、接口版本和请求参数；产品默认使用 `CALLER` 作用域隔离不同内部系统，显式配置 `GLOBAL` 时也只允许同租户复用。缓存命中记录只用于审计和计费，不能成为下一次缓存来源。

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

插件调用记录额外返回实际 `pluginId`、`pluginVersion`、`pipelineVersion`、`snapshotHash`、
`hashAlgorithm` 和 `integrityHash`；发生备用厂商切换时仍以实际执行事实为准，不以主厂商、草稿或
当前最新插件版本反推历史。缓存复用只选择 `responseContractValid=true` 的原始成功记录。

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
BillingEvent 同样保存实际 vendor/plugin/pipeline/snapshot/hashAlgorithm/integrityHash；冲正复制原事件的
追踪事实。请求 `NOT_SENT`、响应契约失败或平台策略不允许时不生成收费事件，幂等键只产生一条事件。

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

插件运行时错误通过 `errorCategory` 区分：`CONFIGURATION_ERROR`、`PLUGIN_NOT_READY`、
`PLUGIN_VERSION_MISMATCH`、`REQUEST_BUILD_ERROR`、`AUTH_SECURITY_ERROR`、`TRANSPORT_TIMEOUT`、
`TRANSPORT_CONNECTION_ERROR`、`TRANSPORT_HTTP_ERROR`、`RESPONSE_SECURITY_ERROR`、
`RESPONSE_PARSE_ERROR`、`BUSINESS_REJECTED`、`CONTRACT_VIOLATION`、`PLUGIN_INTERNAL_ERROR`。
`deliveryState` 为 `NOT_SENT`、`MAYBE_SENT` 或 `SENT`。`ConnectorErrorPolicy` 穷举决定 retry、fallback、
熔断、计费/cache 和外部错误码；只有策略允许且明确 `NOT_SENT` 才能调用一次备用厂商，
`SENT/MAYBE_SENT` 禁止降级。`CONTRACT_VIOLATION` 对外失败、不收费、不缓存。

## 10. Internal API（不经 Gateway）

以下路径只能由带正确 audience 和最小 scope 的 Identity Service JWT 调用。Gateway 明确不路由
`/internal/**`；前端、用户 Token 和 API Key 均不能调用。

| 提供方 | 方法 | 路径 | Scope |
|---|---|---|---|
| Masterdata | GET | `/internal/v1/masterdata/connector-plugins/{pluginId}/versions/{version}/artifact` | `masterdata:connector-artifact:read` |
| Masterdata | GET | `/internal/v1/masterdata/connector-plugins/runtime/required-artifacts` | `masterdata:connector-artifact:read` |
| Masterdata | GET | `/internal/v1/masterdata/vendor-configs/{vendorConfigId}/connector-runtime` | `masterdata:connector-runtime:read` |
| Masterdata | POST | `/internal/v1/masterdata/vendor-security/connector-secrets/resolve` | `masterdata:vendor-secret:read` |
| Access | POST | `/internal/v1/access/connector-plugins/stage` | `access:connector-runtime:manage` |
| Access | GET | `/internal/v1/access/connector-plugins/{pluginId}/versions/{version}/activation` | `access:connector-runtime:read` |
| Access | POST | `/internal/v1/access/connector-plugins/{pluginId}/versions/{version}/release` | `access:connector-runtime:manage` |
| Access | POST | `/internal/v1/access/vendor-connectors/test` | `access:connector-runtime:test` |
| Access | POST | `/internal/v1/access/connector-migrations/observation` | `access:connector-runtime:read` |
| Billing | POST | `/internal/v1/billing/connector-migrations/observation` | `billing:connector-observation:read` |

`stage` 请求为 `{"pluginId":"demo","pluginVersion":"1.0.0"}`。激活响应包含
`pluginId/pluginVersion/ready/instances`，每个实例记录 `serviceInstanceId`、制品哈希、宿主版本、
`state`、加载/心跳时间和安全错误摘要。受控测试 Internal 请求包含
`vendorConfigId/pipelineSnapshot/params`，响应在 Access 侧递归脱敏、限深和截断。
secret resolve 请求携带 `vendorConfigId + secretRefs`，Masterdata 验证引用归属后只返回请求的最小集合；
调用方不能用它枚举或跨 vendor 读取秘密。迁移 observation 只读，不提供迁移写入口。
