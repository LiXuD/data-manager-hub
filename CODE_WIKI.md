# 数据管理平台 (Data Manager Hub) - Code Wiki

> **项目名称**: 数据管理平台 (Data Management Platform)
> **仓库地址**: https://github.com/LiXuD/data-manager-hub.git
> **文档版本**: 2026-06-16
> **技术栈**: Java 21 + Spring Boot 3.4 + Spring Cloud 2024.0.0 + MyBatis-Plus 3.5.7 + Vue3 + TypeScript

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
| 厂商管理 | 多厂商接入、API配置、数据类型管理、请求/响应映射 |
| 调用管理 | API Key认证、限流、调用代理、调用记录 |
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

#### 3.1.2 data-platform-common-runtime (运行时)

> **路径**: `data-platform-common-runtime/src/main/java/com/dataplatform/common/`
> **职责**: 提供运行时复用代码，包括厂商适配器、认证处理、计费计算、熔断器等。

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
| `AuthInterceptor` | 认证拦截器，验证 Bearer Token |
| `OperationLog` | 操作日志注解 |
| `OperationLogAspect` | AOP切面，拦截 `@OperationLog` 注解方法 |
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
| billing-service | `/api/v1/billing/**` | data-platform-billing | `/api/v1` |
| call-service | `/api/v1/call-record/**` | data-platform-access | `/api/v1` |
| monitor-service | `/api/v1/alert/**` | data-platform-governance | `/api/v1` |
| tenant-service | `/api/v1/tenant/**` | data-platform-identity | `/api/v1` |
| iam-service | `/api/v1/user/**`, `/api/v1/auth/**`, `/api/v1/role/**` | data-platform-identity | `/api/v1` |
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
| `RateLimitFilter` | 限流过滤器 |
| `RequestLogFilter` | 请求日志过滤器 |
| `TraceIdFilter` | 链路追踪ID过滤器 |

**外部系统统一入口**:

| 入口 | 请求格式 | 说明 |
|------|----------|------|
| `POST /openapi/v1/query` | `requestId + apiCode + apiVersion + productCode + sceneCode + useCache/cacheDays + params` | 单条数据查询，按 `apiCode` 解析接口和厂商配置 |
| `POST /openapi/v1/batch-query` | `requestId + apiCode + apiVersion + productCode + sceneCode + useCache/cacheDays + items` | 批量数据查询，逐条记录调用明细 |

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
> **职责**: 厂商管理、数据类型管理、接口定义管理、厂商API配置、灰度规则管理。

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
│   │   ├── VendorInternalController.java  # /internal/vendor
│   │   ├── DataTypeController.java # /datatype
│   │   ├── VendorConfigController.java    # /vendor-config
│   │   ├── VendorConfigInternalController.java  # /internal/vendor-config
│   │   ├── ConfigController.java   # /config
│   │   └── VendorExtendedConfigController.java
│   ├── interface_/                 # 接口相关
│   │   ├── ApiInterfaceController.java      # /interface
│   │   └── ApiInterfaceInternalController.java  # /internal/interface
│   └── graylog/                    # 灰度相关
│       └── GraylogController.java  # /graylog
├── service/
│   ├── vendor/
│   │   ├── VendorService.java
│   │   ├── VendorConfigService.java
│   │   ├── VendorHealthService.java
│   │   └── ParamsMappingService.java
│   ├── interface_/
│   │   ├── ApiInterfaceService.java
│   │   └── InterfaceParamService.java
│   └── graylog/
│       └── GraylogService.java
├── mapper/
│   ├── VendorInfoMapper.java
│   ├── VendorConfigMapper.java
│   ├── DataTypeMapper.java
│   ├── ApiInterfaceMapper.java
│   ├── InterfaceParamMapper.java
│   └── GrayRuleMapper.java
└── entity/
    ├── VendorInfo.java
    ├── VendorConfig.java
    ├── DataType.java
    ├── ApiInterface.java
    ├── InterfaceParam.java
    └── GrayRule.java
```

#### API 端点

| 路径 | 说明 |
|------|------|
| `/vendor` | 厂商 CRUD |
| `/datatype` | 数据类型 CRUD |
| `/vendor-config` | 厂商API配置 |
| `/interface` | 接口定义 CRUD |
| `/graylog` | 灰度规则 CRUD |
| `/internal/vendor-config` | 内部 API (Feign) |
| `/internal/interface` | 内部 API (Feign) |

---

### 3.4 Access 域

> **路径**: `data-platform-access/`
> **端口**: 8082
> **职责**: 调用方管理、API Key生命周期、数据查询代理、调用记录。

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
│   ├── ApiKeyController.java     # /api-key
│   ├── CallerInternalController.java  # /internal/caller
│   ├── CallerService.java
│   ├── ApiKeyService.java
│   └── entity/
│       ├── CallerInfo.java
│       └── ApiKey.java
├── call/                          # 调用/数据查询
│   ├── DataQueryController.java  # /data
│   ├── CallRecordController.java # /call-record
│   ├── DataQueryService.java     # 数据查询核心服务
│   ├── CallRecordService.java
│   ├── RateLimitService.java     # 限流 (基于Redis)
│   ├── VendorProxyService.java   # 厂商代理
│   ├── ParamMappingProcessor.java
│   └── config/
│       ├── KafkaConfig.java      # Kafka配置
│       └── RedisConfig.java       # Redis配置
└── service/
```

#### API 端点

