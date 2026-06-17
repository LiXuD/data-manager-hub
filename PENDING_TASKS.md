# 数据管理平台 - 待完成功能与问题清单

**创建日期**: 2026-04-26
**最后更新**: 2026-06-17
**状态**: V2.0 全部完成（SkyWalking 桥接、SDK 多语言生成、灰度厂商路由），已合入 dev

---

## 🏗️ 五域收敛架构 (2026-05-16 最终版)

### 架构概览

```
                          ┌──────────────┐
                          │   前端 (Vue3) │ :3000
                          └──────┬───────┘
                                 │
                          ┌──────▼───────┐
                          │   Gateway    │ :8888
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
   ┌────▼──────────┐  ┌──────────▼──────────┐  ┌──────────▼──────────┐
   │   Identity     │  │      Governance     │  │                      │
   │    (8086)      │  │       (8085)        │  │                      │
   ├───────────────┤  ├────────────────────┤  │                      │
   │ 用户管理        │  │ 监控告警            │  │                      │
   │ 角色权限        │  │ 操作日志           │  │                      │
   │ 租户管理        │  │ 数据质量           │  │                      │
   │ 数据加密        │  │ 数据血缘           │  │                      │
   └────────────────┘  └────────────────────┘  └──────────────────────┘
```

### 域职责划分

| 域 | 模块 | 端口 | 职责 |
|----|------|------|------|
| **masterdata** | `data-platform-masterdata-api/service` | 8081 | 厂商管理、数据类型、接口定义、厂商配置、灰度规则 |
| **access** | `data-platform-access-api/service` | 8082 | 调用方管理、API Key、数据调用、调用记录 |
| **billing** | `data-platform-billing-api/service` | 8084 | 计费规则、账单、结算/对账、预算告警 |
| **identity** | `data-platform-identity-api/service` | 8086 | 租户管理、用户管理、角色权限、数据加密/脱敏 |
| **governance** | `data-platform-governance-api/service` | 8085 | 监控告警、操作日志、数据质量、数据血缘 |

### 模块结构

```
data-platform-masterdata/           # 厂商/数据类型/接口/灰度
├── data-platform-masterdata-api/    # Feign接口、DTO/VO
└── data-platform-masterdata-service/ # Controller/Service/Mapper
    └── src/main/java/com/dataplatform/masterdata/
        ├── MasterdataApplication.java
        ├── controller/             # 控制器
        │   ├── vendor/             # 厂商相关
        │   ├── interface_/         # 接口相关
        │   └── graylog/            # 灰度相关
        ├── service/               # 业务服务
        ├── mapper/                # 数据访问
        └── entity/                 # 实体类

data-platform-access/               # 调用方/调用
├── data-platform-access-api/
└── data-platform-access-service/
    └── src/main/java/com/dataplatform/access/
        ├── AccessApplication.java
        ├── caller/                # 调用方/API Key
        └── call/                   # 调用记录/数据查询

data-platform-billing/              # 计费
├── data-platform-billing-api/
└── data-platform-billing-service/

data-platform-identity/             # 身份/租户/安全
├── data-platform-identity-api/
└── data-platform-identity-service/
    └── src/main/java/com/dataplatform/identity/
        ├── IdentityApplication.java
        ├── iam/                   # 用户/角色/权限
        ├── tenant/                 # 租户/脱敏规则
        └── security/              # 数据加密

data-platform-governance/            # 治理
├── data-platform-governance-api/
│   └── src/main/java/com/dataplatform/governance/
│       ├── api/                   # Feign接口
│       └── log/api/               # 日志远程服务
└── data-platform-governance-service/
    └── src/main/java/com/dataplatform/governance/
        ├── GovernanceApplication.java
        ├── log/                    # 操作日志
        ├── monitor/               # 监控告警
        ├── quality/                # 数据质量
        └── trace/                 # 数据血缘
```

### 公共模块

