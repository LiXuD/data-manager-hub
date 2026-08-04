# 数据管理平台 - 当前任务清单

**最后更新**: 2026-08-03
**当前状态**: `dev` 已完成五域收敛、OpenAPI 与单一接口契约整改、服务间最小权限认证、过期兼容层清理和接口调用权限审批闭环；计费使用版本化方案、事件账本和查询投影。外部请求连接器插件化的核心代码、V042、管理端和隔离 E2E 夹具已完成；隔离六服务、Gateway、签名插件、真实 OpenAPI、发布/回滚、数据库副作用和浏览器管理页面已验收，存量厂商迁移和旧链退役未完成。

---

## 当前基线

- 五个业务域：`masterdata`、`access`、`billing`、`identity`、`governance`；同步跨域调用仅依赖目标域 `*-api` 的 Internal Feign 契约。
- 每张领域表只有所属域可直接读写；跨域统计通过 Access 内部统计契约查询，Billing 不再直接读取 `call_record`。
- `call-record` Kafka 仅用于 Access 域内异步落库；计费计算与日聚合由 Access 同步调用 Billing 完成，不存在跨域 Kafka 消费。
- Gateway、五域服务和前端均可按现有部署文档启动；SDK 是普通 Jar，不作为独立服务部署。
- 数据测试页面通过 `/interface/{id}/contract` 读取请求字段树，自动生成输入项、应用默认值并校验必填项、类型与约束。
- 接口契约只保留 `/contract` 读写；旧 Schema/params/import 端点、访问域旧 `/data/**` 链路和重复 API Key 路由已删除。
- 厂商安全只执行版本化安全流水线；简单 `signType`/`encryptType`、签名构建器和失败回退已删除。

## 已完成里程碑

| 里程碑 | 状态 | 完成时间 |
|---|---|---|
| MVP 验收与核心能力 | 已完成 | 2026-04-26 |
| 五域收敛（13 个小服务合并为 5 域） | 已完成 | 2026-05-16 |
| P1/P2（网关、对账、Nacos、Kafka、Prometheus） | 已完成 | 2026-05-20 |
| V2.0（SkyWalking、SDK 多语言生成、灰度厂商路由） | 已完成 | 2026-05-27 |
| 上线就绪修复与本地运行态验证 | 已完成 | 2026-06-17 |
| 深度更新、过期代码清理与知识库刷新 | 已完成 | 2026-07-23 |
| 接口调用权限申请、Flowable 审批与自动授权 | 已完成 | 2026-07-24 |
| 分系统、分接口缓存策略审批与绝对时效 | 已完成 | 2026-07-24 |
| UAPI 指定日期程序员历史接口与前后端真实链路 | 已完成 | 2026-07-24 |

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
| 计费方案按厂商 + 接口唯一绑定 | 已完成 | V041 对厂商、接口、会计方向和生效区间增加数据库排他约束；运行时多匹配失败关闭；页面按计费维度聚合展示当前、待生效和历史版本 |
| 厂商接口方案支持多档阶梯计费 | 已完成 | `billing_plan_tier`、`billing_usage_balance`；按账期累计调用量并按区间累进计价，请求重试不重复推进阶梯 |
| 模板化与版本化计费方案 | 已完成 | `V021__create_billing_plan_and_event_ledger.sql`；六类模板、响应字段计量、事件账本、套餐/周期费、SLA、契约复核、模拟发布和冲正；旧规则表和迁移已删除 |
| 过期契约与安全兼容层清理 | 已完成 | `V025__remove_obsolete_compatibility_fields.sql`；结构化契约、安全流水线、BCrypt 与密文格式均在迁移和运行时失败关闭 |
| API 与知识库收敛 | 已完成 | 单一 `/contract`、单一 `/caller/apikey` 资源；README、API、部署、架构 Wiki 同步；过期进度和历史验收快照删除 |
| 接口调用权限审批闭环 | 已完成 | 申请/任务/授权 API、草稿编辑与紧急授权 UI、Flowable 7.1.0 适配器、V026 + workflow schema、动态节点表单、到期/撤销、旧全量入口 409 收口 |
| 接口权限审批生产回归 | 已完成 | 全仓 279 个 Maven 测试、前端 lint/build、npm audit、迁移全场景、架构扫描及隔离数据库真实服务 E2E 通过 |
| 缓存策略审批与运行时强制 | 已完成 | 申请/审批天数写入授权事实；未获批或超限调用 403；租户、Caller、接口版本隔离；缓存命中不续期 |
| UAPI 指定日期程序员历史接口 | 已完成 | V029 提供 month/day 契约、真实 UAPI GET 路由、零元计费方案及受保护回滚 |