| 路径 | 说明 |
|------|------|
| `/caller` | 调用方 CRUD |
| `/api-key` | API Key 管理 |
| `/data` | 数据查询 `/data/query` |
| `/call-record` | 调用记录查询 |
| `/internal/caller` | 内部 API (Feign - API Key验证) |

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
│   ├── BillingContractController.java  # 内部契约
│   └── BillingInternalController.java  # 内部API
├── service/
│   ├── BillingService.java        # 计费核心服务
│   ├── BudgetAlertService.java     # 预算告警
│   └── ReconciliationService.java  # 对账服务
├── mapper/
│   ├── BillingRuleMapper.java
│   ├── BillingDailyMapper.java
│   └── CallRecordMapper.java
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
| `/internal/billing` | 内部 API |

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
│   ├── IdentityContractController.java  # 内部契约
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
| `/internal/identity` | 内部 API (Feign) |

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
│   ├── GovernanceContractController.java  # 内部契约
│   ├── monitor/                          # 监控告警
│   │   └── AlertController.java          # /alert
│   ├── log/                              # 操作日志
│   │   ├── LogController.java            # /log
│   │   └── InternalLogController.java    # /log/internal
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
| `/log/internal` | 内部 API (日志保存) |
| `/quality` | 质量规则 CRUD、质量评分 |
| `/trace` | 血缘关系查询与管理 |
| `/internal/governance` | 内部 API (Feign) |

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
| `GrayVendorResolverTest` | 灰度厂商路由测试 (14 用例) |
| `CircuitBreakerFilterTest` | Gateway 熔断器测试 |

---

### 3.10 前端模块

> **路径**: `data-platform-web/`
> **端口**: 3000
> **职责**: 基于Vue3的SPA前端应用。

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
│   │   └── components/    # 接口配置组件
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
| `/call` | 调用记录 | 需登录 |
| `/billing` | 计费管理 | 需登录 |
| `/monitor` | 监控告警 | 需登录 |
| `/graylog` | 灰度发布 | 需登录 |
| `/audit` | 操作日志 | 需登录 |
| `/data-test` | 数据查询测试 | 需登录 |
| `/:pathMatch(.*)*` | 404 | 无需登录 |

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
| access | masterdata | `MasterdataFeignClient` | 获取厂商配置、接口定义 |
| access | identity | `IdentityFeignClient` | 验证 API Key |
| billing | access | `CallContractController` | 获取调用记录 |
| governance | - | `GovernanceFeignClient` | 数据质量/血缘查询 |

### 4.3 依赖规则

- **service → api → common-contract**
- 禁止循环依赖
- 域间调用只能通过 `*-api` 模块的 Feign 契约

---

## 5. 数据库设计

### 5.1 数据库信息

- **数据库**: PostgreSQL 16
- **地址**: localhost:5432
- **每个域独立数据库**，按域功能区分

### 5.2 数据表总览

| 序号 | 表名 | 说明 | 所属域 |
|------|------|------|--------|
| 1 | vendor_info | 厂商信息 | masterdata |
| 2 | data_type | 数据类型 | masterdata |
| 3 | vendor_config | 厂商API配置 | masterdata |
| 4 | vendor_config_extended | 厂商扩展配置 | masterdata |
| 5 | api_interface | 接口定义 | masterdata (migration) |
| 6 | interface_param | 接口参数 | masterdata (migration) |
| 7 | gray_rule | 灰度规则 | masterdata |
| 8 | caller_info | 调用方信息 | access |
| 9 | caller_product | 调用方产品配置 | access |
| 10 | api_key | API Key | access |
| 11 | api_key_product | API Key 产品授权 | access |
| 12 | call_scene | 调用场景字典 | access |
| 13 | call_record | 调用记录 (按月分区) | access |
| 14 | billing_rule | 计费规则 | billing |
| 15 | billing_daily | 日账单 | billing |
| 16 | billing_daily_event | 计费事件 (Kafka) | billing |
| 17 | user_info | 用户 | identity |
| 18 | role_info | 角色 | identity |
| 19 | user_role | 用户角色关联 | identity |
| 20 | tenant_info | 租户 | identity |
| 21 | alert_rule | 告警规则 | governance |
| 22 | alert_record | 告警记录 | governance |
| 23 | circuit_breaker | 熔断记录 | governance |
| 24 | operation_log | 操作日志 | governance |

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
| **策略模式** | `VendorAdapter` / `AuthHandler` / `BillingCalculator` | 不同厂商/认证/计费策略可互换 |
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
Gateway (认证 + 限流)
    │
    ▼
DataQueryController (access:8082)
    │
    ├── 限流检查 (RateLimitService)
    │
    ▼
DataQueryService
    │
    ▼
VendorProxyService (获取厂商配置 + 接口定义)
    │
    ├── MasterdataFeignClient → masterdata:8081
    │
    ▼
VendorAdapterFactory.getAdapter(vendorCode)
    │
    ▼
HttpVendorAdapter.execute(config, params)
    ├── transformRequest()  — 请求参数映射
    ├── addSignature()      — 签名生成
    ├── applyAuth()         — 认证注入
    ├── OkHttp 调用厂商API
    └── transformResponse() — 响应数据映射
    │
    ▼
返回结果 + 异步写入调用记录 (Kafka) + 计费
```

---

> **文档维护说明**: 本文档基于项目代码自动分析生成，如项目结构发生变更，请同步更新此文档。