| 模块 | 职责 |
|------|------|
| `data-platform-common-contract` | API契约 (Result, PageResult, ErrorCode, 枚举) |
| `data-platform-common-web` | Web层 (拦截器、日志AOP、工具类) |
| `data-platform-common-persistence` | 持久层 (Entity基类、MyBatis-Plus配置) |
| `data-platform-common-runtime` | 运行时 (适配器、认证、计费、熔断、映射) |

### 公共模块详解

#### data-platform-common-contract

**路径**: `data-platform-common-contract/src/main/java/com/dataplatform/`

| 路径 | 类名 | 说明 |
|------|------|------|
| `api/` | `Result<T>` | 统一返回结果 |
| `api/` | `PageResult<T>` | 分页结果 |
| `api/exception/` | `BusinessException` | 业务异常 |
| `api/exception/` | `ErrorCode` | 错误码枚举 |
| `common/enums/` | 各种枚举类 | 状态枚举 |

#### data-platform-common-runtime

**路径**: `data-platform-common-runtime/src/main/java/com/dataplatform/common/`

| 路径 | 类名 | 说明 |
|------|------|------|
| `adapter/` | `VendorAdapter` | 厂商适配器接口 |
| `adapter/` | `AbstractVendorAdapter` | 适配器抽象基类 |
| `adapter/` | `HttpVendorAdapter` | HTTP厂商适配器 |
| `adapter/` | `VendorAdapterFactory` | 适配器工厂 |
| `auth/` | `AuthHandler` | 认证处理器接口 |
| `auth/` | `NoneAuthHandler` | 无认证 |
| `auth/` | `BasicAuthHandler` | Basic认证 |
| `auth/` | `BearerAuthHandler` | Bearer Token认证 |
| `auth/` | `ApiKeyAuthHandler` | API Key认证 |
| `auth/` | `AuthHandlerFactory` | 认证处理器工厂 |
| `billing/` | `BillingCalculator` | 计费计算器接口 |
| `billing/` | `StandardBillingCalculator` | 标准计费 |
| `billing/` | `TieredBillingCalculator` | 阶梯计费 |
| `billing/` | `DynamicBillingCalculator` | 动态计费 |
| `circuitbreaker/` | `CircuitBreakerManager` | 熔断器管理 |
| `mapping/` | `RequestMappingProcessor` | 请求映射处理 |
| `mapping/` | `ResponseMappingProcessor` | 响应映射处理 |
| `security/` | `SignatureBuilder` | 签名构建器 |
| `util/` | `DataMaskingUtil` | 数据脱敏工具 |
| `util/` | `VariableSubstitutionUtil` | 变量替换工具 |

#### data-platform-common-web

**路径**: `data-platform-common-web/src/main/java/com/dataplatform/common/`

| 路径 | 类名 | 说明 |
|------|------|------|
| `interceptor/` | `AuthInterceptor` | 认证拦截器 |
| `log/` | `OperationLog` | 操作日志注解 |
| `log/` | `OperationLogAspect` | 操作日志切面 |
| `util/` | `IpUtil` | IP地址工具 |
| `util/` | `UserContext` | 用户上下文工具 |

#### data-platform-common-persistence

**路径**: `data-platform-common-persistence/src/main/java/com/dataplatform/common/`

| 路径 | 类名 | 说明 |
|------|------|------|
| `config/` | `MybatisPlusConfig` | MyBatis-Plus配置 |
| `entity/` | `CallRecord` | 调用记录基类 |
| `entity/` | `DataType` | 数据类型基类 |
| `entity/` | `BillingRuleDO` | 计费规则基类 |
| `entity/` | `VendorConfigDO` | 厂商配置基类 |
| `handler/` | `CodeEnumTypeHandler` | 枚举类型处理器 |
| `handler/` | `JsonbTypeHandler` | JSONB类型处理器 |

---

## 🔄 架构演进历史

### 2026-05-16 五域收敛

**变更**: 从 13 个独立小服务合并为 5 个业务域

