# Gateway 增强设计文档

**日期**: 2026-05-07
**状态**: 设计完成，待用户确认
**关联任务**: PENDING_TASKS.md #2.1 Gateway增强 (P1)

---

## 1. 背景

当前数据管理平台的网关模块（`data-platform-gateway`）仅具备基本路由转发功能，缺少对调用方（外部系统）的统一认证、限流、日志记录和链路追踪能力。按照设计文档的架构规划，内部系统应通过 API 网关统一接入数据平台，网关需要承担认证鉴权、流量控制、请求日志等职责。

### 当前网关现状

- 13 条路由规则，统一使用 `StripPrefix=2` 转发到后端服务
- 已配置 Sa-Token + Redis（用于管理后台认证）
- 已配置 CORS 全局跨域
- **没有任何自定义 GlobalFilter**

### 目标

为调用方 OpenAPI 请求提供统一的网关层增强能力，分两批实施：

| 批次 | 内容 | 状态 |
|------|------|------|
| 第一批（本次） | 认证过滤 + 限流 + 请求日志 + TraceId | 设计中 |
| 第二批（后续） | 网关层熔断 | 待规划 |

---

## 2. 路径划分

网关通过路径前缀区分两套认证体系：

| 路径前缀 | 认证方式 | 说明 |
|----------|----------|------|
| `/api/v1/**` | Sa-Token（现有） | 管理后台请求，保持不变 |
| `/openapi/**` | API Key（新增） | 调用方统一入口，本次新建 |

---

## 3. 总体架构

使用 **独立 GlobalFilter** 模式（方案A），每个能力由一个 GlobalFilter 实现，通过 `@Order` 控制执行顺序：

```
Request → TraceIdFilter(@Order -3) → AuthFilter(@Order -2) → RateLimitFilter(@Order -1) → RequestLogFilter(@Order 0) → Route → 后端服务
```

所有 Filter 放在 `com.dataplatform.gateway.filter` 包下。

---

## 4. 各 Filter 详细设计

### 4.1 TraceIdFilter

| 项目 | 内容 |
|------|------|
| **Order** | -3（最先执行） |
| **拦截范围** | 全部请求 |
| **职责** | 为每个请求生成或透传 TraceId |

**逻辑**：
1. 检查请求头 `X-Trace-Id`，有则沿用（支持调用方传入）
2. 没有则用 `UUID.randomUUID()` 生成
3. 写入 `ServerWebExchange.attributes`
4. 通过 `X-Trace-Id` 请求头透传给下游服务

**代码量预估**：~30 行

---

### 4.2 AuthFilter

| 项目 | 内容 |
|------|------|
| **Order** | -2（TraceId 之后） |
| **拦截范围** | 仅 `/openapi/**`，`/api/v1/**` 跳过 |
| **职责** | API Key 认证校验 |

**API Key 提取**：优先 `X-Api-Key` 请求头，其次 `Authorization: Bearer <key>`

**校验流程**：
```
1. 检查路径是否 /openapi/** → 否 → 直接放行
2. 提取 API Key → 不存在 → 返回 401
3. 查 Redis: openapi:key:{key_value} → 不存在 → 返回 401
4. 检查 key/调用方状态是否启用 → 禁用 → 返回 403
5. 将 callerId、keyId 写入 exchange attributes → 放行
```

**Redis 缓存结构**：
```
Key:   openapi:key:{key_value}
Value: {"callerId": 1, "keyId": 10, "callerName": "风控系统", "status": 1}
```

缓存由 caller-service 在创建/更新/删除 API Key 时写入，网关只读。

**错误响应格式**（统一项目 `Result` 格式）：
```json
{"code": 401, "message": "API Key 无效或已过期", "data": null}
```

**代码量预估**：~60 行

---

### 4.3 RateLimitFilter

| 项目 | 内容 |
|------|------|
| **Order** | -1（Auth 之后，日志之前） |
| **拦截范围** | 仅 `/openapi/**` |
| **职责** | 按 API Key 限流 |