## 外部请求连接器插件化升级（核心实现与隔离运行验收完成）

详细设计见 [外部请求连接器插件化升级设计](docs/2026-08-03-external-request-connector-plugin-upgrade-design.md)。
状态只按已有证据填写：组件测试或夹具通过不等同全服务上线，存量厂商迁移也不因插件运行时存在而自动完成。

| 阶段 | 状态 | 验收门槛 |
|---|---|---|
| 0. [当前外部请求行为基线固化](docs/2026-08-03-external-request-connector-plugin-upgrade-design.md#141-阶段-0基线固化) | 已完成 | GET/POST/映射/认证/安全/主备路由和错误语义有自动化基线；隔离环境真实 Gateway OpenAPI 首次发布与回滚后调用均成功 |
| 1. [轻量 SPI 与内置 `legacy-http`](docs/2026-08-03-external-request-connector-plugin-upgrade-design.md#142-阶段-1spi-与内置兼容插件) | 已完成 | SPI、六阶段内置插件、强类型结果与 `LEGACY` 默认开关已落地；受控测试和真实 OpenAPI 已执行内置 Transport/Normalizer |
| 2. [插件目录与连接器版本控制面](docs/2026-08-03-external-request-connector-plugin-upgrade-design.md#143-阶段-2版本化控制面) | 已完成 | 真实 API 已验证导入、校验、预加载、激活、草稿 CAS、受控测试、发布、回滚和禁用保护；浏览器已核对动态表单、逐实例状态、差异和版本历史 |
| 3. [签名制品与隔离热加载](docs/2026-08-03-external-request-connector-plugin-upgrade-design.md#144-阶段-3签名制品与隔离热加载) | 单实例隔离运行验收完成 | 签名/哈希、HTTPS 白名单、隔离加载、激活/readiness 和真实调用已通过；多 Access 一致激活、容量和故障演练仍是发布环境门禁 |
| 4. [现有厂商逐项迁移](docs/2026-08-03-external-request-connector-plugin-upgrade-design.md#145-阶段-4逐厂商迁移) | 机制与 fixture 发布/回滚已验收，存量迁移待执行 | 单个 fixture 厂商已由版本 1 发布、版本 2 替换并回滚为新活动版本 3；全部活动厂商迁移与稳定观察尚未执行 |
| 5. [旧静态适配器退役](docs/2026-08-03-external-request-connector-plugin-upgrade-design.md#146-阶段-5旧实现退役) | 未实施 | 当前必须保留 `VendorAdapterFactory`、`HttpVendorAdapter` 和 `LEGACY` 回滚路径，满足全部退役前置条件后再删除 |

受控测试会以 `vendorConfigId + draftVersion + snapshotHash` 追加不可变事实；发布必须匹配当前草稿
的成功事实，插件激活也必须先有包含该固定版本的成功测试事实。隔离环境已验证成功事实、事实不可变、
草稿版本冲突、发布/回滚和活动绑定禁用保护；管理页面的关键状态与交互已通过浏览器操作验收。

## 发布前仍需执行的环境验证

以下是部署环境的必做检查，不是待开发功能：

- 通过环境变量或密钥系统提供 `NACOS_SERVER_ADDR`、数据库和 Redis 的连接及凭据；`docker-compose.yml` 仅用于本地开发和测试。
- 在真实集成环境显式开启外部 API 测试：`mvn test -Dintegration.tests=true` 或 `INTEGRATION_TESTS=true`。
- SkyWalking 生产环境使用持久化后端，不使用本地 compose 中的 H2 存储。
- 合入 `dev` 前执行 `mvn verify`、`npm audit`、`npm run lint`、`npm test`、`npm run build`、隔离数据库迁移回归和 `bash arch-scan.sh`。

## 文档职责

| 文档 | 维护内容 |
|---|---|
| `README.md` | 项目入口、模块边界、快速启动和已完成里程碑 |
| `CODE_WIKI.md` | 当前架构、模块职责和关键实现说明 |
| `docs/API.md` | 对外 HTTP API 契约 |
| `docs/DEPLOYMENT.md` | 本地与生产部署要求 |
| `docs/2026-07-23-deep-cleanup-review.md` | 本轮清理范围、架构决策和回归证据 |
| `docs/2026-08-03-external-request-connector-plugin-upgrade-design.md` | 外部请求连接器插件化当前实现、剩余迁移阶段和验收边界 |
历史实施计划、已验收报告和过期性能样本不保留在当前知识库，需要追溯时请查阅 Git 历史。
