# 数据管理平台 - 当前任务清单

**最后更新**: 2026-07-22
**当前状态**: `dev` 已完成五域收敛、OpenAPI 调用链路整改和服务间最小权限认证；计费已收敛为版本化方案、事件账本和查询投影，不保留旧规则兼容层。

---

## 当前基线

- 五个业务域：`masterdata`、`access`、`billing`、`identity`、`governance`；同步跨域调用仅依赖目标域 `*-api` 的 Internal Feign 契约。
- 每张领域表只有所属域可直接读写；跨域统计通过 Access 内部统计契约查询，Billing 不再直接读取 `call_record`。
- `call-record` Kafka 仅用于 Access 域内异步落库；计费计算与日聚合由 Access 同步调用 Billing 完成，不存在跨域 Kafka 消费。
- Gateway、五域服务和前端均可按现有部署文档启动；SDK 是普通 Jar，不作为独立服务部署。
- 最新增量：数据测试页面会读取接口参数定义，自动生成输入项、应用默认值并校验必填项与参数类型。

## 已完成里程碑

| 里程碑 | 状态 | 完成时间 |
|---|---|---|
| MVP 验收与核心能力 | 已完成 | 2026-04-26 |
| 五域收敛（13 个小服务合并为 5 域） | 已完成 | 2026-05-16 |
| P1/P2（网关、对账、Nacos、Kafka、Prometheus） | 已完成 | 2026-05-20 |
| V2.0（SkyWalking、SDK 多语言生成、灰度厂商路由） | 已完成 | 2026-05-27 |
| 上线就绪修复与本地运行态验证 | 已完成 | 2026-06-17 |

## 当前整改进度

| 整改项 | 状态 | 证据 |
|---|---|---|
| API Key 创建路径生成完整密钥并同步网关 Redis 缓存 | 已完成 | `ApiKeyServiceImpl`、`ApiKeyCacheService`、`ApiKeyController` |
| 网关 OpenAPI 鉴权缓存格式兼容与拒绝请求日志覆盖 | 已完成 | `AuthFilter`、`RequestLogFilter` |
| `call_record` 分区随当前月份自动创建 | 已完成 | `sql/init.sql`、`V008__create_current_call_record_partitions.sql` |
| OpenAPI 参数定义运行态校验与配额扣减 | 已完成 | `OpenApiQueryController` |
| 调用记录敏感请求字段脱敏，缓存候选限定为显式启用缓存的记录 | 已完成 | `OpenApiQueryService`、`CallRecordServiceImpl` |
| 本地端到端演示配置与模拟厂商 | 已移除 | 不再在生产代码和迁移中提供固定成功响应或固定密钥 |
| 服务间机器身份认证、受众与 scope 校验 | 已完成 | `InternalSecurityAutoConfiguration`、`InternalTokenController`、`InternalAuthFeignInterceptor` |
| 用户 Token 真实会话校验与跨域共享 | 已完成 | `AuthInterceptor` 使用 Sa-Token 校验；`sa-token-redis-jackson` 共享 Redis 会话 |
| 内部契约与管理契约分离，统一 `/internal/v1/**` | 已完成 | 各域 `*InternalFeignClient` 与 Internal Controller |
| Gateway 禁止内部路由并清理可信请求头 | 已完成 | `InternalBoundaryFilter` |
| 真实服务启动后的端到端调用验证 | 已完成 | OpenAPI 200；Masterdata/Billing Feign 认证通过；`call_record` 和 `billing_daily` 落库 |
| 新版计费方案与数据库结构对齐 | 已完成 | `V021__create_billing_plan_and_event_ledger.sql`；调用只使用固定方案版本和策略哈希，不走旧规则或 Access 降级价格 |
| 治理域内部操作日志字段对齐 | 已完成 | `operation_type`、`operation_module` 统一补全；Service JWT 调用写入 `operation_log` 验证通过 |
| 跨域统计移出共享数据库直读 | 已完成 | `CallStatsInternalFeignClient`、`CallStatsInternalController`；Masterdata/Billing 不再包含 `call_record` Mapper |
| 计费入账改为认证 Feign 且失败关闭 | 已完成 | `BillingChargeService`；Access 不再吞掉 Billing 异常或使用假价格 |
| Service Token 按 audience 授予最小 scope | 已完成 | `clients.*.grants.<audience>`；敏感厂商密钥使用 `masterdata:vendor-secret:read` |
| 内部认证默认开启并增加超时、有限重试与缓存测试 | 已完成 | 五域 dev profile、`ServiceTokenProvider`、认证单元测试 |
| 操作日志自动装配与上下文容错 | 已完成 | `OperationLogAutoConfiguration`、`LogApiAutoConfiguration`、`OperationLogAspect` |
| 清理公共 Feign 与重复适配控制器 | 已完成 | 仅保留 `/internal/**` Feign；管理 HTTP Controller 不再实现 Feign 契约 |
| 架构规则自动守护 | 已完成 | `arch-scan.sh` 检查公共 Feign、隐式扫描、scope、跨域读表和跨域 Kafka |
| 本轮整改运行态复验 | 已完成 | 六服务健康检查均为 `UP`；认证负向用例返回 401/403；OpenAPI、统计、计费幂等聚合和治理日志链路均通过 |
| UAPI 程序员历史外部数据源接入 | 已完成 | `V017__seed_uapi_programmer_history_provider.sql`；真实 GET 调用、响应契约、调用记录与零元计费由 `UapiProgrammerHistoryFlowTest` 验证 |
| 计费方案按厂商 + 接口唯一绑定 | 已完成 | `billing_plan` 的厂商、接口和会计方向约束；计费匹配不再使用数据类型，页面按厂商联动选择接口 |
| 厂商接口方案支持多档阶梯计费 | 已完成 | `billing_plan_tier`、`billing_usage_balance`；按账期累计调用量并按区间累进计价，请求重试不重复推进阶梯 |
| 模板化与版本化计费方案 | 已完成 | `V021__create_billing_plan_and_event_ledger.sql`；六类模板、响应字段计量、事件账本、套餐/周期费、SLA、契约复核、模拟发布和冲正；旧规则表和迁移已删除 |

## 发布前仍需执行的环境验证

以下是部署环境的必做检查，不是待开发功能：

- 通过环境变量或密钥系统提供 `NACOS_SERVER_ADDR`、数据库和 Redis 的连接及凭据；`docker-compose.yml` 仅用于本地开发和测试。
- 在真实集成环境显式开启外部 API 测试：`mvn test -Dintegration.tests=true` 或 `INTEGRATION_TESTS=true`。
- SkyWalking 生产环境使用持久化后端，不使用本地 compose 中的 H2 存储。
- 合入 `dev` 前执行 `mvn -q validate`、后端编译与测试、前端构建和 `bash arch-scan.sh`。

## 文档职责

| 文档 | 维护内容 |
|---|---|
| `README.md` | 项目入口、模块边界、快速启动和已完成里程碑 |
| `CODE_WIKI.md` | 当前架构、模块职责和关键实现说明 |
| `docs/API.md` | 对外 HTTP API 契约 |
| `docs/DEPLOYMENT.md` | 本地与生产部署要求 |
| `docs/2026-06-16-production-readiness-review.md` | 最近一次上线就绪审查的验证证据与环境约束 |

历史实施计划、已验收报告和过期性能样本已从工作区文档中移除，需要追溯时请查阅 Git 历史。
