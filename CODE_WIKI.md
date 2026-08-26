# 数据管理平台 (Data Manager Hub) - Code Wiki

> **项目名称**: 数据管理平台 (Data Management Platform)
> **仓库地址**: https://github.com/LiXuD/data-manager-hub.git
> **文档版本**: 2026-08-20
> **技术栈**: Java 21 + Spring Boot 3.4.13 + Spring Cloud 2024.0.3 + MyBatis-Plus 3.5.8 + Vue 3 + TypeScript

---

## 目录

- [1. 项目概述](#1-项目概述)
- [2. 整体架构](#2-整体架构)
- [3. 模块详解](#3-模块详解)
  - [3.1 公共模块](#31-公共模块)
  - [3.2 Gateway 网关](#32-gateway-网关)
  - [3.3 Masterdata 域](#33-masterdata-域)
  - [3.4 Access 域](#34-access-域)
  - [3.5 Billing 域](#35-billing-域)
  - [3.6 Identity 域](#36-identity-域)
  - [3.7 Governance 域](#37-governance-域)
  - [3.8 SDK 模块 (Jar)](#38-sdk-模块)
  - [3.9 测试模块](#39-测试模块)
  - [3.10 前端模块](#310-前端模块)
- [4. 依赖关系](#4-依赖关系)
- [5. 数据库设计](#5-数据库设计)
- [6. 项目运行](#6-项目运行)
- [7. 设计模式](#7-设计模式)

---

## 1. 项目概述

数据管理平台是一个面向银行场景的**微服务架构**数据管理平台，提供数据厂商接入、API调用管理、计费、监控告警、数据治理等全链路数据服务治理能力。

### 核心能力

| 能力域 | 说明 |
|--------|------|
| 厂商管理 | 多厂商接入、数据类型管理、粗粒度连接器插件、单份产品配置、请求/响应映射与安全流水线 |
| 接口契约 | 树形请求/响应字段、JSON Schema快照、运行时校验、OpenAPI 3.1动态文档 |
| 调用管理 | API Key认证与接口/产品授权、滑动窗口限流、配额、调用代理、调用记录 |
| 计费管理 | 标准计费、阶梯计费、动态计费、对账 |
| 监控告警 | 告警规则、告警记录、熔断器 |
| 身份管理 | 租户管理、用户权限、数据脱敏 |
| 数据治理 | 操作日志、数据质量、数据血缘 |

---

## 2. 整体架构

### 2.1 架构拓扑

```
                          ┌──────────────┐
                          │   前端 (Vue3) │ :3000
                          └──────┬───────┘
                                 │
                          ┌──────▼───────┐
                          │   Gateway    │ :8888
                          │  (API网关)    │
                          └──────┬───────┘
                                 │
        ┌─────────────────────────┼─────────────────────────┐
        │                         │                         │
   ┌────▼──────────┐  ┌──────────▼──────────┐  ┌──────────▼──────────┐
   │   Masterdata   │  │       Access        │  │       Billing       │
   │    (8081)      │  │       (8082)        │  │       (8084)        │
   ├───────────────┤  ├────────────────────┤  ├────────────────────┤
   │ 厂商管理       │  │ 调用方管理          │  │ 计费规则            │
   │ 数据类型       │  │ API Key            │  │ 日账单              │
   │ 接口管理       │  │ 调用记录           │  │ 预算告警            │
   │ 灰度规则       │  │ 数据查询代理       │  │ 对账                │
   └────────────────┘  └────────────────────┘  └────────────────────┘
        │                         │                         │
   ┌────▼──────────┐  ┌──────────▼──────────┐                 │
   │   Identity     │  │      Governance     │                 │
   │    (8086)      │  │       (8085)        │                 │
   ├───────────────┤  ├────────────────────┤                 │
   │ 用户管理        │  │ 监控告警            │                 │
   │ 角色权限        │  │ 操作日志           │                 │
   │ 租户管理        │  │ 数据质量           │                 │
   │ 数据加密        │  │ 数据血缘           │                 │
   └────────────────┘  └────────────────────┘
```

### 2.2 技术栈总览

| 层级 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 语言 | Java | 21 | 后端开发 |
| 框架 | Spring Boot | 3.4.13 | 应用框架 |
| 微服务 | Spring Cloud | 2024.0.3 | 服务治理 |
| 服务发现 | Nacos | 2023.0.3.4 | 注册中心 + 配置中心 |
| 网关 | Spring Cloud Gateway | - | API路由、鉴权 |
| ORM | MyBatis-Plus | 3.5.8 | 数据库访问 |
| 认证 | Sa-Token | 1.37.0 | 会话管理与认证 |
| 缓存 | Redis (Redisson) | 3.27.2 | 分布式缓存 |
| 熔断 | Resilience4j | 2.2.0 | 熔断与重试 |
| HTTP客户端 | OkHttp | - | 厂商API调用 |
| 工具库 | Hutool | 5.8.47 | 通用工具 |
| 前端框架 | Vue3 + TypeScript | 3.5.40 / 5.9.3 | SPA前端 |
| UI组件库 | Element Plus | 2.13.7 | UI组件 |
| 状态管理 | Pinia | 2.3.1 | 前端状态 |
| 构建工具 | Vite | 6.4.3 | 前端构建 |
| 代码检查 | ESLint Flat Config | 9.x | Vue 3、TypeScript 与项目代码规范检查 |

### 2.3 模块结构

```
data-manager-hub/
├── pom.xml                              # 父POM (统一版本管理)
├── docker-compose.yml                   # 基础设施容器编排
├── start-services.sh                    # 一键启动脚本
├── stop-services.sh                     # 一键停止脚本

# 公共模块 (分层复用)
├── data-platform-common-contract/       # API契约 (Result, PageResult, 枚举)
├── data-platform-common-web/            # Web层 (拦截器、AOP、工具)
├── data-platform-common-persistence/     # 持久层 (Entity、MyBatis-Plus)
├── data-platform-common-runtime/        # 运行时 (适配器、认证、计费)
├── data-platform-plugin-spi/            # 连接器插件轻量编译契约 Jar
├── data-platform-plugin-testkit/        # 高层连接器 SDK 的真实 runtime 契约 TestKit

# 五域业务模块
├── data-platform-masterdata/            # 厂商/数据类型/接口/灰度 (8081)
│   ├── data-platform-masterdata-api/
│   └── data-platform-masterdata-service/
├── data-platform-access/                # 调用方/API Key/调用 (8082)
│   ├── data-platform-access-api/
│   └── data-platform-access-service/
├── data-platform-billing/                # 计费 (8084)
│   ├── data-platform-billing-api/
│   └── data-platform-billing-service/
├── data-platform-identity/             # 身份/租户/安全 (8086)
│   ├── data-platform-identity-api/
│   └── data-platform-identity-service/
├── data-platform-governance/             # 治理 (8085)
│   ├── data-platform-governance-api/
│   └── data-platform-governance-service/

# 网关与辅助模块
├── data-platform-gateway/               # API网关 (8888)
├── data-platform-sdk/                   # SDK客户端/代码生成 Jar，不独立部署
├── data-platform-test/                  # 集成测试
└── data-platform-web/                   # 前端 (3000)
```

---

## 3. 模块详解

### 3.1 公共模块

#### 3.1.1 data-platform-common-contract (API契约)

> **路径**: `data-platform-common-contract/src/main/java/com/dataplatform/`
> **职责**: 定义跨模块共享的 API 契约类，所有模块均可引用。

| 类名 | 说明 |
|------|------|
| `Result<T>` | 统一返回结果，包含 `code`、`message`、`data`、`requestId`、`timestamp` |
| `PageResult<T>` | 分页返回结果，包含 `records`、`total`、`page`、`pageSize` |
| `ErrorCode` | 错误码定义类 |

**枚举类** (`common/enums/`):

| 枚举 | 说明 |
|------|------|
| `CommonStatus` | 通用状态 (ACTIVE/INACTIVE/SUSPENDED) |
| `ApiKeyStatus` | API Key状态 |
| `BillingStatus` | 计费状态 |
| `CallStatus` | 调用状态 |
| `AlertStatus` | 告警状态 |
| `EnableStatus` | 启用状态 |
| `GrayRuleStatus` | 灰度规则状态 |
| `CodeEnum` | 编码枚举基接口 |
| `EnumUtils` | 枚举工具类 |

**工具类** (`common/util/`):

| 类名 | 说明 |
|------|------|
| `LogTruncationUtil` | 日志截断工具，SHORT=2048 / MEDIUM=4096 / FULL=8192 |
| `SlidingWindowRateLimitAlgorithm` | Gateway 与 Access 共用的 Redis ZSet 滑动窗口 Lua 脚本和唯一请求成员生成规则 |

#### 3.1.2 data-platform-common-runtime (运行时)

> **路径**: `data-platform-common-runtime/src/main/java/com/dataplatform/common/`
> **职责**: 提供连接器插件的 Manifest/Schema 验证、隔离加载、注册表、流水线、受控 Transport、
> 内置 bridge，以及认证、安全、映射、计费计算和熔断复用能力。

##### 当前连接器插件运行时 (`plugin/`)

`data-platform-plugin-spi` 保留 `ConnectorPlugin`、`ConnectorStageFactory`、`ConnectorStage`、
`StageLifecycle`、`PluginContext`、强类型请求/响应、穷举 `ConnectorErrorPolicy` 和六种
`StageCapability`，并增加 `AbstractVendorConnectorPlugin`、不可变 invocation/result、deadline、取消、
幂等事实和受宿主管理的多请求 Session。它只依赖 Jackson，不依赖 Spring、数据库、Redis、Nacos、
Feign 或五域 Service；厂商开发者可用一个入口类实现 builder/parser 及可选处理器。

`data-platform-common-runtime/common/plugin` 实现 Manifest 读取、制品哈希/签名验证、版本隔离
ClassLoader、租约/引用计数注册表、流水线编译执行、受控 OkHttp Transport、统一安全消息处理，以及
内置 `platform-core:1.0.0`、`generic-http:2.0.0` 和 Legacy `legacy-http:1.0.0` bridge。
`platform-core` 独占宿主 Transport、平台安全和 HOST_MAPPING；Generic 只声明厂商 builder、认证处理和
parser，不能绕过 Access NetworkPolicy。两个新内置坐标都由确定性静态 Metadata 提供 descriptor、
canonical Manifest/Schema/permissions 和摘要；Generic 的数据库目录投影必须逐字段精确一致。
Access 对外部 JAR 进行 HTTPS 白名单下载并缓存到
`<cache-directory>/<pluginId>/<version>/<sha256>/connector-plugin.jar`；每个版本可与旧版本同时驻留，
在途请求通过租约固定版本，引用归零后关闭插件和 ClassLoader。

2026-08-10 的隔离运行验收使用真实签名外部插件、HTTPS fixture、双 Access 和浏览器完成供应链、
逐实例 READY/激活失败门禁、Schema/secretRef、受控测试、V1/V2/V3 不可变历史、Gateway 单条/批量、
缓存/契约、NOT_SENT/SENT/MAYBE_SENT、计费/调用事实、离线缓存/readiness、并发版本切换和 release。
V001—V047 fresh/upgrade/HALT/不变性矩阵通过；这证明隔离环境落地，不代表生产部署或生产容量。

2026-08-20 的粗粒度产品模型阶段 0—4 已完成代码与隔离自动化验收：Manifest v2/高层 SDK、
`connectorSpec` 确定性编译、V049/V050、Generic HTTP、Legacy 转换/分页清点和简化前端工作区均已落地。
V049/V050 fresh/upgrade/repeat/HALT/条件回滚脚本以及 Node 24 前端测试通过；逐厂商生产迁移、完整登录态
多服务 E2E、容量/滚动升级和旧 raw 接口最终退役仍未完成，不能据此宣称生产已发布。

插件模式的六阶段顺序为 `REQUEST_BUILDER → REQUEST_PROCESSOR* → TRANSPORT →
RESPONSE_PROCESSOR* → RESPONSE_PARSER* → RESPONSE_NORMALIZER`，启用步骤必须恰好有一个
`TRANSPORT`。执行结果同时表达传输、业务、缓存、计费和 `NOT_SENT/MAYBE_SENT/SENT`；只有错误
策略允许且明确 `NOT_SENT` 才能调用一次备用厂商，防止已发出请求后重复双发。完整安全边界见
[外部请求连接器插件化升级设计](docs/2026-08-03-external-request-connector-plugin-upgrade-design.md)。

> **历史说明**：实施前的 `VendorAdapterFactory + HttpVendorAdapter` 静态链是本设计的原基线。
> V044/V045 完成 PLUGIN-only 迁移后，工厂、HTTP 实现和旧配置字段已经删除；`VendorAdapter`、
> `AbstractVendorAdapter`、`VendorAdapterConfig` 仅由内置 `legacy-http` 的映射 bridge 使用，不构成
> 静态工厂或第二运行时。

##### 厂商安全流水线 (`security/pipeline/`)

内置 `REQUEST_PROCESSOR/RESPONSE_PROCESSOR` 阶段复用安全流水线：请求构建后执行 `REQUEST`，厂商
响应进入解析前执行 `RESPONSE`。流水线配置缺失或加载失败时拒绝调用，不执行简单签名回退。

| 类名 | 说明 |
|------|------|
| `SecurityPipelineExecutor` | 校验步骤引用关系和配置大小，按 `sortNo` 顺序执行启用步骤 |
| `SecurityExecutionContext` | 保存参数、Header、Query、Body、步骤输出与已解析密钥 |
| `SecurityStepHandler` | 安全步骤处理器统一契约 |
| `DefaultSecurityStepHandlers` | 内置字段选择、值生成、规范化、摘要、HMAC、签名、加解密、验签、编解码、注入和字段删除处理器 |

支持的步骤类型为 `FIELD_SELECT`、`GENERATE`、`CANONICALIZE`、`DIGEST`、`HMAC`、`SIGN`、`ENCRYPT`、`DECRYPT`、`VERIFY`、`ENCODE`、`DECODE`、`INJECT`、`REMOVE_FIELD`。摘要支持 MD5、SHA-1、SHA-256、SHA-512、SM3；HMAC 支持 SHA-1/256/512，签名和加解密能力由步骤配置选择。密钥只通过 `secretRef` 解析，保存与返回配置时不回显明文。

##### 认证处理器体系 (`auth/`)

| 类名 | 认证类型 | 说明 |
|------|----------|------|
| `AuthHandler` | 接口 | 认证处理器接口 |
| `NoneAuthHandler` | NONE | 无认证 |
| `BasicAuthHandler` | BASIC | HTTP Basic认证 |
| `BearerAuthHandler` | BEARER | Bearer Token认证 |
| `ApiKeyAuthHandler` | API_KEY | API Key认证 (Header/Query) |
| `AuthHandlerFactory` | 工厂 | 根据认证类型获取对应处理器 |

##### 熔断器 (`circuitbreaker/`)

| 类名 | 说明 |
|------|------|
| `CircuitBreakerManager` | 熔断器与重试管理器，基于 Resilience4j |
| `CircuitBreakerConfiguration` | Spring Boot 自动配置类 |

**熔断器默认配置**:
- 失败率阈值: 50%
- 熔断持续时间: 30秒
- 半开状态允许调用: 5次
- 最大重试次数: 3次

##### 数据映射体系 (`mapping/`)

| 类名 | 说明 |
|------|------|
| `RequestMappingProcessor` | 请求映射处理器，支持字段转换 (uppercase/lowercase/trim) |
| `ResponseMappingProcessor` | 响应映射处理器，支持嵌套路径解析 (如 `data.list`) |
| `MappingException` | 映射异常 |

##### 工具类 (`util/`)

| 类名 | 说明 |
|------|------|
| `DataMaskingUtil` | 数据脱敏 (手机号、身份证、邮箱等) |
| `VariableSubstitutionUtil` | 变量替换，支持 `${variableName}` 格式 |

#### 3.1.3 data-platform-common-web (Web层)

> **路径**: `data-platform-common-web/src/main/java/com/dataplatform/common/`

| 类名 | 说明 |
|------|------|
| `AuthInterceptor` | 用户认证拦截器，通过共享 Redis 中的 Sa-Token 会话验证 Bearer Token |
| `InternalAuthenticationInterceptor` | 服务认证拦截器，校验 Service JWT 的签名、issuer、audience、有效期和 scope |
| `InternalAuthFeignInterceptor` | 仅为带 `@InternalFeignContract` 标记的 Feign 契约获取并注入短期 Service JWT，避免凭证泄漏到公共请求 |
| `ServiceTokenProvider` | 按 audience 缓存 Service JWT，使用连接/读取超时和有限重试，4xx 不重试 |
| `OperationLog` | 操作日志注解 |
| `OperationLogAspect` | 由自动配置注册，拦截 `@OperationLog` 并通过本地或远程实现写日志；日志上下文异常不影响业务 |
| `IpUtil` | IP地址提取，支持 X-Forwarded-For 代理场景 |
| `UserContext` | Sa-Token 用户上下文 |
| `TraceIdMdcFilter` | 读取 X-Trace-Id header → 写入 MDC → 回写 response header |
| `HttpLoggingFilter` | HTTP 请求/响应报文日志，记录每一笔 API 调用的原始报文 |
| `TraceWebAutoConfiguration` | FilterRegistrationBean 自动注册（TraceIdMdcFilter + HttpLoggingFilter） |

#### 3.1.4 data-platform-common-persistence (持久层)

> **路径**: `data-platform-common-persistence/src/main/java/com/dataplatform/common/`

| 类名 | 说明 |
|------|------|
| `MybatisPlusConfig` | MyBatis-Plus 自动配置 (分页插件) |
| `CodeEnumTypeHandler` | 枚举类型处理器 |
| `JsonbTypeHandler` | JSONB类型处理器 |

---

### 3.2 Gateway 网关

> **路径**: `data-platform-gateway/`
> **端口**: 8888
> **职责**: API网关，统一入口路由、跨域处理、认证校验。

**路由规则**:

| 路由ID | 路径匹配 | 目标服务 | 去除前缀 |
|--------|----------|----------|----------|
| access-openapi-route | `/openapi/**` | data-platform-access | - |
| masterdata-vendor-route | `/api/v1/vendor/**`, `/api/v1/config/**`, `/api/v1/datatype/**`, `/api/v1/data/**` | data-platform-masterdata | `/api/v1` |
| access-caller-route | `/api/v1/caller/**` | data-platform-access | `/api/v1` |
| access-openapi-docs-route | `/api/v1/openapi-docs/**` | data-platform-access | `/api/v1` |
| billing-management-route | `/api/v1/billing/**` | data-platform-billing | `/api/v1` |
| access-call-record-route | `/api/v1/call-record/**` | data-platform-access | `/api/v1` |
| access-call-scene-route | `/api/v1/call-scene/**` | data-platform-access | `/api/v1` |
| governance-alert-route | `/api/v1/alert/**` | data-platform-governance | `/api/v1` |
| identity-tenant-route | `/api/v1/tenant/**` | data-platform-identity | `/api/v1` |
| identity-iam-route | `/api/v1/user/**`, `/api/v1/auth/**`, `/api/v1/role/**`, `/api/v1/permission/**` | data-platform-identity | `/api/v1` |
| governance-log-route | `/api/v1/log/**` | data-platform-governance | `/api/v1` |
| masterdata-gray-route | `/api/v1/graylog/**` | data-platform-masterdata | `/api/v1` |
| identity-security-route | `/api/v1/security/**` | data-platform-identity | `/api/v1` |
| governance-trace-route | `/api/v1/trace/**` | data-platform-governance | `/api/v1` |
| governance-quality-route | `/api/v1/quality/**` | data-platform-governance | `/api/v1` |
| masterdata-interface-route | `/api/v1/interface/**` | data-platform-masterdata | `/api/v1` |

**过滤器** (`filter/`):

| 类名 | 说明 |
|------|------|
| `AuthFilter` | 认证过滤器 |
| `RateLimitFilter` | API Key维度的 Redis ZSet 滑动窗口限流；文档路径不计数，Redis异常时拒绝请求 |
| `RequestLogFilter` | 请求日志过滤器 |
| `TraceIdFilter` | 链路追踪ID过滤器 |

**外部系统统一入口**:

| 入口 | 请求格式 | 说明 |
|------|----------|------|
| `POST /openapi/v1/query` | `requestId + apiCode + apiVersion + productCode + sceneCode + useCache/cacheDays + params` | 单条数据查询，按 `apiCode` 解析接口和厂商配置 |
| `POST /openapi/v1/batch-query` | `requestId + apiCode + apiVersion + productCode + sceneCode + useCache/cacheDays + items` | 批量数据查询，逐条记录调用明细 |
| `GET /openapi/v1/docs/interfaces` | `X-Api-Key` 或 `Authorization: Bearer` | 只列出该 Key 已授权且启用的接口，不扣减限流和业务配额 |
| `GET /openapi/v1/docs/interfaces/{apiCode}` | 同上 | 查看动态接口文档；追加 `/openapi?format=json|yaml` 下载 OpenAPI 3.1 |

**API Key 限流**:

- Gateway 从 `openapi:rate_limit:{keyId}` 读取启用开关、60秒窗口和最大请求数，并在 `openapi:window:{keyId}` 中原子维护滑动窗口计数。
- Access 在业务入口使用同一 `SlidingWindowRateLimitAlgorithm` 再做一层防御性校验，配置上限来自 API Key 的 `rateLimit`；关闭限流时两层均跳过计数。
- 限流策略由 Access 保存到 PostgreSQL，并通过 `ApiKeyCacheService` 同步到 Redis；默认每分钟100次，管理端允许配置1至1,000,000次/分钟。

**OpenAPI 归因与缓存**:

| 能力 | 说明 |
|------|------|
| 调用方产品 | `caller_product`，调用方未配置产品则 OpenAPI 调用失败 |
| API Key 产品授权 | `api_key_product`，一把 Key 可绑定多个调用方产品 |
| 公共场景 | `call_scene`，调用时必须传启用的 `sceneCode` |
| 历史缓存 | `useCache=true` 时按 `apiCode + requestHash` 查询 call_record，命中不调用厂商且费用为 0 |
| 多维统计 | `/call-record/dimension-stats` 支持 caller/product/scene/api/vendor/dataType/cacheHit 过滤，分组含 byCaller/byVendor/byDataType 等 |
| 接口质量报表 | `/call-record/quality-report` 按 vendor+dataType+apiCode 分组，含成功率/P50/P95/P99 延迟，默认最近 90 天 |

---

### 3.3 Masterdata 域

> **路径**: `data-platform-masterdata/`
> **端口**: 8081
> **职责**: 厂商管理、数据类型管理、接口契约、厂商 API 与安全流水线配置、连接器插件目录与不可变版本、灰度规则管理。

#### 子模块

| 子模块 | 说明 |
|--------|------|
| `data-platform-masterdata-api` | Feign客户端接口、DTO |
| `data-platform-masterdata-service` | 业务实现 |

#### 内部包结构

```
com.dataplatform.masterdata/
├── MasterdataApplication.java
├── controller/
│   ├── vendor/                    # 厂商相关
│   │   ├── VendorController.java   # /vendor
│   │   ├── VendorInternalController.java  # /internal/v1/masterdata/vendors
│   │   ├── DataTypeController.java # /datatype
│   │   ├── VendorConfigController.java    # /vendor/config
│   │   ├── VendorConfigInternalController.java  # /internal/v1/masterdata/vendor-configs
│   │   ├── VendorSecurityController.java  # /vendor/config/**/security-*
│   │   ├── VendorSecurityInternalController.java  # /internal/v1/masterdata/vendor-security
│   │   ├── ConfigController.java   # /config
│   │   └── VendorExtendedConfigController.java
│   ├── interface_/                 # 接口相关
│   │   ├── ApiInterfaceController.java      # /interface
│   │   └── ApiInterfaceInternalController.java  # /internal/v1/masterdata/interfaces
│   └── graylog/                    # 灰度相关
│       ├── GraylogController.java  # /graylog
│       └── GraylogInternalController.java  # /internal/v1/masterdata/gray-rules
├── connector/                      # 连接器插件与产品控制面
│   ├── controller/                 # /connector-plugin、Legacy /connector
│   ├── compiler/                   # 纯函数 ConnectorSpec 确定性编译器
│   ├── spec/                       # /connector-spec 目录、草稿、测试/发布、历史/回滚、升级、转换、清点
│   ├── service/                    # 签名导入、Schema校验、Legacy兼容与转换器
│   ├── mapper/                     # 插件目录和连接器版本
│   └── entity/
├── service/
│   ├── vendor/
│   │   ├── VendorService.java
│   │   ├── VendorConfigService.java
│   │   ├── VendorHealthService.java
│   │   ├── ParamsMappingService.java
│   │   └── VendorSecurityService.java
│   ├── interface_/
│   │   ├── ApiInterfaceService.java
│   │   ├── InterfaceParamService.java
│   │   └── InterfaceContractService.java
│   └── graylog/
│       └── GraylogService.java
├── mapper/
│   ├── VendorInfoMapper.java
│   ├── VendorConfigMapper.java
│   ├── DataTypeMapper.java
│   ├── ApiInterfaceMapper.java
│   ├── InterfaceParamMapper.java
│   ├── VendorSecurityStepMapper.java
│   ├── VendorSecurityVersionMapper.java
│   └── GrayRuleMapper.java
└── entity/
    ├── VendorInfo.java
    ├── VendorConfig.java
    ├── DataType.java
    ├── ApiInterface.java
    ├── InterfaceParam.java
    ├── VendorSecurityStep.java
    ├── VendorSecurityVersion.java
    └── GrayRule.java
```

#### API 端点

| 路径 | 说明 |
|------|------|
| `/vendor` | 厂商 CRUD |
| `/datatype` | 数据类型 CRUD |
| `/vendor/config` | 厂商API配置、参数映射和连通性测试 |
| `/vendor/config/security-capabilities` | 查询平台支持的安全步骤及算法能力 |
| `/vendor/config/{configId}/security-steps` | 查询或事务性替换请求/响应安全流水线；使用版本号做并发控制 |
| `/vendor/config/{configId}/security-steps/order` | 调整同方向安全步骤顺序 |
| `/vendor/config/{configId}/security-preview`、`/security-test` | 脱敏预览流水线结果、执行厂商连通性测试 |
| `/vendor/config/{configId}/security-versions` | 查询版本历史，并通过 `/{versionId}/rollback` 回滚 |
| `/connector-plugin/**` | 插件目录、签名导入、验证、预加载、逐实例状态、激活和禁用 |
| `/vendor/config/{configId}/connector-spec/catalog`、`/catalog/{pluginId}/versions` | 当前 vendor/dataType 兼容的强类型 SIMPLE 插件与固定版本 |
| `/vendor/config/{configId}/connector-spec/draft`、`/validate`、`/execution-plan` | SIMPLE Spec CAS 草稿、纯校验和脱敏只读计划 |
| `/vendor/config/{configId}/connector-spec/test`、`/publish`、`/versions`、`/rollback/{version}` | 五元组测试事实、不可变发布、安全历史和复制式回滚 |
| `/vendor/config/{configId}/connector-spec/upgrade-preview` | 同插件显式版本的只读 Schema/config/plan 差异与确定性摘要预检 |
| `/vendor/config/{configId}/connector-spec/convert-preview`、`/convert` | Legacy 无损分类预检与 expectedDraftVersion CAS 转换 |
| `/vendor/config/connector-spec/inventory` | Legacy 活动/草稿的分页只读分类；不返回 pipeline、配置或 SecretRef |
| `/vendor/config/{configId}/connector/**` | ADVANCED_LEGACY raw 兼容面；写/测试/发布/回滚拒绝 SIMPLE，validate 仅只读 |
| `/interface` | 接口定义 CRUD |
| `/interface/{id}/contract` | 查询或事务性替换完整请求/响应字段树，并自动刷新派生 Schema |
| `/graylog` | 灰度规则 CRUD |
| `/internal/v1/masterdata/interfaces/{id}/contract` | 向 Access 暴露稳定的 `InterfaceContractDTO` Feign 契约 |
| `/internal/v1/masterdata/vendor-security/{configId}` | 向 Access 提供运行时安全步骤；跨域不直连 Masterdata 数据库 |
| `/internal/v1/masterdata/connector-plugins/**` | 向 Access 提供固定制品描述和当前活动绑定所需版本，需 `masterdata:connector-artifact:read` |
| `/internal/v1/masterdata/vendor-configs/{id}/connector-runtime` | 向 Access 提供不可变连接器运行快照，需 `masterdata:connector-runtime:read` |
| `/internal/v1/masterdata/**` | 受 Service JWT 和 `masterdata:read` 保护；厂商密钥另需 `masterdata:vendor-secret:read` |

`interface_param` 是接口契约的唯一结构化数据源：使用 `direction` 区分 `REQUEST`/`RESPONSE`，通过 `parentId` 组织 object/array 子树，并保存类型、必填、默认值、示例、约束和同级排序。`api_interface.request_schema`、`response_schema` 仅作为由字段树自动生成的派生 Schema，不接受独立写入。

---

### 3.4 Access 域

> **路径**: `data-platform-access/`
> **端口**: 8082
> **职责**: 调用方管理、API Key 生命周期与授权、接口权限申请审批、滑动窗口限流、连接器插件加载与执行、契约化数据查询、动态接口文档、调用记录。

#### 子模块

| 子模块 | 说明 |
|--------|------|
| `data-platform-access-api` | Feign客户端接口、DTO |
| `data-platform-access-service` | 业务实现 |

#### 内部包结构

```
com.dataplatform.access/
├── AccessApplication.java
├── caller/                        # 调用方/API Key
│   ├── CallerController.java      # /caller
│   ├── ApiKeyController.java     # /caller/apikey
│   ├── CallerService.java
│   ├── ApiKeyService.java
│   ├── ApiKeyCacheService.java   # 同步Gateway认证和限流缓存
│   └── entity/
│       ├── CallerInfo.java
│       └── ApiKey.java
├── call/                          # 调用/数据查询
│   ├── OpenApiQueryController.java # /openapi/v1/query、batch-query
│   ├── CallRecordController.java # /call-record
│   ├── CallStatsInternalController.java # /internal/v1/access/call-stats
│   ├── CallStatsQueryService.java # Access 领域统计查询
│   ├── OpenApiQueryService.java  # 契约化调用、缓存、计费与记录
│   ├── InterfaceContractValidator.java # 嵌套字段、类型、默认值和约束校验
│   ├── CallRecordService.java
│   ├── RateLimitService.java     # Redis ZSet滑动窗口限流
│   ├── VendorProxyService.java   # 厂商代理
│   ├── ParamMappingProcessor.java
│   └── config/
│       ├── KafkaConfig.java      # Kafka配置
│       └── RedisConfig.java       # Redis配置
├── docs/
│   ├── OpenApiDocumentController.java       # 管理端文档
│   ├── CallerOpenApiDocumentController.java # API Key调用方文档
│   └── OpenApiDocumentService.java          # OpenAPI 3.1生成
├── approval/
│   ├── controller/               # 申请、任务、授权管理业务 API
│   ├── service/                  # 资格校验、状态机、到期、撤销与紧急授权
│   ├── engine/                   # ApprovalEnginePort 与 Flowable 适配器
│   ├── workflow/                 # 校验、授权、驳回 JavaDelegate 白名单
│   └── domain/                   # 申请、申请项、动作、流程路由和状态
├── connector/                    # 插件运行面
│   ├── artifact/                 # HTTPS下载、哈希地址缓存
│   ├── runtime/                  # 签名密钥、Manifest网络上下文和指标
│   ├── service/                  # 预加载、执行、受控测试和启动同步
│   ├── health/                   # connectorRuntimeReadiness
│   ├── controller/               # Access Internal API
│   ├── mapper/
│   └── entity/
└── service/
```

#### API 端点

| 路径 | 说明 |
|------|------|
| `/caller` | 调用方 CRUD |
| `/caller/apikey` | API Key 管理，以及接口和产品授权 |
| `/api-permission/applications/**` | 草稿、提交、撤回、复制、详情与资格选项 |
| `/api-permission/tasks/**` | 候选任务、认领、释放、节点表单、批准/驳回与流程历史 |
| `/api-permission/grants/**`、`/emergency-grants` | 授权台账、撤销与限时紧急授权 |
| `PUT /caller/apikey/{id}/rate-limit` | 开关并配置该 Key 每分钟最大请求数，同时刷新 Gateway Redis 配置 |
| `/openapi/v1/query`、`/batch-query` | 外部系统单笔/批量调用；执行认证、授权、请求契约、限流、配额和厂商代理 |
| `/openapi-docs/interfaces/{id}` | 管理端按 `interface:view` 权限查看文档和下载 JSON/YAML |
| `/openapi/v1/docs/interfaces/**` | 调用方用 API Key 查看已授权接口的文档和 OpenAPI，不消耗业务限流或配额 |
| `/call-record` | 调用记录查询 |
| `/internal/v1/access/call-stats` | 向 Masterdata/Billing 提供只读统计，需 `access:stats:read` |
| `/internal/v1/access/connector-plugins/**` | 预加载、逐实例状态和释放，分别使用 runtime read/manage scope |
| `/internal/v1/access/vendor-connectors/test` | 执行不计费、不缓存、不写调用记录的脱敏草稿测试，需 `access:connector-runtime:test` |

请求契约在进入厂商调用前严格校验，覆盖 object/array 嵌套路径、必填、基础类型、枚举、正则、字符串长度、数值范围、数组长度和格式，并为缺失的可选字段应用默认值；暂时允许未声明的额外字段。响应在厂商映射完成后以及缓存命中链路上校验 `data`，不阻断正常返回，但写入 `call_record.response_contract_*`、结构化日志和 `openapi.response.contract.invalid` 监控计数。

`VendorProxyService` 按 `VendorConfigDTO.runtimeMode` 选择旧适配器或 `ConnectorVendorExecutor`。
插件执行固定连接器 `pipelineVersion/snapshotHash`，调用记录额外保存实际 `pluginId`、
`pluginVersion`、`pipelineVersion` 和 `snapshotHash`。插件只给出 `billingSignal/cacheSignal`，最终
计费、缓存、熔断和主备厂商仍由 Access 平台编排。

接口权限审批以 `api_key_interface` 为运行时权限事实源，以 `api_permission_action` 为不可变业务审计，以 Flowable `workflow` schema 为流程实例、活动任务和历史事实源。业务代码只依赖 `ApprovalEnginePort`；默认 BPMN 是单节点审批，但适配器和自动化测试覆盖顺序节点、条件网关、并行网关、并行多实例会签、版本并存和服务重启恢复。旧 `POST /caller/apikey/{id}/interfaces` 固定返回 409，避免绕过审批执行全量覆盖。

---

### 3.5 Billing 域

> **路径**: `data-platform-billing/`
> **端口**: 8084
> **职责**: 计费规则管理、日账单生成、预算告警、对账。

#### 子模块

| 子模块 | 说明 |
|--------|------|
| `data-platform-billing-api` | DTO、Entity |
| `data-platform-billing-service` | 业务实现 |

#### 内部包结构

```
com.dataplatform.billing/
├── BillingApplication.java
├── controller/
│   ├── BillingController.java     # /billing
│   └── BillingInternalController.java  # /internal/v1/billing
├── service/
│   ├── BillingService.java        # 计费核心服务
│   ├── BudgetAlertService.java     # 预算告警
│   └── ReconciliationService.java  # 对账服务
├── mapper/
│   └── BillingDailyMapper.java
├── entity/
│   ├── BillingDaily.java
│   ├── BillingReconciliation.java
│   └── TenantBudget.java
└── task/
    └── BudgetScheduler.java       # 预算检查定时任务
```

#### API 端点

| 路径 | 说明 |
|------|------|
| `/billing/daily` | 日账单查询 |
| `/billing/budget` | 预算管理 |
| `/billing/summary` | 账单汇总 |
| `/internal/v1/billing` | 受 Service JWT 和 `billing:calculate` scope 保护的内部 API |

---

### 3.6 Identity 域

> **路径**: `data-platform-identity/`
> **端口**: 8086
> **职责**: 租户管理、用户管理、角色权限管理、数据加密/脱敏。

#### 子模块

| 子模块 | 说明 |
|--------|------|
| `data-platform-identity-api` | Feign客户端、DTO |
| `data-platform-identity-service` | 业务实现 |

#### 内部包结构

```
com.dataplatform.identity/
├── IdentityApplication.java
├── controller/
│   ├── IdentityContractController.java  # Identity 契约
│   ├── InternalTokenController.java     # /internal-auth/v1/token
│   ├── iam/                            # 用户权限
│   │   ├── AuthController.java         # /auth
│   │   ├── UserController.java         # /user
│   │   ├── RoleController.java         # /role
│   │   └── PermissionController.java   # /permission
│   ├── tenant/                         # 租户
│   │   └── TenantController.java       # /tenant
│   └── security/                       # 安全
│       └── EncryptionController.java   # /security
├── service/
│   ├── iam/
│   │   ├── UserService.java
│   │   ├── RoleService.java
│   │   └── PermissionService.java
│   ├── tenant/
│   │   ├── TenantService.java
│   │   └── MaskingService.java
│   └── security/
│       └── EncryptionService.java
├── mapper/
│   ├── UserMapper.java
│   ├── RoleMapper.java
│   ├── TenantMapper.java
│   └── MaskingRuleMapper.java
└── entity/
    ├── iam/
    │   ├── User.java
    │   ├── Role.java
    │   ├── Permission.java
    │   └── UserRole.java
    ├── tenant/
    │   ├── TenantInfo.java
    │   └── MaskingRule.java
    └── security/
        └── EncryptedField.java
```

#### API 端点

| 路径 | 说明 |
|------|------|
| `/auth` | 认证 (login/logout/verify) |
| `/user` | 用户 CRUD |
| `/role` | 角色 CRUD |
| `/permission` | 权限管理 |
| `/tenant` | 租户 CRUD |
| `/security` | 数据加密/解密 |
| `/internal-auth/v1/token` | 使用服务客户端密钥换取短期 Service JWT，不经 Gateway 暴露 |

---

### 3.7 Governance 域

> **路径**: `data-platform-governance/`
> **端口**: 8085
> **职责**: 监控告警、操作日志、数据质量、数据血缘。

#### 子模块

| 子模块 | 说明 |
|--------|------|
| `data-platform-governance-api` | Feign客户端、日志远程服务 |
| `data-platform-governance-service` | 业务实现 |

#### 内部包结构

```
com.dataplatform.governance/
├── GovernanceApplication.java
├── controller/
│   ├── GovernanceContractController.java  # 管理契约
│   ├── GovernanceInternalController.java  # /internal/v1/governance
│   ├── monitor/                          # 监控告警
│   │   └── AlertController.java          # /alert
│   ├── log/                              # 操作日志
│   │   ├── LogController.java            # /log
│   │   └── InternalLogController.java    # /internal/v1/governance/logs
│   ├── quality/                          # 数据质量
│   │   └── QualityController.java       # /quality
│   └── trace/                            # 数据血缘
│       └── DataLineageController.java    # /trace
├── service/
│   ├── monitor/
│   │   └── AlertService.java
│   ├── log/
│   │   └── LogService.java
│   ├── quality/
│   │   └── QualityService.java
│   └── trace/
│       └── DataLineageService.java
├── mapper/
│   ├── AlertRuleMapper.java
│   ├── AlertRecordMapper.java
│   ├── OperationLogMapper.java
│   ├── QualityRuleMapper.java
│   └── DataLineageMapper.java
└── entity/
    ├── monitor/
    │   ├── AlertRule.java
    │   └── AlertRecord.java
    ├── log/
    │   └── OperationLog.java
    ├── quality/
    │   ├── QualityRule.java
    │   └── QualityScore.java
    └── trace/
        └── DataLineage.java
```

#### API 端点

| 路径 | 说明 |
|------|------|
| `/alert` | 告警规则 CRUD、告警记录 |
| `/log` | 操作日志查询 |
| `/internal/v1/governance/logs` | 受 `governance:log` scope 保护的日志写入 API |
| `/quality` | 质量规则 CRUD、质量评分 |
| `/trace` | 血缘关系查询与管理 |
| `/internal/v1/governance` | 受 Service JWT scope 保护的治理内部 API |

---

### 3.8 SDK 模块

> **路径**: `data-platform-sdk/`
> **类型**: 普通 Jar 依赖，不作为 Spring Boot 服务独立部署
> **职责**: SDK 客户端与代码生成工具，支持 Java/Python/Go 三语言，Freemarker 模板引擎驱动。

| 类名 | 说明 |
|------|------|
| `SDKGenerator` | 基于 API 规格生成 Java/Python/Go 多文件 SDK |
| `ApiSpec` | API 端点模型，`fromDefaults()` 硬编码 14 个端点 |
| `SDKCli` | 纯 Java CLI 入口（`--lang`、`--base-url`、`--output`） |

#### Freemarker 模板

| 模板 | 语言 | 说明 |
|------|------|------|
| `client-java.ftl` | Java | HTTP 客户端 |
| `model-java.ftl` | Java | 数据模型 |
| `client-python.ftl` | Python | HTTP 客户端 |
| `model-python.ftl` | Python | 数据模型 |
| `client-go.ftl` | Go | HTTP 客户端 |
| `model-go.ftl` | Go | 数据模型 |

---

### 3.9 测试模块

> **路径**: `data-platform-test/`

#### 集成测试类

| 测试类 | 说明 |
|--------|------|
| `BaseTest` | 基础测试类 |
| `VendorApiTest` | 厂商管理 API 测试 |
| `IAMApiTest` | IAM API 测试 |
| `CallerApiTest` | 调用方 API 测试 |
| `CallApiTest` | 调用服务 API 测试 |
| `BillingApiTest` | 计费 API 测试 |
| `MonitorApiTest` | 监控告警 API 测试 |
| `LogApiTest` | 操作日志 API 测试 |
| `GraylogApiTest` | 灰度发布 API 测试 |
| `InterfaceApiTest` | 接口管理 API 测试 |
| `TenantApiTest` | 租户管理 API 测试 |
| `SdkApiTest` | SDK生成 API 测试 |
| `SecurityApiTest` | 数据安全 API 测试 |
| `TraceApiTest` | 数据血缘 API 测试 |
| `QualityApiTest` | 数据质量 API 测试 |

#### 单元测试类

| 测试类 | 说明 |
|--------|------|
| `RequestMappingProcessorTest` | 请求映射处理测试 |
| `ResponseMappingProcessorTest` | 响应映射处理测试 |
| `ConnectorPipelineExecutionPolicyTest` / `DefaultConnectorVendorExecutorLifecycleTest` | 六阶段执行、租约切换、超时、错误和厂商调用集成测试 |
| `PluginRuntimeConcurrencyTest` / `PluginArtifactVerifierTest` | 多版本并存、引用释放、危险字节码与 ClassLoader 生命周期测试 |
| `VendorSecurityServiceImplTest` / `VendorSecurityControllerAuthorizationTest` | 安全配置版本、并发控制、回滚与权限测试 |
| `InterfaceContractServiceImplTest` / `InterfaceContractValidatorTest` | 契约树、Schema生成、默认值、嵌套类型和约束校验测试 |
| `OpenApiDocumentServiceTest` / `OpenApiDocumentControllerAuthorizationTest` | OpenAPI 3.1生成及管理端/调用方授权测试 |
| `RateLimitServiceTest` / `RateLimitFilterTest` | Access与Gateway共享滑动窗口行为、异常降级和文档免计数测试 |
| `ApiKeyServiceImplRateLimitTest` / `ApiKeyControllerRateLimitTest` | API Key限流策略校验、持久化和缓存同步测试 |
| `GrayVendorResolverTest` | 灰度厂商路由测试 (14 用例) |
| `CircuitBreakerFilterTest` | Gateway 熔断器测试 |

---

### 3.10 前端模块

> **路径**: `data-platform-web/`
> **端口**: 3000
> **职责**: 基于Vue3的SPA前端应用。

数据测试页会依据 `/interface/{id}/contract` 返回的请求字段树自动生成输入项，应用默认值并校验必填项及参数类型。

接口管理的“配置”分为内部调用契约和厂商接入配置：前者以树形表格维护请求/响应字段、子字段、约束、
示例和排序；后者在普通流程中只选择一个固定连接器插件版本并填写一份 Schema 产品表单。页面提供版本
升级预检、响应字段映射、校验/测试/发布、历史/回滚和脱敏只读执行计划，不暴露
`stageKey/capability/order/enabled/TRANSPORT` 编辑。Legacy 只读展示并在能证明无损时提供转换。
产品 API 返回 403 时不会回退 raw API；只有非权限类兼容失败才使用 Legacy 只读 fallback。保存内部契约
后，管理端文档页和调用方文档页会立即使用最新字段树与 Schema 快照生成示例和 OpenAPI 3.1。

调用方管理页在 API Key 列表中展示限流状态和每分钟上限，并提供“启用限流 + 最大请求数”配置对话框。公开文档页中的 API Key 仅保存在 Vue 组件内存，通过 Header 发送，不写入 URL、localStorage 或下载内容。

#### 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5.40 | 前端框架 |
| TypeScript | 5.9.3 | 类型安全 |
| Element Plus | 2.13.7 | UI组件库 |
| Vue Router | 4.6.4 | 路由管理 |
| Pinia | 2.3.1 | 状态管理 |
| Axios | 1.18.1 | HTTP客户端 |
| Vite | 6.4.3 | 构建工具 |
| ESLint | 9.x | Flat Config代码检查，覆盖Vue 3、TypeScript、浏览器和Node配置文件 |

#### 目录结构

```
data-platform-web/src/
├── api/                    # API调用层
│   ├── vendor.ts          # 厂商管理
│   ├── caller.ts           # 调用方管理
│   ├── call.ts             # 调用记录
│   ├── billing.ts          # 计费管理
│   ├── monitor.ts          # 监控告警
│   ├── user.ts             # 用户管理
│   ├── role.ts             # 角色管理
│   ├── tenant.ts           # 租户管理
│   ├── log.ts              # 操作日志
│   ├── graylog.ts          # 灰度发布
│   ├── interface.ts        # 接口管理
│   ├── connector-spec.ts   # SIMPLE 目录、草稿、计划、测试发布、升级和转换
│   ├── openapi-docs.ts     # 管理端动态接口文档
│   ├── datatype.ts         # 数据类型
│   ├── security.ts         # 数据安全
│   ├── trace.ts            # 数据血缘
│   ├── quality.ts          # 数据质量
│   ├── auth.ts             # 认证
│   └── data-query.ts       # 数据查询
├── views/                  # 页面组件
│   ├── login/             # 登录页
│   ├── dashboard/         # 数据概览
│   ├── layout/            # 布局框架
│   ├── vendor/            # 厂商管理
│   ├── connector-plugin/  # 签名插件目录、版本和逐实例激活状态
│   ├── caller/            # 调用方管理
│   ├── datatype/          # 数据类型
│   ├── interface/         # 接口管理
│   │   ├── docs.vue       # 登录态接口文档页
│   │   └── components/    # 契约编辑、文档展示、厂商接入和安全流水线组件
│   ├── openapi-docs/      # API Key调用方公开文档入口
│   ├── call/              # 调用记录
│   ├── billing/           # 计费管理
│   ├── monitor/           # 监控告警
│   ├── graylog/           # 灰度发布
│   ├── audit/             # 操作日志
│   ├── user/              # 用户管理
│   ├── role/              # 角色管理
│   ├── tenant/            # 租户管理
│   ├── security/          # 数据安全
│   ├── trace/             # 数据血缘
│   ├── quality/           # 数据质量
│   ├── data-test/         # 数据查询测试
│   └── profile/           # 个人中心
├── components/             # 通用组件
├── stores/                 # Pinia状态管理
├── utils/                  # 工具函数
├── router/                 # 路由配置
├── App.vue
└── main.ts
```

#### 路由配置

| 路径 | 页面 | 说明 |
|------|------|------|
| `/login` | 登录页 | 已登录跳转 `/dashboard` |
| `/dashboard` | 数据概览 | 需登录 |
| `/tenant` | 租户管理 | 需登录 |
| `/user` | 用户管理 | 需登录 |
| `/role` | 角色管理 | 需登录 |
| `/vendor` | 厂商管理 | 需登录 |
| `/connector-plugin` | 连接器插件目录、版本和激活管理 | 需登录及任一 `connector-plugin:view/import/verify/activate/disable` 权限 |
| `/caller` | 调用方管理 | 需登录 |
| `/datatype` | 数据类型 | 需登录 |
| `/interface` | 接口管理 | 需登录 |
| `/interface/:id/docs` | 单接口动态文档、JSON/YAML下载 | 需登录及 `interface:view` |
| `/call` | 调用记录 | 需登录 |
| `/call-scene` | 场景字典 | 需登录 |
| `/billing` | 计费管理 | 需登录 |
| `/monitor` | 监控告警 | 需登录 |
| `/config` | 配置中心 | 需登录 |
| `/graylog` | 灰度发布 | 需登录 |
| `/audit` | 操作日志 | 需登录 |
| `/data-test` | 数据查询测试 | 需登录 |
| `/profile` | 个人中心 | 需登录 |
| `/openapi-docs` | 调用方输入 API Key 后查看已授权接口文档 | 公开页面，文档接口需 API Key |
| `/:pathMatch(.*)*` | 404 | 无需登录 |

前端代码检查由 `eslint.config.js` 提供，`npm run lint` 执行只读检查，`npm run lint:fix` 执行可自动修复的规则；生成声明、构建产物、覆盖率和依赖目录被排除。

---

## 4. 依赖关系

### 4.1 模块依赖图

```
                         ┌─────────────────────────────────────────┐
                         │        data-platform-common-*          │
                         │  (contract / web / persistence / runtime) │
                         └───────────────────┬─────────────────────┘
                                             │ 被所有服务模块依赖
                         ┌───────────────────┼─────────────────────┐
                         │                   │                     │
              ┌──────────▼──────┐  ┌─────────▼────────┐  ┌───────▼────────┐
              │ masterdata-api │  │   access-api     │  │  billing-api   │
              └───────┬────────┘  └─────────┬────────┘  └───────┬────────┘
                      │                    │                     │
         ┌────────────▼────────┐ ┌─────────▼────────┐ ┌─────────▼────────┐
         │ data-platform-     │ │  data-platform-  │ │  data-platform- │
         │     masterdata     │ │     access       │ │     billing      │
         └────────────────────┘ └──────────────────┘ └──────────────────┘
                        │                    │                     │
              ┌──────────▼──────┐  ┌─────────▼────────┐  ┌───────▼────────┐
              │ identity-api   │  │ governance-api  │  │   gateway      │
              └────────────────┘  └─────────────────┘  └────────────────┘
```

### 4.2 服务间调用 (Feign)

| 调用方 | 被调用方 | 接口 | 说明 |
|--------|----------|------|------|
| access | masterdata | `ApiInterfaceFeignClient`、`Vendor*InternalFeignClient`、`VendorSecurityInternalFeignClient`、`GraylogInternalFeignClient`、`ConnectorPluginInternalFeignClient`、`VendorConnectorInternalFeignClient` | 获取接口契约、厂商配置、安全流水线、灰度规则、连接器制品和固定运行快照 |
| access | billing | `BillingInternalFeignClient` | 计算费用并更新幂等日聚合 |
| billing | access | `CallStatsInternalFeignClient` | 获取厂商日调用统计用于对账 |
| masterdata | access | `CallStatsInternalFeignClient`、`ConnectorPluginActivationInternalFeignClient`、`VendorConnectorRuntimeInternalFeignClient` | 获取统计、编排插件预加载/状态及执行受控测试 |
| access / billing / masterdata / identity | governance | `LogClient` | 写入操作日志 |
| billing | governance | `GovernanceInternalFeignClient` | 写入对账告警 |

### 4.3 依赖规则

- **service → api → common-contract**
- 禁止循环依赖
- 域间同步调用只能通过 `*-api` 模块中带 `@InternalFeignContract` 标记、路径为 `/internal/**` 的 Feign 契约
- 每个内部控制器必须声明 `@InternalScope`，每个客户端按 audience 获取最小 scope
- 领域表仅允许所属域直接访问；Kafka 不跨域传递需要认证和一致性语义的业务调用

---

## 5. 数据库设计

### 5.1 数据库信息

- **数据库**: PostgreSQL 16
- **地址**: 由 `DB_HOST`、`DB_PORT` 和 `DB_NAME` 环境变量配置
- **逻辑归属**: 表按业务域划分；当前本地部署使用同一个 PostgreSQL 数据库
- **建库方式**: 通过 `./migrate-db.sh update` 执行 Liquibase 根变更日志；禁止手工拼接 SQL 建库

### 5.2 数据表总览

| 序号 | 表名 | 说明 | 所属域 |
|------|------|------|--------|
| 1 | vendor_info | 厂商信息 | masterdata |
| 2 | data_type | 数据类型 | masterdata |
| 3 | vendor_config | 厂商API配置 | masterdata |
| 4 | vendor_interface_security_step | 厂商接口请求/响应安全步骤、方向和排序 | masterdata |
| 5 | vendor_interface_security_version | 安全流水线版本快照 | masterdata |
| 6 | vendor_config_extended | 厂商扩展配置 | masterdata |
| 7 | api_interface | 接口定义及自动生成的请求/响应Schema快照 | masterdata |
| 8 | interface_param | 请求/响应统一契约字段树、约束和排序 | masterdata |
| 9 | gray_rule | 灰度规则 | masterdata |
| 10 | caller_info | 调用方信息 | access |
| 11 | caller_product | 调用方产品配置 | access |
| 12 | api_key | API Key、限流开关、每分钟上限和配额 | access |
| 13 | api_key_interface | API Key 接口授权 | access |
| 14 | api_key_product | API Key 产品授权 | access |
| 15 | call_scene | 调用场景字典 | access |
| 16 | call_record | 调用记录、缓存来源和响应契约校验结果 (按月分区) | access |
| 17 | billing_template | 计费算法模板 | billing |
| 18 | billing_plan | 厂商接口版本化计费方案 | billing |
| 19 | billing_plan_tier | 方案阶梯价格 | billing |
| 20 | billing_usage_balance | 账期累计用量 | billing |
| 21 | billing_event | 不可变计费事件账本 | billing |
| 22 | billing_daily | 日账单查询投影 | billing |
| 23 | billing_daily_event | 日账单投影幂等记录 | billing |
| 24 | billing_reconciliation | 计费对账结果 | billing |
| 25 | tenant_info | 租户 | identity |
| 26 | user_info | 用户 | identity |
| 27 | role_info | 角色 | identity |
| 28 | permission | 权限定义 | identity |
| 29 | user_role | 用户角色关联 | identity |
| 30 | role_permission | 角色权限关联 | identity |
| 31 | user_caller | 用户与调用方关联 | identity |
| 32 | encryption_key | 持久化加密密钥元数据 | identity |
| 33 | alert_rule | 告警规则 | governance |
| 34 | alert_record | 告警记录 | governance |
| 35 | circuit_breaker | 熔断记录 | governance |
| 36 | operation_log | 操作日志 | governance |
| 37 | data_lineage | 数据血缘 | governance |
| 38 | quality_rule | 数据质量规则 | governance |
| 39 | quality_score | 数据质量评分 | governance |
| 40 | service_health_check | 服务健康检查记录 | governance |
| 41 | connector_plugin | 插件逻辑身份目录 | masterdata |
| 42 | connector_plugin_version | 不可变签名插件版本和 Manifest/Schema | masterdata |
| 43 | vendor_connector_version | 厂商连接器草稿和不可变发布快照 | masterdata |
| 44 | vendor_connector_test_fact | 受控测试的不可变安全事实及发布门禁 | masterdata |
| 45 | connector_plugin_activation | Access 各实例的插件加载事实 | access |
| 46 | vendor_connector_migration | 已完成迁移计划与三域观察的只读历史 | masterdata |

V042 建立连接器控制面并种入 `legacy-http:1.0.0`；V043—V048 完成迁移观察、PLUGIN-only、旧列退役、
完整性/不可变保护和主备用配置引用。V049 增加 Manifest v2 投影、SIMPLE Spec/编译事实和五元测试门禁；
V050 种入与宿主静态 Metadata 精确一致的 `generic-http:2.0.0`。全新库使用
`./migrate-db.sh update`，旧库先备份并按 `./migrate-db.sh baseline` 接管。V049/U049 与 V050/U050 的
fresh、upgrade、repeat、HALT 原子性和条件回滚分别由 `verify-v049-connector-product-spec.sh`、
`verify-v050-generic-http.sh` 在隔离临时库验证。已受保护或被引用的事实使用版本回滚、备份恢复或
forward recovery，不执行破坏性逆迁移，详见 `sql/MIGRATIONS.md`。

---

## 6. 项目运行

### 6.1 服务启动顺序

```
启动脚本: ./start-services.sh

顺序: 8081 → 8082 → 8084 → 8085 → 8086 → 8888
      masterdata  access  billing governance identity gateway
```

### 6.2 服务端口总览

| 域 | 服务名 | 端口 | 说明 |
|----|--------|------|------|
| - | data-platform-gateway | 8888 | API网关 |
| masterdata | data-platform-masterdata | 8081 | 厂商/接口/灰度 |
| access | data-platform-access | 8082 | 调用方/调用 |
| billing | data-platform-billing | 8084 | 计费 |
| identity | data-platform-identity | 8086 | 身份/租户 |
| governance | data-platform-governance | 8085 | 监控/日志/质量 |
| - | data-platform-web | 3000 | 前端 |

---

## 7. 设计模式

### 7.1 模式汇总

| 模式 | 应用位置 | 说明 |
|------|----------|------|
| **策略模式** | `ConnectorStage` / `ConnectorErrorPolicy` / `SecurityStepHandler` | 阶段能力与平台治理策略分离 |
| **工厂模式** | `ConnectorStageFactory` / `AuthHandlerFactory` | 按固定插件版本创建共享或请求级阶段 |
| **产品编译 + 流水线模式** | `ConnectorSpecCompiler` / `PipelineCompiler` / `ConnectorPipelineExecutor` | 将一份 Spec 确定性编译为隐藏六阶段计划并执行 |
| **AOP模式** | `OperationLogAspect` | 通过注解声明式记录操作日志 |
| **代理模式** | `VendorProxyService` | 代理调用方请求到厂商API |
| **自动配置模式** | Spring Boot AutoConfiguration | common模块自动注册Bean |

### 7.2 核心数据流

**数据查询主流程**:

```
调用方请求
    │
    ▼
Gateway
    ├── AuthFilter：API Key认证并写入keyId上下文
    └── RateLimitFilter：按Key策略执行60秒Redis ZSet滑动窗口
    │
    ▼
OpenApiQueryController (access:8082)
    ├── 校验API Key状态、接口/产品授权、场景和配额
    ├── ApiInterfaceFeignClient → 获取InterfaceContractDTO
    ├── InterfaceContractValidator → 严格校验params并应用默认值
    └── RateLimitService → 业务层滑动窗口防御性校验
    │
    ▼
OpenApiQueryService
    ├── 可选读取历史调用缓存（只复用 response_contract_valid=true 的记录）
    └── VendorProxyService → 获取主备厂商、灰度结果和 PLUGIN-only 配置
    │
    ▼
DefaultConnectorVendorExecutor
    ├── Masterdata Internal API → 固定连接器快照与目录材料
    ├── 校验 V1_DERIVED/V2_EMBEDDED snapshot/integrity hash
    ├── acquire PipelineLease / PluginHandle
    └── PipelineCompiler → 固定 pluginId + pluginVersion + stage config
    │
    ▼
ConnectorPipelineExecutor
    ├── REQUEST_BUILDER
    ├── REQUEST_PROCESSOR* — 摘要/签名/加密/认证等
    ├── TRANSPORT — 宿主管理 HTTPS、超时、响应上限和网络许可
    ├── RESPONSE_PROCESSOR* — 解密/验签/解码等
    ├── RESPONSE_PARSER*
    └── RESPONSE_NORMALIZER
    │
    ▼
OpenApiQueryService
    ├── InterfaceContractValidator → 响应失败转 CONTRACT_VIOLATION 并阻断
    ├── ConnectorErrorPolicy → retry/fallback/delivery/billing/cache/外部错误码
    ├── Access域内Kafka异步写入调用记录和契约异常
    ├── BillingInternalFeignClient → 以 actual vendor/plugin/integrity 事实幂等计费
    └── 返回OpenApiQueryRespVO
```

**动态接口文档流程**:

```
InterfaceContractService（字段树唯一数据源）
    ├── 自动生成 request_schema / response_schema 派生 Schema
    └── ApiInterfaceFeignClient.getContract()
            │
            ▼
OpenApiDocumentService
    ├── 组合平台公共请求外壳与业务params/data
    ├── 固定当前接口apiCode并保留真实query/batch-query路径
    └── 输出页面描述、Curl示例、OpenAPI 3.1 JSON/YAML
            │
            ├── 管理端：登录认证 + interface:view
            └── 调用方：API Key认证 + 接口授权过滤（不扣限流/配额）
```

---

> **文档维护说明**: 本文档基于项目代码自动分析生成，如项目结构发生变更，请同步更新此文档。
