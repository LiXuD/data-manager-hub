# 数据管理平台 (Data Manager Hub) - Code Wiki

> **项目名称**: 数据管理平台 (Data Management Platform)
> **仓库地址**: https://github.com/LiXuD/data-manager-hub.git
> **文档版本**: 2026-07-20
> **技术栈**: Java 21 + Spring Boot 3.4 + Spring Cloud 2024.0.0 + MyBatis-Plus 3.5.7 + Vue 3 + TypeScript

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
| 厂商管理 | 多厂商接入、API配置、数据类型管理、请求/响应映射、可排序安全流水线 |
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
| 框架 | Spring Boot | 3.4.0 | 应用框架 |
| 微服务 | Spring Cloud | 2024.0.0 | 服务治理 |
| 服务发现 | Nacos | 2023.0.1.0 | 注册中心 + 配置中心 |
| 网关 | Spring Cloud Gateway | - | API路由、鉴权 |
| ORM | MyBatis-Plus | 3.5.7 | 数据库访问 |
| 认证 | Sa-Token | 1.37.0 | 会话管理与认证 |
| 缓存 | Redis (Redisson) | 3.27.0 | 分布式缓存 |
| 熔断 | Resilience4j | 2.2.0 | 熔断与重试 |
| HTTP客户端 | OkHttp | - | 厂商API调用 |
| 工具库 | Hutool | 5.8.28 | 通用工具 |
| 前端框架 | Vue3 + TypeScript | 3.5.0 | SPA前端 |
| UI组件库 | Element Plus | 2.8.0 | UI组件 |
| 状态管理 | Pinia | 2.2.0 | 前端状态 |
| 构建工具 | Vite | 6.0.0 | 前端构建 |
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
| `BusinessException` | 业务异常类 |
| `ErrorCode` | 错误码定义类 |

**枚举类** (`common/enums/`):

| 枚举 | 说明 |
|------|------|
| `CommonStatus` | 通用状态 (ACTIVE/INACTIVE/SUSPENDED) |
| `ApiKeyStatus` | API Key状态 |
| `BillingStatus` | 计费状态 |
| `BillingType` | 计费类型 (STANDARD/TIERED/DYNAMIC) |
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
> **职责**: 提供运行时复用代码，包括厂商适配器、认证处理、安全流水线、计费计算、熔断器等。

##### 厂商适配器体系 (`adapter/`)

采用 **策略模式 + 工厂模式** 实现多厂商API适配：

```
VendorAdapter (接口)
    ├── getVendorCode()                    — 获取厂商编码
    ├── supports(String dataTypeCode)      — 是否支持该数据类型
    ├── execute(config, params)            — 执行数据查询
    ├── transformRequest(params, mapping)  — 请求参数转换
    └── transformResponse(response, mapping) — 响应数据转换

AbstractVendorAdapter (抽象类)
    └── 实现通用的 transformRequest / transformResponse 逻辑

HttpVendorAdapter (具体实现)
    └── 基于 OkHttp 的 HTTP 厂商适配器
        ├── buildRequest()   — 构建HTTP请求 (支持GET/POST)
        ├── applyAuth()      — 应用认证配置
        ├── addSignature()   — 添加签名
        └── handleResponse() — 处理HTTP响应

VendorAdapterFactory (工厂类)
    ├── getAdapter(vendorCode)   — 获取/创建适配器 (ConcurrentHashMap缓存)
    ├── registerAdapter()        — 注册自定义适配器
    └── clearCache()             — 清除缓存
```

**VendorAdapterConfig** — 适配器配置数据类：
- `apiUrl` — API地址
- `method` — 请求方法 (GET/POST)
- `authType` — 认证类型
- `signType` / `secretKey` — 签名类型与密钥
- `requestTemplate` — 请求映射模板
- `responseMapping` — 响应映射模板
- `securitySteps` / `resolvedSecrets` — 按顺序执行的请求、响应安全步骤及运行时解析后的密钥

##### 厂商安全流水线 (`security/pipeline/`)

