# 数据管理平台 (Data Manager Hub) - Code Wiki

> **项目名称**: 数据管理平台 (Data Management Platform)
> **仓库地址**: https://github.com/LiXuD/data-manager-hub.git
> **文档版本**: 2026-05-05
> **技术栈**: Java 21 + Spring Boot 3.4 + Spring Cloud 2024.0.0 + MyBatis-Plus 3.5.7 + Vue3 + TypeScript

---

## 目录

- [1. 项目概述](#1-项目概述)
- [2. 整体架构](#2-整体架构)
- [3. 模块详解](#3-模块详解)
  - [3.1 公共模块 (data-platform-common)](#31-公共模块-data-platform-common)
  - [3.2 API契约模块 (data-platform-api)](#32-api契约模块-data-platform-api)
  - [3.3 网关模块 (data-platform-gateway)](#33-网关模块-data-platform-gateway)
  - [3.4 厂商管理 (data-platform-vendor)](#34-厂商管理-data-platform-vendor)
  - [3.5 调用方管理 (data-platform-caller)](#35-调用方管理-data-platform-caller)
  - [3.6 调用服务 (data-platform-call)](#36-调用服务-data-platform-call)
  - [3.7 计费管理 (data-platform-billing)](#37-计费管理-data-platform-billing)
  - [3.8 监控告警 (data-platform-monitor)](#38-监控告警-data-platform-monitor)
  - [3.9 用户权限管理 (data-platform-iam)](#39-用户权限管理-data-platform-iam)
  - [3.10 操作日志 (data-platform-log)](#310-操作日志-data-platform-log)
  - [3.11 灰度发布 (data-platform-graylog)](#311-灰度发布-data-platform-graylog)
  - [3.12 接口管理 (data-platform-interface)](#312-接口管理-data-platform-interface)
  - [3.13 租户管理 (data-platform-tenant)](#313-租户管理-data-platform-tenant)
  - [3.14 SDK生成 (data-platform-sdk)](#314-sdk生成-data-platform-sdk)
  - [3.15 数据安全 (data-platform-security)](#315-数据安全-data-platform-security)
  - [3.16 数据血缘 (data-platform-trace)](#316-数据血缘-data-platform-trace)
  - [3.17 数据质量 (data-platform-quality)](#317-数据质量-data-platform-quality)
  - [3.18 测试模块 (data-platform-test)](#318-测试模块-data-platform-test)
  - [3.19 前端模块 (data-platform-web)](#319-前端模块-data-platform-web)
- [4. 依赖关系](#4-依赖关系)
- [5. 数据库设计](#5-数据库设计)
- [6. 项目运行方式](#6-项目运行方式)
- [7. 设计模式与核心机制](#7-设计模式与核心机制)

---

## 1. 项目概述

数据管理平台是一个面向银行场景的**微服务架构**数据管理平台，提供数据厂商接入、API调用管理、计费、监控告警、灰度发布、数据安全等全链路数据服务治理能力。

### 核心能力

| 能力域 | 说明 |
|--------|------|
| 厂商管理 | 多厂商接入、API配置、数据类型管理、请求/响应映射 |
| 调用管理 | API Key认证、限流、调用代理、调用记录 |
| 计费管理 | 标准计费、阶梯计费、动态计费、对账 |
| 监控告警 | 告警规则、告警记录、熔断器 |
| 安全合规 | 数据脱敏、字段加密、签名验证 |
| 灰度发布 | 灰度规则、流量分配 |
| 数据治理 | 数据血缘、数据质量 |

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
                                 │ Spring Cloud Gateway + Nacos
                 ┌───────────────┼───────────────┐
                 │               │               │
    ┌────────────▼─────┐  ┌─────▼──────┐  ┌────▼──────────┐
    │   核心业务层      │  │  数据服务层  │  │  治理与安全层  │
    ├──────────────────┤  ├────────────┤  ├───────────────┤
    │ vendor   :8081   │  │ call:8083  │  │ monitor :8085 │
    │ caller   :8082   │  │ log  :8090 │  │ security:8094 │
    │ iam      :8093   │  │ trace:8095 │  │ quality :8096 │
    │ tenant   :8086   │  │            │  │ graylog :8092 │
    └──────────────────┘  └────────────┘  └───────────────┘
                 │               │               │
    ┌────────────▼───────────────▼───────────────▼────┐
    │              基础设施层                          │
    │  PostgreSQL:5432 │ Redis:6379 │ Nacos:8848      │
    │  Kafka:9092      │ Prometheus:9090 │ Grafana:3100│
    └─────────────────────────────────────────────────┘
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
| 消息队列 | Kafka | 3.2.0 | 异步消息 |
| 熔断 | Resilience4j | - | 熔断与重试 |
| HTTP客户端 | OkHttp | - | 厂商API调用 |
| 工具库 | Hutool | 5.8.28 | 通用工具 |
| 前端框架 | Vue3 + TypeScript | 3.5.0 | SPA前端 |
| UI组件库 | Element Plus | 2.8.0 | UI组件 |
| 状态管理 | Pinia | 2.2.0 | 前端状态 |
| 构建工具 | Vite | 6.0.0 | 前端构建 |
| 图表 | ECharts | 5.5.0 | 数据可视化 |

### 2.3 模块结构

```
data-manager-hub/
├── pom.xml                              # 父POM (统一版本管理)
├── docker-compose.yml                   # 基础设施容器编排
├── start-services.sh                    # 一键启动脚本
├── stop-services.sh                     # 一键停止脚本
├── data-platform-api/                   # 公共API契约 (Result, PageResult, ErrorCode)
├── data-platform-common/                # 公共模块 (适配器、认证、计费、日志、工具)
├── data-platform-gateway/               # API网关 (8888)
├── data-platform-vendor/                # 厂商管理 (8081) [含配置中心、数据类型]
│   ├── data-platform-vendor-api/
│   └── data-platform-vendor-service/
├── data-platform-caller/                # 调用方管理 (8082)
│   ├── data-platform-caller-api/
│   └── data-platform-caller-service/
├── data-platform-call/                  # 调用服务 (8083)
│   ├── data-platform-call-api/
│   └── data-platform-call-service/
├── data-platform-billing/               # 计费管理 (8084)
│   ├── data-platform-billing-api/
│   └── data-platform-billing-service/
├── data-platform-monitor/               # 监控告警 (8085)
│   ├── data-platform-monitor-api/
│   └── data-platform-monitor-service/
├── data-platform-tenant/                # 租户管理 (8086)
│   ├── data-platform-tenant-api/
│   └── data-platform-tenant-service/
├── data-platform-sdk/                   # SDK生成 (8087)
├── data-platform-log/                   # 操作日志 (8090)
│   ├── data-platform-log-api/           # LogClient Feign接口 + RemoteOperationLogService
│   └── data-platform-log-service/
├── data-platform-graylog/               # 灰度发布 (8092)
│   ├── data-platform-graylog-api/
│   └── data-platform-graylog-service/
├── data-platform-iam/                   # 用户权限管理 (8093) [含用户、角色]
│   ├── data-platform-iam-api/
│   └── data-platform-iam-service/
├── data-platform-security/              # 数据安全 (8094)
├── data-platform-trace/                 # 数据血缘 (8095)
│   ├── data-platform-trace-api/
│   └── data-platform-trace-service/
├── data-platform-quality/               # 数据质量 (8096)
│   ├── data-platform-quality-api/
│   └── data-platform-quality-service/
├── data-platform-interface/             # 接口管理 (8097)
│   ├── data-platform-interface-api/
│   └── data-platform-interface-service/
├── data-platform-test/                  # 集成测试
│   ├── data-platform-test-api/
│   └── data-platform-test-service/
└── data-platform-web/                   # 前端 (3000)
```

---

## 3. 模块详解

### 3.1 公共模块 (data-platform-common)

> **路径**: `data-platform-common/`
> **职责**: 提供所有微服务共享的基础设施代码，包括统一响应封装、厂商适配器、认证处理、计费计算、操作日志、数据映射、熔断器、工具类等。

#### 3.1.1 统一响应封装 (`result/`)

| 类名 | 说明 |
|------|------|
| `Result<T>` | 统一API返回结果，包含 `code`、`message`、`data`、`requestId`、`timestamp`、`latency` |
| `PageResult<T>` | 分页返回结果，包含 `list`、`total`、`page`、`pageSize` |

**Result 核心方法**:
- `Result.success()` / `Result.success(T data)` — 成功响应 (code=0)
- `Result.error(Integer code, String message)` — 错误响应
- `Result.error(String message)` — 通用错误 (code=4001)

#### 3.1.2 厂商适配器体系 (`adapter/`)

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

**VendorAdapterConfig** — 适配器配置数据类，包含：
- `apiUrl` — API地址
- `method` — 请求方法 (GET/POST)
- `authType` — 认证类型
- `authConfig` — 认证配置
- `signType` / `secretKey` — 签名类型与密钥
- `headers` — 自定义请求头
- `requestTemplate` — 请求映射模板
- `responseMapping` — 响应映射模板

#### 3.1.3 认证处理器体系 (`auth/`)

采用 **策略模式 + 工厂模式** 实现多种API认证方式：

| 类名 | 认证类型 | 说明 |
|------|----------|------|
| `AuthHandler` | 接口 | 认证处理器接口，定义 `getAuthType()`、`applyAuth()`、`validateConfig()` |
| `NoneAuthHandler` | NONE | 无认证 |
| `BasicAuthHandler` | BASIC | HTTP Basic认证 |
| `BearerAuthHandler` | BEARER | Bearer Token认证 |
| `ApiKeyAuthHandler` | API_KEY | API Key认证 (Header/Query) |
| `AuthHandlerFactory` | 工厂 | 根据认证类型获取对应处理器，支持动态注册 |

#### 3.1.4 计费计算器体系 (`billing/`)

采用 **策略模式 + 工厂模式** 实现多种计费策略：

| 类名 | 计费类型 | 说明 |
|------|----------|------|
| `BillingCalculator` | 接口 | 计费计算器接口，定义 `calculate()` 和 `calculateSingle()` |
| `StandardBillingCalculator` | STANDARD | 标准计费 (单价 × 调用次数) |
| `TieredBillingCalculator` | TIERED | 阶梯计费 (不同量级不同单价) |
| `DynamicBillingCalculator` | DYNAMIC | 动态计费 (基于响应时间等动态因素) |
| `BillingCalculatorFactory` | 工厂 | 根据 `BillingType` 枚举返回对应计算器 |

#### 3.1.5 操作日志体系 (`log/`)

采用 **AOP + 注解** 实现声明式操作日志：

| 类/注解 | 说明 |
|---------|------|
| `@OperationLog` | 注解，声明在Controller方法上，属性：`module`、`operation`、`description`、`saveParams`、`saveResult` |
| `OperationLogAspect` | AOP切面，拦截 `@OperationLog` 注解方法，自动记录操作人、IP、参数、返回结果、耗时 |
| `OperationLogRecord` | 日志记录数据类 |
| `OperationLogService` | 日志保存接口 (由各模块实现或通过 `RemoteOperationLogService` 远程保存) |

**日志记录字段**: module、operation、description、method、userId、username、ip、params、result、duration、status、errorMsg

**日志大小限制**: 8KB (`MAX_LOG_LENGTH = 8192`)

#### 3.1.6 数据映射体系 (`mapping/`)

| 类名 | 说明 |
|------|------|
| `RequestMappingItem` | 请求映射项 (sourceVar → targetField, required, defaultValue, transformType) |
| `RequestMappingProcessor` | 请求映射处理器，支持字段转换 (uppercase/lowercase/trim) |
| `ResponseMappingItem` | 响应映射项 (sourcePath → targetField, transformType) |
| `ResponseMappingProcessor` | 响应映射处理器，支持嵌套路径解析 (如 `data.list`) |
| `MappingException` | 映射异常 |

#### 3.1.7 熔断器 (`circuitbreaker/`)

| 类名 | 说明 |
|------|------|
| `CircuitBreakerManager` | 熔断器与重试管理器，基于 Resilience4j |
| `CircuitBreakerConfiguration` | Spring Boot 自动配置类 |

**熔断器默认配置**:
- 失败率阈值: 50%
- 熔断持续时间: 30秒
- 半开状态允许调用: 5次
- 滑动窗口: 10次 (COUNT_BASED)

**重试默认配置**:
- 最大重试次数: 3次
- 重试间隔: 500ms

**核心方法**:
- `executeWithProtection(vendorCode, supplier)` — 带熔断+重试保护执行
- `getCircuitBreakerState(vendorCode)` — 获取熔断器状态
- `forceOpen(vendorCode)` / `forceClose(vendorCode)` — 强制开关

#### 3.1.8 工具类 (`util/`)

| 类名 | 说明 | 核心方法 |
|------|------|----------|
| `UserContext` | Sa-Token 用户上下文 | `getCurrentUserId()`, `getCurrentUsername()`, `isLoggedIn()`, `login()`, `logout()` |
| `IpUtil` | IP地址提取 | `getClientIp(request)` — 支持 X-Forwarded-For 代理场景 |
| `DataMaskingUtil` | 数据脱敏 | `maskPhone()`, `maskIdCard()`, `maskEmail()`, `maskBankCard()`, `maskName()`, `maskAddress()`, `maskInLog()` |
| `VariableSubstitutionUtil` | 变量替换 | `substitute(value, context)` — 支持 `${variableName}` 格式替换 |

#### 3.1.9 其他组件

| 类名 | 说明 |
|------|------|
| `AuthInterceptor` | 认证拦截器，验证 Bearer Token，排除 `/auth/login`、`/auth/verify`、`/actuator`、`/health` |
| `SignatureBuilder` | 签名构建器，支持 HMAC-SHA256 和 MD5 签名算法 |
| `MybatisPlusConfig` | MyBatis-Plus 自动配置 (分页插件) |
| `StatusConstants` | 状态常量 (SUCCESS/FAIL) |

#### 3.1.10 枚举类 (`enums/`)

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
| `ConditionType` | 条件类型 |
| `ErrorCode` | 错误码 |
| `CodeEnum` | 编码枚举基接口 |
| `EnumUtils` | 枚举工具类 |

#### 3.1.11 自动配置

通过 Spring Boot 自动配置机制 (`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`) 注册：
- `com.dataplatform.common.config.MybatisPlusConfig`
- `com.dataplatform.common.circuitbreaker.CircuitBreakerConfiguration`

---

### 3.2 API契约模块 (data-platform-api)

> **路径**: `data-platform-api/`
> **职责**: 定义跨模块共享的API契约类，所有模块均可引用。

| 类名 | 说明 |
|------|------|
| `Result<T>` | 统一返回结果 (与 common 模块的 Result 类似，供 Feign 接口使用) |
| `PageResult<T>` | 分页返回结果 |
| `BusinessException` | 业务异常类 |
| `ErrorCode` | 错误码定义类 |

---

### 3.3 网关模块 (data-platform-gateway)

> **路径**: `data-platform-gateway/`
> **端口**: 8888
> **职责**: API网关，统一入口路由、跨域处理、认证校验。

**核心配置**:

| 配置项 | 值 |
|--------|-----|
| 应用类型 | `reactive` (WebFlux) |
| 服务发现 | Nacos (localhost:8848, namespace=dev) |
| 连接超时 | 5000ms |
| 响应超时 | 10s |
| 认证方式 | Sa-Token (Bearer Token, UUID风格, 7200秒超时) |
| Redis | localhost:6379, password=redis_password |

**路由规则**:

| 路由ID | 路径匹配 | 目标服务 |
|--------|----------|----------|
| vendor-service | `/api/v1/vendor/**`, `/api/v1/config/**`, `/api/v1/data-type/**`, `/api/v1/datatype/**` | data-platform-vendor-service |
| caller-service | `/api/v1/caller/**` | data-platform-caller-service |
| billing-service | `/api/v1/billing/**` | data-platform-billing-service |
| call-service | `/api/v1/data/**`, `/api/v1/call-record/**` | data-platform-call-service |
| monitor-service | `/api/v1/alert/**` | data-platform-monitor-service |
| tenant-service | `/api/v1/tenant/**` | data-platform-tenant-service |
| iam-service | `/api/v1/user/**`, `/api/v1/auth/**`, `/api/v1/role/**` | data-platform-iam-service |
| log-service | `/api/v1/log/**` | data-platform-log-service |
| graylog-service | `/api/v1/graylog/**` | data-platform-graylog-service |
| sdk-service | `/api/v1/sdk/**` | data-platform-sdk |
| security-service | `/api/v1/security/**` | data-platform-security |
| trace-service | `/api/v1/trace/**` | data-platform-trace-service |
| quality-service | `/api/v1/quality/**` | data-platform-quality-service |
| interface-service | `/api/v1/interface/**` | data-platform-interface-service |

**全局过滤器**: `DedupeResponseHeader` — 去重 CORS 响应头

**CORS配置**: 允许所有来源、所有方法、所有头部，支持凭证，最大缓存3600秒

**启动类**: `GatewayApplication`

---

### 3.4 厂商管理 (data-platform-vendor)

> **路径**: `data-platform-vendor/`
> **端口**: 8081
> **职责**: 管理数据厂商信息、厂商API配置、扩展配置、配置版本、数据类型。合并了原 `data-platform-datatype` 和 `data-platform-config` 模块。

#### 子模块

| 子模块 | 说明 |
|--------|------|
| `data-platform-vendor-api` | 实体定义 (VendorInfo, DataType, VendorConfig 等) |
| `data-platform-vendor-service` | 业务实现 |

#### Controller层

| Controller | 路径前缀 | 说明 | 核心API |
|------------|----------|------|---------|
| `VendorController` | `/vendor` | 厂商CRUD | `GET /list`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}`, `PATCH /{id}/status`, `POST /{id}/test`, `GET /all` |
| `DataTypeController` | `/datatype` | 数据类型管理 | CRUD + 按厂商查询 |
| `VendorConfigController` | `/vendor-config` | 厂商API配置 | CRUD + 按厂商/数据类型查询 |
| `VendorExtendedConfigController` | `/vendor-extended-config` | 扩展配置 | CRUD |
| `ConfigController` | `/config` | 配置中心 | 配置查询与版本管理 |
| `VendorConfigInternalController` | `/internal/vendor-config` | 内部API | 供其他服务Feign调用 |
| `VendorInternalController` | `/internal/vendor` | 内部API | 供其他服务Feign调用 |

#### Service层

| Service | 说明 |
|---------|------|
| `VendorService` | 厂商信息管理 (CRUD、状态变更、连通性测试) |
| `DataTypeService` | 数据类型管理 |
| `VendorConfigService` | 厂商API配置管理 |
| `VendorExtendedConfigService` | 扩展配置管理 |
| `ConfigService` | 配置中心管理 |

#### Entity层

| Entity | 对应表 | 说明 |
|--------|--------|------|
| `VendorInfo` | vendor_info | 厂商信息 (vendorCode, vendorName, vendorType, status, contact, description) |
| `DataType` | data_type | 数据类型 (code, name, category, vendorId) |
| `VendorConfig` | vendor_config | 厂商API配置 (apiUrl, method, authType, signType, requestTemplate, responseMapping) |
| `VendorConfigExtended` | vendor_config_extended | 扩展配置 |
| `ConfigVersion` | config_version | 配置版本历史 |

#### 启动类

`VendorApplication`

---

### 3.5 调用方管理 (data-platform-caller)

> **路径**: `data-platform-caller/`
> **端口**: 8082
> **职责**: 管理API调用方信息、API Key生命周期。

#### Controller层

| Controller | 路径前缀 | 说明 | 核心API |
|------------|----------|------|---------|
| `CallerController` | `/caller` | 调用方CRUD | `GET /list`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}`, `PATCH /{id}/status` |
| `ApiKeyController` | `/api-key` | API Key管理 | `GET /list`, `POST`, `PUT /{id}`, `DELETE /{id}`, `POST /{id}/refresh` |
| `CallerInternalController` | `/internal/caller` | 内部API | API Key验证 (供call服务Feign调用) |

#### Service层

| Service | 说明 |
|---------|------|
| `CallerService` | 调用方信息管理 |
| `ApiKeyService` | API Key管理 (生成、刷新、验证) |

#### Entity层

| Entity | 对应表 | 说明 |
|--------|--------|------|
| `CallerInfo` | caller_info | 调用方信息 (name, contact, status, tenantId, rateLimit) |
| `ApiKey` | api_key | API Key (key, secret, callerId, status, expireTime, rateLimit) |

#### VO层

| VO | 说明 |
|----|------|
| `CallerSaveVO` | 新增调用方请求 |
| `CallerUpdateVO` | 更新调用方请求 |
| `CallerDetailVO` | 调用方详情 (含API Key列表) |
| `ApiKeySaveVO` | 新增API Key请求 |
| `ApiKeyUpdateVO` | 更新API Key请求 |

#### 启动类

`CallerApplication`

---

### 3.6 调用服务 (data-platform-call)

> **路径**: `data-platform-call/`
> **端口**: 8083
> **职责**: 核心数据查询代理服务，接收调用方请求，通过厂商适配器转发至厂商API，记录调用记录，提供限流和缓存能力。

#### Controller层

| Controller | 路径前缀 | 说明 | 核心API |
|------------|----------|------|---------|
| `DataQueryController` | `/data` | 数据查询 | `POST /query` (单次查询), `POST /batch-query` (批量查询), `POST /cache/clear`, `GET /cache/stats` |
| `CallRecordController` | `/call-record` | 调用记录 | `GET /list`, `GET /{id}`, `GET /stats` |

#### Service层

| Service | 说明 |
|---------|------|
| `DataQueryService` | 数据查询核心服务 (查询代理、缓存管理、统计) |
| `CallRecordService` | 调用记录管理 |
| `RateLimitService` | 限流服务 (基于Redis) |
| `VendorProxyService` | 厂商代理服务 (调用厂商适配器) |

#### VO层

| VO | 说明 |
|----|------|
| `ApiQueryReqVO` | API查询请求 (vendorCode, dataType, interfaceCode, params) |
| `BatchQueryReqVO` | 批量查询请求 |
| `DataQueryReqVO` | 数据查询请求 |
| `DataQueryRespVO` | 数据查询响应 |

#### 配置类

| 配置类 | 说明 |
|--------|------|
| `KafkaConfig` | Kafka配置 (调用记录异步写入) |
| `RedisConfig` | Redis配置 (限流、缓存) |
| `WebMvcConfig` | Web配置 (拦截器注册) |

#### Feign客户端

- `CallerFeignClient` — 调用 caller 服务验证 API Key

#### 启动类

`CallApplication`

---

### 3.7 计费管理 (data-platform-billing)

> **路径**: `data-platform-billing/`
> **端口**: 8084
> **职责**: 计费规则管理、日账单生成、预算告警、对账。

#### Controller层

| Controller | 路径前缀 | 说明 |
|------------|----------|------|
| `BillingController` | `/billing` | 计费管理 (账单查询、计费规则CRUD) |
| `BillingInternalController` | `/internal/billing` | 内部API (供其他服务调用) |

#### Service层

| Service | 说明 |
|---------|------|
| `BillingService` | 计费核心服务 (账单生成、费用计算) |
| `BudgetAlertService` | 预算告警服务 |
| `ReconciliationService` | 对账服务 |

#### 定时任务

| 任务 | 说明 |
|------|------|
| `BudgetScheduler` | 预算检查定时任务 |

#### Entity层 (billing-api)

| Entity | 对应表 | 说明 |
|--------|--------|------|
| `BillingDaily` | billing_daily | 日账单 |
| `BillingRule` | billing_rule | 计费规则 (type, unitPrice, tierConfig) |
| `BillingReconciliation` | - | 对账记录 |
| `TenantBudget` | - | 租户预算 |

#### 启动类

`BillingApplication`

---

### 3.8 监控告警 (data-platform-monitor)

> **路径**: `data-platform-monitor/`
> **端口**: 8085
> **职责**: 告警规则管理、告警记录管理。

#### Controller层

| Controller | 路径前缀 | 说明 |
|------------|----------|------|
| `AlertController` | `/alert` | 告警管理 (规则CRUD、记录查询、告警确认) |

#### Service层

| Service | 说明 |
|---------|------|
| `AlertService` | 告警服务 (规则管理、告警触发、记录查询) |

#### Entity层

| Entity | 对应表 | 说明 |
|--------|--------|------|
| `AlertRule` | alert_rule | 告警规则 (type, condition, threshold, notifyConfig) |
| `AlertRecord` | alert_record | 告警记录 (ruleId, status, triggerTime, resolveTime) |

#### 枚举

| 枚举 | 说明 |
|------|------|
| `AlertRuleType` | 告警规则类型 |

#### 启动类

`MonitorApplication`

---

### 3.9 用户权限管理 (data-platform-iam)

> **路径**: `data-platform-iam/`
> **端口**: 8093
> **职责**: 用户管理、角色管理、认证鉴权。合并了原 `data-platform-user` 和 `data-platform-role` 模块。

#### Controller层

| Controller | 路径前缀 | 说明 | 核心API |
|------------|----------|------|---------|
| `AuthController` | `/auth` | 认证管理 | `POST /login`, `POST /logout`, `GET /verify`, `GET /userinfo` |
| `UserController` | `/user` | 用户CRUD | `GET /list`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}`, `PATCH /{id}/status` |
| `RoleController` | `/role` | 角色CRUD | `GET /list`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}`, `PATCH /{id}/status` |

#### Service层

| Service | 说明 |
|---------|------|
| `UserService` | 用户管理 (CRUD、状态变更) |
| `RoleService` | 角色管理 (CRUD、权限分配) |

#### Entity层 (iam-api)

| Entity | 对应表 | 说明 |
|--------|--------|------|
| `User` | user_info | 用户 (username, password, email, phone, status) |
| `Role` | role_info | 角色 (roleCode, roleName, permissions, status) |
| `UserRole` | user_role | 用户角色关联 (userId, roleId) |

#### 认证机制

- 基于 **Sa-Token** 实现会话管理
- `AuthController.login()` → `UserContext.login(userId, username)` → `StpUtil.login(userId)`
- Token格式: Bearer UUID
- Token超时: 7200秒
- 通过 `UserContext` 工具类获取当前登录用户信息

#### 跨服务日志

IAM 服务通过 Feign 调用 Log 服务保存操作日志 (`RemoteOperationLogService` → `LogClient`)

#### 启动类

`IamApplication`

---

### 3.10 操作日志 (data-platform-log)

> **路径**: `data-platform-log/`
> **端口**: 8090
> **职责**: 操作日志存储与查询，提供跨服务日志保存能力。

#### 子模块

| 子模块 | 说明 |
|--------|------|
| `data-platform-log-api` | Feign客户端接口 + 远程日志服务实现，供其他服务引用 |
| `data-platform-log-service` | 日志存储与查询服务 |

#### log-api 关键类

| 类名 | 说明 |
|------|------|
| `LogClient` | Feign客户端接口，`@FeignClient(name = "data-platform-log-service")`，提供 `saveLog()` 方法 |
| `RemoteOperationLogService` | 实现 `OperationLogService` 接口，通过 Feign 远程保存日志 |
| `LogApiAutoConfiguration` | 自动配置类，注册 `RemoteOperationLogService` Bean |

#### log-service Controller层

| Controller | 路径前缀 | 说明 |
|------------|----------|------|
| `LogController` | `/log` | 日志查询 (分页、条件查询) |
| `InternalLogController` | `/log/internal` | 内部API (日志保存，供Feign调用) |

#### Entity层

| Entity | 对应表 | 说明 |
|--------|--------|------|
| `OperationLog` | operation_log | 操作日志 (module, operation, userId, username, ip, params, result, duration, status) |

#### 启动类

`LogApplication`

---

### 3.11 灰度发布 (data-platform-graylog)

> **路径**: `data-platform-graylog/`
> **端口**: 8092
> **职责**: 灰度规则管理，支持按调用方、租户等维度进行流量分配。

#### Controller层

| Controller | 路径前缀 | 说明 |
|------------|----------|------|
| `GraylogController` | `/graylog` | 灰度规则CRUD、状态管理 |

#### Service层

| Service | 说明 |
|---------|------|
| `GraylogService` | 灰度规则管理 |

#### Entity层

| Entity | 对应表 | 说明 |
|--------|--------|------|
| `GrayRule` | gray_rule | 灰度规则 (vendorCode, dataType, condition, percentage, status) |

#### 启动类

`GraylogApplication`

---

### 3.12 接口管理 (data-platform-interface)

> **路径**: `data-platform-interface/`
> **端口**: 8097
> **职责**: API接口定义管理，接口统计。

#### Controller层

| Controller | 路径前缀 | 说明 |
|------------|----------|------|
| `ApiInterfaceController` | `/interface` | 接口CRUD、接口统计 |
| `ApiInterfaceInternalController` | `/internal/interface` | 内部API (供其他服务调用) |

#### Service层

| Service | 说明 |
|---------|------|
| `ApiInterfaceService` | 接口管理服务 |

#### Entity层

| Entity | 对应表 | 说明 |
|--------|--------|------|
| `ApiInterface` | api_interface | 接口定义 (code, name, vendorCode, dataType, method, path, config) |
| `ApiInterfaceVO` | - | 接口视图对象 |

#### Mapper XML

- `ApiInterfaceMapper.xml` — 自定义SQL查询

#### 启动类

`InterfaceApplication`

---

### 3.13 租户管理 (data-platform-tenant)

> **路径**: `data-platform-tenant/`
> **端口**: 8086
> **职责**: 多租户信息管理、脱敏规则配置。

#### Controller层

| Controller | 路径前缀 | 说明 |
|------------|----------|------|
| `TenantController` | `/tenant` | 租户CRUD |

#### Entity层

| Entity | 对应表 | 说明 |
|--------|--------|------|
| `TenantInfo` | tenant_info | 租户信息 (name, code, status, config) |
| `MaskingRule` | - | 脱敏规则 |

#### 启动类

`TenantApplication`

---

### 3.14 SDK生成 (data-platform-sdk)

> **路径**: `data-platform-sdk/`
> **端口**: 8087
> **职责**: 根据接口定义自动生成SDK代码。

#### Controller层

| Controller | 路径前缀 | 说明 |
|------------|----------|------|
| `SDKController` | `/sdk` | SDK生成与下载 |

#### Service层

| Service | 说明 |
|---------|------|
| `SDKGeneratorService` | SDK代码生成服务 |

#### 启动类

`SDKApplication`

---

### 3.15 数据安全 (data-platform-security)

> **路径**: `data-platform-security/`
> **端口**: 8094
> **职责**: 数据加密、脱敏、字段级安全控制。

#### Controller层

| Controller | 路径前缀 | 说明 |
|------------|----------|------|
| `EncryptionController` | `/security` | 加密/解密管理 |

#### Service层

| Service | 说明 |
|---------|------|
| `EncryptionService` | 加密服务 (字段加密/解密) |

#### Entity层

| Entity | 对应表 | 说明 |
|--------|--------|------|
| `EncryptedField` | - | 加密字段定义 |

#### 启动类

`SecurityApplication`

---

### 3.16 数据血缘 (data-platform-trace)

> **路径**: `data-platform-trace/`
> **端口**: 8095
> **职责**: 数据血缘追踪，记录数据流转关系。

#### Controller层

| Controller | 路径前缀 | 说明 |
|------------|----------|------|
| `DataLineageController` | `/trace` | 血缘关系查询与管理 |

#### 启动类

`TraceApplication`

---

### 3.17 数据质量 (data-platform-quality)

> **路径**: `data-platform-quality/`
> **端口**: 8096
> **职责**: 数据质量规则管理、质量评分。

#### Controller层

| Controller | 路径前缀 | 说明 |
|------------|----------|------|
| `QualityController` | `/quality` | 质量规则CRUD、质量评分查询 |

#### Service层

| Service | 说明 |
|---------|------|
| `QualityService` | 质量管理服务 |

#### Entity层

| Entity | 对应表 | 说明 |
|--------|--------|------|
| `QualityRule` | - | 质量规则 |
| `QualityScore` | - | 质量评分 |

#### 启动类

`QualityApplication`

---

### 3.18 测试模块 (data-platform-test)

> **路径**: `data-platform-test/`
> **职责**: 集成测试，验证各模块API的正确性。

#### 测试类

| 测试类 | 说明 |
|--------|------|
| `BaseTest` | 基础测试类 (通用测试辅助方法) |
| `VendorApiTest` | 厂商管理API测试 (含数据类型、配置) |
| `IAMApiTest` | IAM API测试 (含用户、角色) |
| `CallerApiTest` | 调用方API测试 |
| `CallApiTest` | 调用服务API测试 |
| `BillingApiTest` | 计费API测试 |
| `BillingCalculatorTest` | 计费计算器单元测试 |
| `MonitorApiTest` | 监控告警API测试 |
| `LogApiTest` | 操作日志API测试 |
| `GraylogApiTest` | 灰度发布API测试 |
| `InterfaceApiTest` | 接口管理API测试 |
| `TenantApiTest` | 租户管理API测试 |
| `SdkApiTest` | SDK生成API测试 |
| `SecurityApiTest` | 数据安全API测试 |
| `TraceApiTest` | 数据血缘API测试 |
| `QualityApiTest` | 数据质量API测试 |
| `HttpVendorAdapterTest` | HTTP厂商适配器测试 |
| `SignatureBuilderTest` | 签名构建器测试 |

---

### 3.19 前端模块 (data-platform-web)

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
| ECharts | 5.5.0 | 图表可视化 |
| NProgress | 0.2.0 | 页面加载进度条 |
| Vite | 6.0.0 | 构建工具 |
| Sass | 1.99.0 | CSS预处理 |

#### 目录结构

```
data-platform-web/src/
├── api/                    # API调用层
│   ├── vendor.ts           # 厂商管理API
│   ├── caller.ts           # 调用方API
│   ├── call.ts             # 调用记录API
│   ├── billing.ts          # 计费API
│   ├── monitor.ts          # 监控API
│   ├── user.ts             # 用户API
│   ├── role.ts             # 角色API
│   ├── tenant.ts           # 租户API
│   ├── log.ts              # 日志API
│   ├── graylog.ts          # 灰度API
│   ├── interface.ts        # 接口API
│   ├── sdk.ts              # SDK API
│   ├── auth.ts             # 认证API (login)
│   ├── security.ts         # 安全API
│   ├── trace.ts            # 血缘API
│   ├── quality.ts          # 质量API
│   ├── config.ts           # 配置API
│   └── data-query.ts       # 数据查询API
├── views/                  # 页面组件
│   ├── login/              # 登录页
│   ├── dashboard/          # 数据概览
│   ├── layout/             # 布局框架
│   ├── vendor/             # 厂商管理
│   │   └── components/     # 厂商表单组件
│   ├── caller/             # 调用方管理
│   ├── datatype/           # 数据类型
│   ├── interface/          # 接口管理
│   │   └── components/     # 接口配置组件
│   │       ├── VendorInterfaceConfig.vue
│   │       ├── InterfaceForm.vue
│   │       ├── InterfaceStats.vue
│   │       ├── ApiConfigForm.vue
│   │       └── config/
│   │           ├── ParamsMappingEditor.vue
│   │           ├── HeaderEditor.vue
│   │           ├── RequestBodyEditor.vue
│   │           ├── SignConfig.vue
│   │           └── AuthConfig.vue
│   ├── call/               # 调用记录
│   ├── billing/            # 计费管理
│   ├── monitor/            # 监控告警
│   ├── config/             # 配置中心
│   ├── graylog/            # 灰度发布
│   ├── audit/              # 操作日志
│   ├── user/               # 用户管理
│   ├── role/               # 角色管理
│   ├── tenant/             # 租户管理
│   ├── sdk/                # SDK生成
│   ├── security/           # 数据安全
│   ├── trace/              # 数据血缘
│   ├── quality/            # 数据质量
│   ├── data-test/          # 数据查询测试
│   └── profile/            # 个人中心
├── components/             # 通用组件
│   ├── common/
│   │   ├── JsonEditor.vue  # JSON编辑器
│   │   └── KeyValueEditor.vue # 键值对编辑器
│   ├── StatCard.vue        # 统计卡片
│   ├── SearchBar.vue       # 搜索栏
│   └── PageHeader.vue      # 页面头部
├── stores/                 # Pinia状态管理
│   ├── index.ts
│   ├── user.ts             # 用户状态 (登录/登出/token)
│   └── cache.ts            # 缓存状态
├── utils/                  # 工具函数
│   ├── request.ts          # Axios封装 (拦截器、token注入)
│   ├── format.ts           # 格式化工具
│   ├── status.ts           # 状态工具
│   └── pagination.ts       # 分页工具
├── constants/              # 常量定义
│   ├── index.ts
│   ├── dataType.ts
│   └── status.ts
├── types/                  # TypeScript类型定义
│   └── index.ts
├── styles/                 # 全局样式
│   └── index.scss
├── router/                 # 路由配置
│   └── index.ts
├── App.vue                 # 根组件
└── main.ts                 # 入口文件
```

#### 路由配置

| 路径 | 页面 | 路由守卫 |
|------|------|----------|
| `/login` | 登录页 | 已登录则跳转 `/dashboard` |
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
| `/config` | 配置中心 | 需登录 |
| `/graylog` | 灰度发布 | 需登录 |
| `/audit` | 操作日志 | 需登录 |
| `/data-test` | 数据查询测试 | 需登录 |
| `/profile` | 个人中心 | 需登录 |
| `/:pathMatch(.*)*` | 404 页面 | 无需登录 |

---

## 4. 依赖关系

### 4.1 模块间依赖图

```
                    ┌─────────────────┐
                    │  data-platform- │
                    │     common      │
                    └────────┬────────┘
                             │ 被所有服务模块依赖
              ┌──────────────┼──────────────┐
              │              │              │
    ┌─────────▼──────┐ ┌────▼───────┐ ┌────▼────────┐
    │ data-platform- │ │data-platform│ │data-platform│
    │     api        │ │   -gateway  │ │   -test     │
    └────────────────┘ └────────────┘ └─────────────┘

    ┌──────────────────────────────────────────────────────┐
    │              业务服务模块 (均依赖 common)              │
    ├──────────────┬───────────────┬───────────────────────┤
    │   vendor     │    caller     │      billing          │
    │   (8081)     │    (8082)     │      (8084)           │
    ├──────────────┼───────────────┼───────────────────────┤
    │    call      │    monitor    │       iam             │
    │   (8083)     │    (8085)     │      (8093)           │
    ├──────────────┼───────────────┼───────────────────────┤
    │   tenant     │     sdk       │       log             │
    │   (8086)     │    (8087)     │      (8090)           │
    ├──────────────┼───────────────┼───────────────────────┤
    │   graylog    │   security    │      trace            │
    │   (8092)     │    (8094)     │      (8095)           │
    ├──────────────┼───────────────┼───────────────────────┤
    │   quality    │  interface    │                       │
    │   (8096)     │    (8097)     │                       │
    └──────────────┴───────────────┴───────────────────────┘
```

### 4.2 服务间调用关系 (Feign)

| 调用方 | 被调用方 | Feign接口 | 说明 |
|--------|----------|-----------|------|
| call (8083) | caller (8082) | `CallerFeignClient` | 验证API Key有效性 |
| call (8083) | vendor (8081) | `VendorConfigInternalController` | 获取厂商API配置 |
| call (8083) | interface (8097) | `ApiInterfaceInternalController` | 获取接口定义 |
| iam (8093) | log (8090) | `LogClient` | 跨服务保存操作日志 |
| billing (8084) | call (8083) | - | 获取调用记录用于计费 |

### 4.3 第三方依赖总览

| 依赖 | 版本 | 用途 | 使用模块 |
|------|------|------|----------|
| Spring Boot | 3.4.0 | 应用框架 | 所有后端模块 |
| Spring Cloud | 2024.0.0 | 微服务治理 | 所有后端模块 |
| Spring Cloud Alibaba Nacos | 2023.0.1.0 | 服务发现/配置 | 所有后端模块 |
| MyBatis-Plus | 3.5.7 | ORM | common + 所有业务模块 |
| Sa-Token | 1.37.0 | 认证鉴权 | gateway + iam + 需认证模块 |
| Redisson | 3.27.0 | Redis客户端 | gateway + call + 需缓存模块 |
| Spring Kafka | 3.2.0 | 消息队列 | call |
| Resilience4j | - | 熔断重试 | common |
| OkHttp | - | HTTP客户端 | common (HttpVendorAdapter) |
| Hutool | 5.8.28 | 工具库 | common (SignatureBuilder等) |
| Lombok | 1.18.36 | 代码简化 | 所有后端模块 |
| OpenFeign | - | 服务间调用 | call, iam, billing |

---

## 5. 数据库设计

### 5.1 数据库信息

- **数据库**: PostgreSQL 16
- **地址**: localhost:5432
- **每个服务独立数据库**，按服务名区分

### 5.2 数据表总览

| 序号 | 表名 | 说明 | 所属模块 |
|------|------|------|----------|
| 1 | tenant_info | 租户信息 | tenant |
| 2 | vendor_info | 厂商信息 | vendor |
| 3 | data_type | 数据类型 | vendor |
| 4 | vendor_config | 厂商API配置 | vendor |
| 5 | vendor_config_extended | 厂商扩展配置 | vendor |
| 6 | config_version | 配置版本历史 | vendor |
| 7 | caller_info | 调用方信息 | caller |
| 8 | api_key | API Key | caller |
| 9 | api_interface | 接口定义 | interface |
| 10 | interface_param | 接口参数定义 | interface |
| 11 | call_record | 调用记录 (按月分区) | call |
| 11 | billing_daily | 日账单 | billing |
| 12 | billing_rule | 计费规则 | billing |
| 13 | user_info | 用户 | iam |
| 14 | role_info | 角色 | iam |
| 15 | user_role | 用户角色关联 | iam |
| 16 | alert_rule | 告警规则 | monitor |
| 17 | alert_record | 告警记录 | monitor |
| 18 | circuit_breaker | 熔断记录 | common |
| 19 | operation_log | 操作日志 | log |
| 20 | gray_rule | 灰度规则 | graylog |

---

## 6. 项目运行方式

### 6.1 环境要求

| 依赖 | 版本 | 说明 |
|------|------|------|
| JDK | 21 | Java运行环境 |
| Maven | 3.9+ | 构建工具 |
| Node.js | 18+ | 前端构建 |
| PostgreSQL | 16 | 数据库 |
| Docker | 20+ | 中间件容器 |

### 6.2 基础设施启动

```bash
# 启动 Redis、Kafka、Nacos、Prometheus、Grafana、Elasticsearch、Kibana
docker-compose up -d
```

**Docker Compose 服务清单**:

| 服务 | 端口 | 说明 |
|------|------|------|
| Redis | 6379 | 缓存 (密码: redis_password) |
| Kafka | 9092 | 消息队列 |
| Nacos | 8848, 9848 | 服务发现与配置中心 |
| Prometheus | 9090 | 监控指标采集 |
| Grafana | 3100 | 监控可视化 (admin/admin123) |
| Elasticsearch | 9200, 9300 | 日志存储 |
| Kibana | 5601 | 日志可视化 |
| Filebeat | - | 日志收集 |

### 6.3 数据库初始化

确保 PostgreSQL 16 运行在 localhost:5432，各服务启动时会通过 MyBatis-Plus 自动建表。

### 6.4 后端服务启动

**方式一: 一键启动脚本 (推荐)**

```bash
# 启动所有服务 (按依赖顺序启动，Gateway最后)
./start-services.sh

# 停止所有服务
./stop-services.sh

# 停止并清理日志
./stop-services.sh --clean-logs
```

启动脚本会：
1. 先停止可能存在的旧服务
2. 按顺序启动14个业务服务 + Gateway
3. 等待45秒后检查各端口状态
4. 输出启动状态报告

**方式二: 单独启动**

```bash
# 启动单个服务
cd data-platform-vendor
mvn spring-boot:run

# 指定环境
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**服务启动顺序**:

```
8081 → 8082 → 8083 → 8084 → 8085 → 8086 → 8087 → 8090 → 8092 → 8093 → 8094 → 8095 → 8096 → 8097 → 8888
vendor  caller  call    billing monitor tenant sdk    log     graylog  iam    security trace  quality interface gateway
```

### 6.5 前端启动

```bash
cd data-platform-web

# 安装依赖
npm install

# 开发模式启动
npm run dev

# 生产构建
npm run build
```

### 6.6 服务端口总览

| 服务 | 端口 | 说明 |
|------|------|------|
| Gateway | 8888 | API网关 (统一入口) |
| vendor | 8081 | 厂商管理 |
| caller | 8082 | 调用方管理 |
| call | 8083 | 调用服务 |
| billing | 8084 | 计费管理 |
| monitor | 8085 | 监控告警 |
| tenant | 8086 | 租户管理 |
| sdk | 8087 | SDK生成 |
| log | 8090 | 操作日志 |
| graylog | 8092 | 灰度发布 |
| iam | 8093 | 用户权限管理 |
| security | 8094 | 数据安全 |
| trace | 8095 | 数据血缘 |
| quality | 8096 | 数据质量 |
| interface | 8097 | 接口管理 |
| 前端 | 3000 | Vue3前端 |

### 6.7 环境配置

项目支持 `dev` 和 `prod` 两个环境 profile，通过 Maven profile 切换：

- **dev** (默认): 本地开发配置，直连本地 PostgreSQL/Redis/Nacos
- **prod**: 生产环境配置

每个服务模块均有 `application.yml` + `application-dev.yml` + `application-prod.yml` 三层配置。

---

## 7. 设计模式与核心机制

### 7.1 设计模式汇总

| 模式 | 应用位置 | 说明 |
|------|----------|------|
| **策略模式** | `VendorAdapter` / `AuthHandler` / `BillingCalculator` | 不同厂商/认证/计费策略可互换 |
| **工厂模式** | `VendorAdapterFactory` / `AuthHandlerFactory` / `BillingCalculatorFactory` | 根据类型创建对应策略实例 |
| **模板方法模式** | `AbstractVendorAdapter` | 定义通用的请求/响应转换流程，子类实现具体执行逻辑 |
| **AOP模式** | `OperationLogAspect` | 通过注解声明式记录操作日志 |
| **观察者模式** | Kafka消息 (调用记录异步写入) | 解耦调用处理与记录存储 |
| **代理模式** | `VendorProxyService` | 代理调用方请求到厂商API |
| **自动配置模式** | Spring Boot AutoConfiguration | common模块通过 `AutoConfiguration.imports` 自动注册Bean |

### 7.2 核心数据流

**数据查询主流程**:

```
调用方请求
    │
    ▼
DataQueryController (验证API Key + 限流检查)
    │
    ▼
DataQueryService (查询缓存 → 未命中则代理)
    │
    ▼
VendorProxyService (获取厂商配置 + 接口定义)
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
返回结果 + 异步写入调用记录 (Kafka) + 计费 (异步)
```

### 7.3 认证鉴权流程

```
前端请求 → Gateway → Sa-Token 校验 Bearer Token
                        │
                        ├── 有效 → 路由到目标服务
                        └── 无效 → 返回 401

各服务 AuthInterceptor → 二次校验 Authorization Header
                        │
                        ├── 排除路径 (/auth/login, /auth/verify, /actuator, /health) → 放行
                        └── 其他路径 → 校验 Bearer Token 格式与有效性
```

### 7.4 跨服务操作日志机制

```
IAM/其他服务 Controller 方法
    │
    ▼
@OperationLog 注解
    │
    ▼
OperationLogAspect (AOP拦截)
    │ 收集: module, operation, userId, username, ip, params, result, duration
    │
    ▼
OperationLogService (接口)
    │
    ├── 本地实现 → 直接保存到本地数据库
    │
    └── RemoteOperationLogService (通过 log-api 自动配置)
            │
            ▼
        LogClient (Feign)
            │
            ▼
        InternalLogController (/log/internal/save)
            │
            ▼
        LogService → OperationLogMapper → operation_log 表
```

### 7.5 Maven模块规范

业务模块采用 **api + service** 双模块结构：

- **`{module}-api`**: 定义实体类 (Entity/DTO)，供其他模块 Feign 引用
- **`{module}-service`**: 业务实现 (Controller/Service/Mapper/Config)

例外：`data-platform-sdk`、`data-platform-security` 等较简单模块采用单模块结构。

---

> **文档维护说明**: 本文档基于项目代码自动分析生成，如项目结构发生变更，请同步更新此文档。