| 原模块 | 合并到 | 域 |
|--------|--------|---|
| data-platform-vendor | masterdata | masterdata |
| data-platform-interface | masterdata | masterdata |
| data-platform-graylog | masterdata | masterdata |
| data-platform-caller | access | access |
| data-platform-call | access | access |
| data-platform-billing | billing | billing |
| data-platform-tenant | identity | identity |
| data-platform-iam | identity | identity |
| data-platform-security | identity | identity |
| data-platform-monitor | governance | governance |
| data-platform-log | governance | governance |
| data-platform-quality | governance | governance |
| data-platform-trace | governance | governance |

**提交记录**: 五域收敛基线合入 dev

---

## 📊 服务端口总览

| 域 | 服务名 | 端口 | 说明 |
|----|--------|------|------|
| - | data-platform-gateway | 8888 | API网关 |
| masterdata | data-platform-masterdata | 8081 | 厂商/接口/灰度 |
| access | data-platform-access | 8082 | 调用方/调用 |
| billing | data-platform-billing | 8084 | 计费 |
| identity | data-platform-identity | 8086 | 身份/租户 |
| governance | data-platform-governance | 8085 | 监控/日志/质量 |
| - | data-platform-sdk | 8087 | SDK生成 |
| - | data-platform-web | 3000 | 前端 |

---

## 🔧 依赖关系

### 服务间调用 (Feign)

| 调用方 | 被调用方 | 接口 | 说明 |
|--------|----------|------|------|
| access | masterdata | `MasterdataFeignClient` | 获取厂商配置、接口定义 |
| access | identity | `IdentityFeignClient` | 验证API Key |
| billing | access | `CallContractController` | 获取调用记录 |
| governance | - | `GovernanceFeignClient` | 数据质量/血缘 |

### 依赖规则

- **service → api → common-contract**
- 禁止循环依赖
- 域间调用只能通过 `api` 模块的 Feign 契约

---

## 🏗️ 五域收敛后续推进任务 (2026-05-16 规划)

> 来源: `docs/2026-05-16-five-domain-next-phase-plan.md`
> 执行顺序按阶段 A→F，同一阶段内任务可并行。
> **更新**: 2026-05-17 所有阶段任务已完成。

### 阶段 A: 基线守护与协作规则

- [x] **架构扫描测试** — `arch-scan.sh`，5 项检查（跨域依赖、api 重型依赖、全包扫描、跨域 import、旧模块残留）✅
- [x] **合并前检查清单** — 已写入 README.md「合并前检查」章节 ✅

### 阶段 B: 启动烟雾测试

- [x] **五域服务启动验证** — 6/6 端口监听正常，服务响应正常，无 Bean/Mapper 冲突 ✅
- [x] **Gateway 路由映射梳理** — 12 条路由全部可达，发现 1 处 data-type/datatype 路径不匹配 ✅

### 阶段 B+: Gateway 熔断补充

- [x] **CircuitBreakerFilter** — `filter/CircuitBreakerFilter.java`，4 个测试通过 ✅

### 阶段 C: 业务链路回归

- [x] **P1 核心链路测试** — `smoke-test-api.sh`，覆盖主数据/访问/计费三条核心链路 ✅
- [x] **P2 链路测试** — `smoke-test-api.sh` 补充身份租户、治理告警/日志/质量/血缘观测 ✅

### 阶段 D: 前端接口归并

- [x] **前端 API 类型整理** — 发现 8 处重复类型、CallRecord 字段冲突、多数 API 函数未类型化 ✅

### 阶段 E: 文档清理

- [x] **DEPLOYMENT.md 更新** — 去掉 sdk 服务行，补充发布顺序 ✅
- [x] **CODE_WIKI.md 重写** — 架构图去掉 sdk、sdk 标注为 Jar、端口表去掉 sdk ✅

### 阶段 F: 部署路径

- [x] **docker-compose 更新** — 添加五域服务说明注释 ✅

---

## 📋 待完成功能 (P1)

### 1. Gateway 增强