`HttpVendorAdapter` 在请求参数映射后执行 `REQUEST` 流水线，在厂商响应解析后执行 `RESPONSE` 流水线；未配置新流水线时保留旧 `signType` 签名逻辑作为兼容回退。

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

##### 计费计算器体系 (`billing/`)

| 类名 | 计费类型 | 说明 |
|------|----------|------|
| `BillingCalculator` | 接口 | 计费计算器接口 |
| `StandardBillingCalculator` | STANDARD | 标准计费 (单价 × 调用次数) |
| `TieredBillingCalculator` | TIERED | 阶梯计费 (不同量级不同单价) |
| `DynamicBillingCalculator` | DYNAMIC | 动态计费 (基于响应时间等动态因素) |
| `BillingCalculatorFactory` | 工厂 | 根据 `BillingType` 枚举返回对应计算器 |

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
| `SignatureBuilder` | 签名构建器，支持 HMAC-SHA256 和 MD5 签名算法 |

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
| openapi-access-service | `/openapi/**` | data-platform-access | - |
| vendor-service | `/api/v1/vendor/**`, `/api/v1/config/**`, `/api/v1/datatype/**`, `/api/v1/data/**` | data-platform-masterdata | `/api/v1` |
| caller-service | `/api/v1/caller/**` | data-platform-access | `/api/v1` |
| openapi-docs-access-service | `/api/v1/openapi-docs/**` | data-platform-access | `/api/v1` |
| billing-service | `/api/v1/billing/**` | data-platform-billing | `/api/v1` |
| call-service | `/api/v1/call-record/**` | data-platform-access | `/api/v1` |
| call-scene-service | `/api/v1/call-scene/**` | data-platform-access | `/api/v1` |
| monitor-service | `/api/v1/alert/**` | data-platform-governance | `/api/v1` |
| tenant-service | `/api/v1/tenant/**` | data-platform-identity | `/api/v1` |
| iam-service | `/api/v1/user/**`, `/api/v1/auth/**`, `/api/v1/role/**`, `/api/v1/permission/**` | data-platform-identity | `/api/v1` |
| log-service | `/api/v1/log/**` | data-platform-governance | `/api/v1` |
| graylog-service | `/api/v1/graylog/**` | data-platform-masterdata | `/api/v1` |
| security-service | `/api/v1/security/**` | data-platform-identity | `/api/v1` |
| trace-service | `/api/v1/trace/**` | data-platform-governance | `/api/v1` |
| quality-service | `/api/v1/quality/**` | data-platform-governance | `/api/v1` |
| interface-service | `/api/v1/interface/**` | data-platform-masterdata | `/api/v1` |

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
> **职责**: 厂商管理、数据类型管理、接口契约、厂商API与安全流水线配置、灰度规则管理。

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
| `/interface` | 接口定义 CRUD |
| `/interface/{id}/contract` | 查询或事务性替换完整请求/响应字段树，并自动刷新 Schema 快照 |
| `/interface/{id}/contract/import-schema` | 将兼容 JSON Schema 导入结构化字段；无法无损转换时明确报错 |
| `/interface/{id}/schema`、`/params` | 兼容旧调用的适配接口，统一委托契约服务处理 |
| `/graylog` | 灰度规则 CRUD |
| `/internal/v1/masterdata/interfaces/{id}/contract` | 向 Access 暴露稳定的 `InterfaceContractDTO` Feign 契约 |
| `/internal/v1/masterdata/vendor-security/{configId}` | 向 Access 提供运行时安全步骤；跨域不直连 Masterdata 数据库 |
| `/internal/v1/masterdata/**` | 受 Service JWT 和 `masterdata:read` 保护；厂商密钥另需 `masterdata:vendor-secret:read` |

`interface_param` 是接口契约的唯一结构化数据源：使用 `direction` 区分 `REQUEST`/`RESPONSE`，通过 `parentId` 组织 object/array 子树，并保存类型、必填、默认值、示例、约束和同级排序。`api_interface.request_schema`、`response_schema` 仅作为由字段树自动生成的兼容快照。