**限流维度**：按 API Key 独立配额
**算法**：滑动窗口（Redis ZSET），窗口大小 1 分钟

**限流流程**：
```
1. 检查路径 → 非 /openapi/** → 放行
2. 从 exchange attributes 获取 keyId（AuthFilter 已写入）
3. 读 Redis: openapi:rate_limit:{keyId} → {"windowSec": 60, "maxReqs": 100}
   无自定义配置 → 默认值 60s/100次
4. 执行 Redis Lua 脚本（原子化 ZADD + ZREMRANGEBYSCORE + ZCARD）
5. count > maxReqs → 返回 429 + Retry-After 头
6. count ≤ maxReqs → 放行
```

**为什么选滑动窗口**：固定窗口存在边界突发问题（00:59 秒 100 次 + 01:00 秒 100 次 = 实际 2 秒内 200 次），滑动窗口始终统计过去 N 秒内请求数。

**错误响应**：
```json
{"code": 429, "message": "请求过于频繁，请稍后再试", "data": {"retryAfter": 30}}
```
响应头：`Retry-After: 30`

**代码量预估**：~50 行 + 一个 Redis Lua 脚本

---

### 4.4 RequestLogFilter

| 项目 | 内容 |
|------|------|
| **Order** | 0（最后执行，记录完整请求信息） |
| **拦截范围** | 仅 `/openapi/**` |
| **职责** | 记录请求摘要日志 |

**日志格式**：
```
[OPENAPI] POST /openapi/company/query | caller=风控系统 | keyId=10 | traceId=abc123 | 200 | 45ms
```

**核心逻辑**：
1. 记录请求开始时间（exchange.attributes）
2. 请求路由完成后（filter chain 的 `then()` 回调）：
   - 从 attributes 获取 callerId、keyId、traceId
   - 计算耗时 = now - startTime
   - 获取响应 HTTP 状态码
   - 拼装日志字符串，`log.info()` 输出
3. 非 `/openapi/**` 路径直接放行

**设计要点**：
- 使用响应式 `then()` 回调，确保拿到状态码和真实耗时
- 日志格式为单行结构化文本，方便后续接入 ELK
- 不记录请求/响应 body（避免日志膨胀，body 日志由 call 模块 `CallRecord` 负责）

**代码量预估**：~35 行

---

## 5. Filter 汇总

| # | Filter | Order | 拦截路径 | 代码量 | 核心依赖 |
|---|--------|-------|----------|--------|----------|
| 1 | TraceIdFilter | -3 | 全部 | ~30行 | UUID |
| 2 | AuthFilter | -2 | `/openapi/**` | ~60行 | ReactiveRedisTemplate |
| 3 | RateLimitFilter | -1 | `/openapi/**` | ~50行 | Redis Lua 脚本 |
| 4 | RequestLogFilter | 0 | `/openapi/**` | ~35行 | Slf4j |
| **合计** | | | | **~175行** | |

---

## 6. 新增依赖

网关 `pom.xml` 需新增：

```xml
<!-- Spring Boot Redis Reactive (Gateway 使用响应式 Redis) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
</dependency>
```

当前网关已有 `redisson-spring-boot-starter`（阻塞式），需要额外引入响应式 Redis 用于 Gateway Filter 的非阻塞操作。

---

## 7. 新增文件清单

```
data-platform-gateway/src/main/java/com/dataplatform/gateway/
├── filter/
│   ├── TraceIdFilter.java
│   ├── AuthFilter.java
│   ├── RateLimitFilter.java
│   └── RequestLogFilter.java
└── config/
    └── GatewayRedisConfig.java       # ReactiveRedisTemplate Bean 配置

data-platform-gateway/src/test/java/com/dataplatform/gateway/filter/
├── TraceIdFilterTest.java
├── AuthFilterTest.java
├── RateLimitFilterTest.java
└── RequestLogFilterTest.java
```

---

## 8. 单元测试设计