- [x] 限流过滤器 (RateLimitFilter) — `filter/RateLimitFilter.java` ✅
- [x] 认证过滤器增强 (AuthFilter) — `filter/AuthFilter.java` ✅
- [x] 请求日志过滤器 (RequestLogFilter) — `filter/RequestLogFilter.java` ✅
- [x] 链路追踪过滤器 (TraceIdFilter) — `filter/TraceIdFilter.java` ✅（额外实现）
- [x] 熔断过滤器 (CircuitBreakerFilter) — `filter/CircuitBreakerFilter.java` ✅
- [x] 外部系统统一入口增强 — 产品/场景归因、API Key 产品授权、历史缓存命中、多维统计；提交前补齐 fresh DB DDL、场景字典网关路由、维度分组统计 ✅

### 2. 自动对账完善

- [x] 厂商账单获取 (CSV 文件导入) — `/billing/reconciliation/import` ✅
- [x] 差异比对逻辑 — `/billing/reconciliation/run|list|diffs`，按平台调用记录与厂商账单的调用量/金额双维度比对 ✅
- [x] 差异告警通知 — `diff_warning/diff_error` 通过 governance-api 写入告警记录；系统规则补齐必填阈值并避免重复状态告警 ✅

### 3. 前端完善

- [x] 三级联动: 厂商 → 数据类型 → 接口 — 后端 `/interface/options` + 前端 data-test/interface 筛选联动 ✅
- [x] 统一调用入口前端补齐 — 调用方产品配置、API Key 产品授权、场景字典、OpenAPI 查询测试、调用记录产品/场景/缓存筛选 ✅

### 4. 五域收敛后修复 (2026-05-17)

- [x] Gateway 路由 `/api/v1/data-type/**` 移除 — 与 Controller `/datatype` 不匹配，删除死路由 ✅
- [x] Gateway actuator/health 放行 — `sa-token.check-exclude-paths` 添加 `/actuator/**`、`/health/**` ✅
- [x] 前端 CallRecord 类型统一 — 三处定义合并为 `types/index.ts` 单一来源 ✅
- [x] 前端 API 函数泛型补充 — 14 个 API 文件添加类型参数 (call/quality/graylog/billing/config/datatype/log/permission/role/security/trace/user/caller/vendor-config) ✅

---

## 📋 后续迭代 (P2)

- [x] 配置热更新 (Nacos) — Gateway `CircuitBreakerFilter`、`RateLimitFilter` 与 masterdata 厂商配置缓存 TTL 接入 `@RefreshScope`/配置项热更新样例 ✅
- [x] Kafka 异步化 — OpenAPI 调用记录写入 Kafka `call-record`，access 消费端落库，billing 消费端按 `request_id` 幂等聚合 `billing_daily`，持久化失败交由 Kafka 重试 ✅
- [x] Prometheus 指标暴露 — Gateway 与五域服务统一暴露 `/actuator/prometheus` ✅

---

## 📋 V2.0 规划

- [x] 数据溯源 (SkyWalking) ✅
  - 第一阶段: governance trace 血缘实体/DDL/API 对齐，JSON body 写入与上下游查询；OpenAPI 调用记录已保存 `X-Trace-Id`
  - 第二阶段: SkyWalking OAP+UI docker-compose 编排、Java Agent 下载/启动脚本、Feign 拦截器 `TraceFeignRequestInterceptor` 跨域传播 `X-Trace-Id`、`TraceIdMdcFilter` 写入 SLF4J MDC、6 服务日志 pattern 注入 `%X{traceId}`
  - 第三阶段 (2026-05-27): `TraceContextBridge` 桥接工具类（反射检测 SkyWalking 可用性，零硬依赖），`TraceIdMdcFilter` 注入 SkyWalking 原生 trace ID 到 MDC，`verify-trace.sh` 验证脚本，`apm-toolkit-trace:9.4.0` provided 依赖，`.gitignore` 排除 agent 目录
- [x] SDK 多语言生成 ✅
  - Freemarker 模板引擎驱动，支持 Java/Python/Go 三语言
  - `ApiSpec` 模型 + `ApiSpec.fromDefaults()` 硬编码 14 个端点
  - `SDKCli` 纯 Java CLI 入口（`--lang`、`--base-url`、`--output`）
  - 6 个 Freemarker 模板：client + model 各 3 语言
  - 向后兼容旧 `generateJavaSDK(baseUrl)` API
  - 13 个单元测试通过
