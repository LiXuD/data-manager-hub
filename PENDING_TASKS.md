# 数据管理平台 - 当前任务清单

**最后更新**: 2026-08-10
**当前状态**: `dev` 已完成五域收敛、OpenAPI 与单一接口契约整改、服务间最小权限认证、接口调用权限审批闭环和版本化计费。外部请求连接器插件化阶段 0—5 已全部实现：V043—V045 完成存量配置迁移、PLUGIN-only 约束和旧静态适配器退役，V046—V047 固化完整性与历史不可变约束；双 Access 隔离环境、真实 Gateway/OpenAPI、管理 API、浏览器页面和数据库副作用均已验收。该结论不表示已经部署到生产环境。

---

## 当前基线

- 五个业务域：`masterdata`、`access`、`billing`、`identity`、`governance`；同步跨域调用仅依赖目标域 `*-api` 的 Internal Feign 契约。
- 每张领域表只有所属域可直接读写；跨域统计通过 Access 内部统计契约查询，Billing 不再直接读取 `call_record`。
- `call-record` Kafka 仅用于 Access 域内异步落库；计费计算与日聚合由 Access 同步调用 Billing 完成，不存在跨域 Kafka 消费。
- Gateway、五域服务和前端均可按现有部署文档启动；SDK 是普通 Jar，不作为独立服务部署。
- 数据测试页面通过 `/interface/{id}/contract` 读取请求字段树，自动生成输入项、应用默认值并校验必填项、类型与约束。
- 接口契约只保留 `/contract` 读写；旧 Schema/params/import 端点、访问域旧 `/data/**` 链路和重复 API Key 路由已删除。
- 厂商安全只执行版本化安全流水线；简单 `signType`/`encryptType`、签名构建器和失败回退已删除。
- 厂商外部请求只走发布后的连接器插件流水线；`VendorAdapterFactory`、`HttpVendorAdapter`、旧请求列和 LEGACY 写路径已删除，`legacy-http` 仅作为宿主内置阶段桥接现有 HTTP/映射/安全能力。

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
| 外部请求连接器插件化阶段 0—5 与旧链退役 | 已完成并通过隔离验收 | 2026-08-10 |

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

## 外部请求连接器插件化升级（阶段 0—5 已完成并通过隔离验收）

详细设计见 [外部请求连接器插件化升级设计](docs/2026-08-03-external-request-connector-plugin-upgrade-design.md)。
状态只按已有证据填写：当前工作树与隔离运行环境已经完成验收，但未声称生产部署、生产流量切换或生产容量演练已经完成。

| 阶段 | 状态 | 验收门槛 |
|---|---|---|
| 0. [当前外部请求行为基线固化](docs/2026-08-03-external-request-connector-plugin-upgrade-design.md#141-阶段-0基线固化) | 已实现并通过隔离验收 | GET/POST、映射、安全、主备、错误、缓存和计费行为有自动化与真实 Gateway 基线 |
| 1. [轻量 SPI 与内置 `legacy-http`](docs/2026-08-03-external-request-connector-plugin-upgrade-design.md#142-阶段-1spi-与内置兼容插件) | 已实现并通过隔离验收 | 六阶段 SPI、强类型结果、Stage 生命周期、内置 Transport/Normalizer 和受限执行器已落地 |
| 2. [插件目录与连接器版本控制面](docs/2026-08-03-external-request-connector-plugin-upgrade-design.md#143-阶段-2版本化控制面) | 已实现并通过隔离验收 | 导入、验证、双实例预加载/激活、草稿 CAS、Schema/secretRef、受控测试、发布、历史和回滚均通过 |
| 3. [签名制品与隔离热加载](docs/2026-08-03-external-request-connector-plugin-upgrade-design.md#144-阶段-3签名制品与隔离热加载) | 已实现并通过隔离验收 | 双 Access 一致激活、单实例失败门禁、离线缓存/readiness、在途租约、卸载 gauge 和 100 次生命周期专测均通过 |
| 4. [现有厂商逐项迁移](docs/2026-08-03-external-request-connector-plugin-upgrade-design.md#145-阶段-4逐厂商迁移) | 已实现并通过隔离验收 | V043 迁移观测事实与只读历史、V044 全量 PLUGIN 转换及失败关闭约束已完成；真实 fixture 迁移/回滚通过 |
| 5. [旧静态适配器退役](docs/2026-08-03-external-request-connector-plugin-upgrade-design.md#146-阶段-5旧实现退役) | 已实现并通过隔离验收 | V045 删除旧请求列；静态工厂、旧 HTTP Adapter、迁移写端点和旧固定编辑器均已删除；V047 冻结历史事实 |

受控测试会以 `vendorConfigId + draftVersion + snapshotHash` 追加不可变事实；发布必须匹配当前草稿
的成功事实，插件激活也必须先有包含该固定版本的成功测试事实。隔离环境已验证成功事实、事实不可变、
草稿版本冲突、发布/回滚和活动绑定禁用保护；管理页面的关键状态与交互已通过浏览器操作验收。

## 仍需执行的生产发布门禁

以下是部署环境的必做检查，不是待开发功能：

- 通过环境变量或密钥系统提供 `NACOS_SERVER_ADDR`、数据库和 Redis 的连接及凭据；`docker-compose.yml` 仅用于本地开发和测试。
- 在真实集成环境显式开启外部 API 测试：`mvn test -Dintegration.tests=true` 或 `INTEGRATION_TESTS=true`。
- SkyWalking 生产环境使用持久化后端，不使用本地 compose 中的 H2 存储。
- 在生产制品库和部署 TrustStore 中配置经审批的签名公钥、仓库前缀、证书链和 Access 持久缓存卷；隔离验收使用的一次性凭据已经销毁。
- 按生产 Access 实例规模执行容量、滚动升级、制品库故障和告警联动演练，并依据生产变更流程决定上线窗口；这些是环境/发布工作，不是待开发功能。
- 合入 `dev` 前执行 `mvn verify`、`npm audit`、`npm run lint`、`npm test`、`npm run build`、隔离数据库迁移回归和 `bash arch-scan.sh`。

## 文档职责

| 文档 | 维护内容 |
|---|---|
| `README.md` | 项目入口、模块边界、快速启动和已完成里程碑 |
| `CODE_WIKI.md` | 当前架构、模块职责和关键实现说明 |
| `docs/API.md` | 对外 HTTP API 契约 |
| `docs/DEPLOYMENT.md` | 本地与生产部署要求 |
| `docs/2026-07-23-deep-cleanup-review.md` | 本轮清理范围、架构决策和回归证据 |
| `sql/MIGRATIONS.md` | V001—V047 的迁移、不可逆边界和前向恢复要求 |
| `docs/2026-08-03-external-request-connector-plugin-upgrade-design.md` | Bumblebee 参考、实施前基线、现已落地架构和隔离验收边界 |
历史实施计划、已验收报告和过期性能样本不保留在当前知识库，需要追溯时请查阅 Git 历史。