### 8.1 测试工具选型

- 测试框架：JUnit 5 + Mockito（与项目现有测试体系一致）
- Gateway 测试工具：`MockServerWebExchange`、`MockGatewayFilterChain`
- Redis Mock：使用 Mockito mock `ReactiveRedisTemplate` 和 `ReactiveRedisScript`

### 8.2 各 Filter 测试用例

#### TraceIdFilterTest（4 个用例，无外部依赖）

| # | 场景 | 输入 | 期望 |
|---|------|------|------|
| 1 | 无请求头，自动生成 | 无 X-Trace-Id 头 | exchange.attributes 中有 traceId |
| 2 | 有请求头，透传 | X-Trace-Id=abc | traceId = "abc" |
| 3 | 下游请求头传递 | 任意请求 | 下游请求头含 X-Trace-Id |
| 4 | 每次生成唯一 | 连续两次无头请求 | 两个 traceId 不同 |

#### AuthFilterTest（6 个用例，mock ReactiveRedisTemplate）

| # | 场景 | 输入 | 期望 |
|---|------|------|------|
| 1 | 非 /openapi 路径跳过 | `/api/v1/xxx` | 直接放行 |
| 2 | 无 API Key | `/openapi/xxx`，无认证头 | 401 |
| 3 | API Key 无效 | `/openapi/xxx`，key 不在 Redis | 401 |
| 4 | 调用方已禁用 | `/openapi/xxx`，status=0 | 403 |
| 5 | 认证成功 | `/openapi/xxx`，有效 key | 放行，attributes 含 callerId/keyId |
| 6 | Bearer 方式提取 | `Authorization: Bearer xxx` | 正常解析 |

#### RateLimitFilterTest（5 个用例，mock ReactiveRedisTemplate + ReactiveRedisScript）

| # | 场景 | 输入 | 期望 |
|---|------|------|------|
| 1 | 非 /openapi 路径跳过 | `/api/v1/xxx` | 直接放行 |
| 2 | 无自定义配置用默认值 | key 无 rate_limit 配置 | 默认 60s/100 次 |
| 3 | 未超限额放行 | count=50，maxReqs=100 | 放行 |
| 4 | 超限额拒绝 | count=100，maxReqs=100 | 429，含 Retry-After 头 |
| 5 | Redis 异常降级 | Redis 连接失败 | 放行（不阻塞业务） |

#### RequestLogFilterTest（3 个用例，无外部依赖）

| # | 场景 | 输入 | 期望 |
|---|------|------|------|
| 1 | 非 /openapi 路径跳过 | `/api/v1/xxx` | 无 OPENAPI 日志输出 |
| 2 | 正常请求日志 | `/openapi/xxx`，200 | 日志含 caller/traceId/耗时/200 |
| 3 | 异常请求日志 | `/openapi/xxx`，路由异常 | 日志含错误状态码 |

### 8.3 汇总

| 测试类 | 用例数 | 外部 Mock |
|--------|--------|-----------|
| TraceIdFilterTest | 4 | 无 |
| AuthFilterTest | 6 | ReactiveRedisTemplate |
| RateLimitFilterTest | 5 | ReactiveRedisTemplate + ReactiveRedisScript |
| RequestLogFilterTest | 3 | 无 |
| **合计** | **18** | |

---

## 9. 验收标准

1. **TraceIdFilter**：所有请求（含 `/api/v1/**`）都自动生成/透传 `X-Trace-Id`
2. **AuthFilter**：`/openapi/**` 路径校验 API Key，无效 key 返回 401，禁用 key 返回 403，`/api/v1/**` 不受影响
3. **RateLimitFilter**：超出配额的 API Key 返回 429，响应头含 `Retry-After`
4. **RequestLogFilter**：`/openapi/**` 请求输出单行结构化日志，含 traceId/caller/耗时/状态码
5. 编译成功：`mvn compile -pl data-platform-gateway`
6. 不影响现有 13 条路由和 Sa-Token 认证