---

### 3.4 Access 域

> **路径**: `data-platform-access/`
> **端口**: 8082
> **职责**: 调用方管理、API Key生命周期与授权、滑动窗口限流、契约化数据查询、动态接口文档、调用记录。

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
│   ├── DataQueryController.java  # /data，兼容入口
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
└── service/
```

#### API 端点

| 路径 | 说明 |
|------|------|
| `/caller` | 调用方 CRUD |
| `/caller/apikey` | API Key 管理，以及接口和产品授权 |
| `PUT /caller/apikey/{id}/rate-limit` | 开关并配置该 Key 每分钟最大请求数，同时刷新 Gateway Redis 配置 |
| `/openapi/v1/query`、`/batch-query` | 外部系统单笔/批量调用；执行认证、授权、请求契约、限流、配额和厂商代理 |
| `/data` | 兼容数据查询入口 `/data/query` |
| `/openapi-docs/interfaces/{id}` | 管理端按 `interface:view` 权限查看文档和下载 JSON/YAML |
| `/openapi/v1/docs/interfaces/**` | 调用方用 API Key 查看已授权接口的文档和 OpenAPI，不消耗业务限流或配额 |
| `/call-record` | 调用记录查询 |
| `/internal/v1/access/call-stats` | 向 Masterdata/Billing 提供只读统计，需 `access:stats:read` |

请求契约在进入厂商调用前严格校验，覆盖 object/array 嵌套路径、必填、基础类型、枚举、正则、字符串长度、数值范围、数组长度和格式，并为缺失的可选字段应用默认值；暂时允许未声明的额外字段。响应在厂商映射完成后以及缓存命中链路上校验 `data`，不阻断正常返回，但写入 `call_record.response_contract_*`、结构化日志和 `openapi.response.contract.invalid` 监控计数。

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
│   ├── BillingUsageRecorder.java  # 幂等更新日聚合
│   ├── BudgetAlertService.java     # 预算告警
│   └── ReconciliationService.java  # 对账服务
├── mapper/
│   ├── BillingRuleMapper.java
│   └── BillingDailyMapper.java
├── entity/
│   ├── BillingRule.java
│   ├── BillingDaily.java
│   ├── BillingReconciliation.java
│   └── TenantBudget.java
└── task/
    └── BudgetScheduler.java       # 预算检查定时任务
```

#### API 端点

| 路径 | 说明 |
|------|------|
| `/billing/rule` | 计费规则 CRUD |
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
| `SDKGeneratorService` | SDK代码生成服务（向后兼容旧 API） |
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
| `SignatureBuilderTest` | 签名构建器测试 |
| `BillingCalculatorTest` | 计费计算器测试 |
| `HttpVendorAdapterTest` | HTTP厂商适配器测试 |
| `RequestMappingProcessorTest` | 请求映射处理测试 |
| `ResponseMappingProcessorTest` | 响应映射处理测试 |
| `SecurityPipelineExecutorTest` / `HttpVendorAdapterSecurityPipelineTest` | 安全步骤排序、引用和厂商请求/响应流水线集成测试 |
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

数据测试页会依据所选接口的请求字段自动生成输入项，应用默认值并校验必填项及参数类型；当前通过兼容的 `/interface/{id}/params` API 读取，底层与统一契约服务共用数据源。

接口管理的“配置”分为内部调用契约和厂商接入配置：前者以树形表格维护请求/响应字段、子字段、约束、示例和排序，后者维护厂商参数映射及可排序安全流水线。保存契约后，管理端文档页和调用方文档页会立即使用最新字段树与 Schema 快照生成示例和 OpenAPI 3.1。

调用方管理页在 API Key 列表中展示限流状态和每分钟上限，并提供“启用限流 + 最大请求数”配置对话框。公开文档页中的 API Key 仅保存在 Vue 组件内存，通过 Header 发送，不写入 URL、localStorage 或下载内容。

#### 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5.0 | 前端框架 |
| TypeScript | 5.6.0 | 类型安全 |
| Element Plus | 2.8.0 | UI组件库 |
| Vue Router | 4.5.0 | 路由管理 |
| Pinia | 2.2.0 | 状态管理 |
| Axios | 1.7.0 | HTTP客户端 |
| Vite | 6.0.0 | 构建工具 |
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
| access | masterdata | `ApiInterfaceFeignClient`、`Vendor*InternalFeignClient`、`VendorSecurityInternalFeignClient`、`GraylogInternalFeignClient` | 获取接口契约、厂商配置、安全流水线和灰度规则 |
| access | billing | `BillingInternalFeignClient` | 计算费用并更新幂等日聚合 |
| billing | access | `CallStatsInternalFeignClient` | 获取厂商日调用统计用于对账 |
| masterdata | access | `CallStatsInternalFeignClient` | 获取接口汇总与每日统计 |
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
- **建库方式**: 先执行 `sql/init.sql`，再按顺序执行 `sql/migrations/` 下的迁移脚本

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
| 17 | billing_rule | 计费规则 | billing |
| 18 | billing_daily | 日账单 | billing |
| 19 | billing_daily_event | 计费聚合幂等请求账本 | billing |
| 20 | billing_reconciliation | 计费对账结果 | billing |
| 21 | tenant_info | 租户 | identity |
| 22 | user_info | 用户 | identity |
| 23 | role_info | 角色 | identity |
| 24 | permission | 权限定义 | identity |
| 25 | user_role | 用户角色关联 | identity |
| 26 | role_permission | 角色权限关联 | identity |
| 27 | user_caller | 用户与调用方关联 | identity |
| 28 | encryption_key | 持久化加密密钥元数据 | identity |
| 29 | alert_rule | 告警规则 | governance |
| 30 | alert_record | 告警记录 | governance |
| 31 | circuit_breaker | 熔断记录 | governance |
| 32 | operation_log | 操作日志 | governance |
| 33 | data_lineage | 数据血缘 | governance |
| 34 | quality_rule | 数据质量规则 | governance |
| 35 | quality_score | 数据质量评分 | governance |
| 36 | service_health_check | 服务健康检查记录 | governance |

近期结构变更由 `V013__add_vendor_security_pipeline.sql`、`V014__add_interface_contract_fields.sql` 和 `V016__add_api_key_rate_limit_policy.sql` 提供；升级已有环境时必须按版本顺序执行，不能只覆盖 `init.sql`。

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
| **策略模式** | `VendorAdapter` / `AuthHandler` / `BillingCalculator` / `SecurityStepHandler` | 不同厂商、认证、计费和安全步骤实现可互换 |
| **工厂模式** | `VendorAdapterFactory` / `AuthHandlerFactory` / `BillingCalculatorFactory` | 根据类型创建对应策略实例 |
| **模板方法模式** | `AbstractVendorAdapter` | 定义通用的请求/响应转换流程 |
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
    ├── 可选读取历史调用缓存（命中也执行响应契约校验）
    └── VendorProxyService → 获取厂商配置、安全步骤和灰度结果
    │
    ▼
VendorAdapterFactory.getAdapter(vendorCode)
    │
    ▼
HttpVendorAdapter.execute(config, params)
    ├── transformRequest()  — 请求参数映射
    ├── SecurityPipelineExecutor(REQUEST) — 摘要/签名/加密/注入等有序步骤
    ├── applyAuth()         — 认证注入
    ├── OkHttp 调用厂商API
    ├── SecurityPipelineExecutor(RESPONSE) — 解密/验签/解码等有序步骤
    └── transformResponse() — 响应数据映射
    │
    ▼
OpenApiQueryService
    ├── InterfaceContractValidator → 校验响应data；异常只告警、不阻断
    ├── Access域内Kafka异步写入调用记录和契约异常
    ├── BillingInternalFeignClient → 费用计算与幂等日聚合
    └── 返回OpenApiQueryRespVO
```

**动态接口文档流程**:

```
InterfaceContractService（字段树唯一数据源）
    ├── 自动生成request_schema / response_schema兼容快照
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