- [x] 灰度发布增强 ✅
  - `GrayVendorResolver` 核心组件：内置 30s TTL 缓存、条件评估（random/header/caller/ip）、权重随机选择
  - `GraylogService.getActiveRule()` 增强：加入 startTime/endTime 时间窗口校验
  - `OpenApiQueryController.resolveApiRoute()` 接入灰度路由：2+ 个 active 配置时按规则选择厂商
  - 静默降级：Feign 异常时回落稳定厂商，无用户感知
  - 14 个单元测试覆盖全场景

---

## 📋 上线前审查与修复 (2026-06-16)

- [x] 生产 profile 安全化 — 五域服务与 Gateway 的 `application-prod.yml` 去除 `localhost`、默认数据库密码、默认 Redis 密码等生产默认值；新增 masterdata 生产配置 ✅
- [x] Trace 传播修复 — `TraceIdMdcFilter` 在请求缺失 `X-Trace-Id` 时生成 traceId；`TraceFeignRequestInterceptor` 支持从 MDC/SkyWalking 桥接上下文兜底传播 ✅
- [x] 默认测试门禁修复 — 外部 API 集成测试默认跳过，需设置 `-Dintegration.tests=true` 或 `INTEGRATION_TESTS=true` 才执行，避免未启动服务时 `mvn test` 假失败 ✅
- [x] 厂商适配器回归修复 — `AbstractVendorAdapter.transformRequest` 恢复兼容旧 JSON 字段映射格式，同时保留 `requestMapping` 结构化格式 ✅
- [x] 本地基础设施闭环 — `docker-compose.yml` 补齐 PostgreSQL，修复 Grafana 端口映射，补交 `prometheus/prometheus.yml`，并标注 compose 仅用于本地/测试 ✅
- [x] SkyWalking Agent 安装修复 — `setup-agent.sh` 改为从临时目录定位真实 `skywalking-agent.jar` 后复制到 `skywalking/agent` ✅
- [x] 部署文档更新 — 统一 `DB_USERNAME` 变量、修正 SDK CLI 主类、区分本地 compose 与生产高可用基础设施 ✅
- [x] 运行态启动阻断修复 — 删除 masterdata/access 域内重复 `WebMvcConfig`，补齐 common-runtime 熔断自动配置，dev profile 改为环境变量占位并对齐 compose ✅
- [x] 可执行 Jar 打包修复 — 五域 service 与 Gateway 统一输出 Spring Boot 可执行主 Jar，避免部署产物缺少 `BOOT-INF` ✅
- [x] 前端资源体积优化 — 去除 Element Plus 整包入口注册，Dashboard 改用 ECharts 按需模块，拆分 Vue/axios 公共 chunk，消除 Vite 大 chunk 警告 ✅

验证结果:
- [x] `bash arch-scan.sh` 通过
- [x] `mvn -q validate` 通过
- [x] `mvn -q -DskipTests compile` 通过
- [x] `mvn -q -DskipTests test-compile` 通过
- [x] `mvn -q test` 通过
- [x] `npm run build` 通过，无 Vite 大 chunk 警告；最大 chunk 480.75 KiB
- [x] `mvn -q -DskipTests clean package` 通过，五域 service 与 Gateway 主 Jar 均包含 `BOOT-INF`
- [x] `docker compose config` 通过
- [x] compose PostgreSQL 备用端口启动与 `sql/init.sql` 初始化通过
- [x] masterdata/access/billing/identity/governance/gateway 六服务本地启动健康检查全部 `UP`

注意事项:
- `docker-compose.yml` 仍保留本地/测试默认密码，不作为生产模板；生产环境必须使用外部密钥或环境变量覆盖。
- `mvn test` 默认跳过真实外部 API 集成测试；上线前真实环境验收需显式开启 `-Dintegration.tests=true` 或 `INTEGRATION_TESTS=true`。

---

**文档维护**: 按架构变更更新
**最后更新**: 2026-06-17
