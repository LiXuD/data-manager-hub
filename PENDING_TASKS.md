# 数据管理平台 - 当前任务清单

**最后更新**: 2026-08-30
**当前状态**: `dev` 的 fresh V052、六服务、前端和 3/2/2 真实业务夹具已经形成可重复闭环；本地 full build、持久 demo、真实浏览器和受保护清理均有直接证据。默认分支已镜像并激活 Dev MVP 工作日调度，Ubuntu 手工 run `33289826840` 已用 `dev` SHA `b3a7343be809e245d2696dd7200f96698693bad4` full-build 通过并上传报告。当前主线转为保持 Dev MVP 可重复、定时发现回归和清理非阻断前端债务；生产厂商 inventory/迁移/观察/容量/滚动升级和阶段 6 旧入口最终退役继续保留为后续生产门禁，不作为当前开发主线。

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
- 普通连接器配置通过 `connectorSpec` 选择一个固定插件版本并填写一份 Schema 表单；Masterdata 确定性编译隐藏的六阶段 `pipelineSnapshot`，Access 仍只消费不可变快照。
- `generic-http:2.0.0` 是宿主内置的标准单次 HTTPS 产品插件；`platform-core:1.0.0` 承担平台 Transport、安全和响应映射，二者均受签名目录事实、SecretRef 所有权和 Access 网络策略约束。
- `ADVANCED_LEGACY` 草稿和历史继续只读可解释；旧 raw 变更、测试、发布和回滚接口不能覆盖 SIMPLE 事实，Legacy 可通过只读预检和 CAS 转换进入 SIMPLE。

## 当前开发里程碑：dev 环境跑通

- [x] V049/V050 前向-only 迁移隔离修复；V049、V050、fresh V052、重复 update、Liquibase validate 已在隔离库通过。
- [x] 本机 dev 数据库已升级至 V052，`migrate-db.sh status` 和数据库事实均为 0 pending。
- [x] `data-platform-test/test-fixtures/dev-mvp/verify-dev-closure.sh` 提供 fresh 基础设施、迁移、六服务、前端和业务验收入口；报告 v2 记录源码 SHA、dirty 状态、时间、耗时和构建模式。
- [x] dev MVP 夹具与正式迁移分离，验证 3 家厂商、2 个调用系统、2 类数据类型、单 HTTP/Token+业务/主备三类连接器流程，以及审批、OpenAPI、CallRecord、Billing、审计和监控落库事实。
- [x] 普通配置页收敛为插件、固定版本和一次 Schema 表单；阶段、能力、顺序、TRANSPORT 和摘要迁移到 `system:admin` 保护的“连接器运行诊断”页。
- [x] `--demo` 可在命令返回后保留隔离运行态，真实浏览器已验证登录、连接器诊断、2 条有效授权、12 条调用、¥1/3 次计费、54 条成功审计和 6/6 服务健康；受保护清理可停止进程并删除隔离库/目录。
- [x] `dev` 分支保护要求 `CI / required-ci`，启用管理员约束、stale approval 失效、线性历史并禁止 force-push/删除。
- [x] 从 `master` 选择性同步当前 Maven/npm/Action 安全版本，并用 `ci/contracts/dev-security-sync.v1.json` 明确禁止把生产 CI 作业误并入 dev required CI；远端新发现的 Spring Kafka 1 high/2 moderate 告警使用 Boot 3.5.16 BOM 兼容的 3.3.16 独立修复。
- [x] `.github/workflows/dev-mvp-e2e.yml` 已通过 PR #27 镜像到默认分支 `master` 并处于 active，提供工作日定时和手工 full-build 验收，明确 checkout `dev`，不在 push/PR 上运行，不作为 required check；Ubuntu 手工 run `33289826840` 已成功，artifact 保留 14 天。
- [ ] 清理 `data-platform-web/src/views/layout/index.vue` 运行时图标模板造成的 Vue runtime compiler warning，并增加登录后 console warning 烟雾断言。

## 开发阶段 CI/CD（当前生效）

当前提交和 PR 只运行 [基础 CI](.github/workflows/ci.yml)：安全同步/CI 边界验证、65 个仓库合同测试、
后端 Java 编译/单元测试，以及前端依赖安装、lint、单元测试和构建。`CI / required-ci` 是唯一的
开发阶段必需检查，失败不得合并。Dev MVP fresh E2E 按工作日定时或手工执行，只用于持续观察，
不阻塞每个 PR。
完整部署、制品和生产方案保留在 [生产前 CI/CD 设计](docs/2026-08-22-ci-cd-pipeline-design.md)，
当前不自动触发。

覆盖率、Checkstyle 和 SpotBugs 已移入根 POM 的 `quality` Profile，需要时手工执行，不属于基础 CI 门禁。

当前阶段性成果：通过 `docker-compose.local-infra.yml`，Mac Docker 已提供 PostgreSQL 16、Redis 7、Kafka 3.7、Nacos 2.3.2；Mac
本地可启动六个后端服务和 Vite 前端，六个后端健康检查均为 `UP`，数据库 V001—V052 已迁移并
通过校验。该成果满足开发阶段执行目标，但不改变 staging/production 尚未真实发布的结论。

| 阶段 | 状态 | 当前边界 |
|---|---|---|
| 开发阶段基础 CI | 已实现；每个 PR 仍须等待最终 SHA 远端 CI | 安全同步与仓库合同、后端 `verify`（排除 `data-platform-test` 的 API、测试服务和 E2E fixture）、前端 `npm ci/lint/test/build`、`CI / required-ci` |
| Dev MVP 定时 E2E | 已实现，非 required check | 工作日和手工 full-build、fresh V052、六服务、前端、3/2/2 业务事实及安全报告 artifact |
| 生产前置能力 | 保留设计和代码蓝图，当前不自动执行 | 部署、GHCR/OCI、签名、Helm/Kubernetes、Nacos、快照、生产发布和回滚 |

### 生产部署前置方案（当前延期，不作为开发门禁）

以下事项在生产部署前重新启用；当前不要求执行，也不能作为开发阶段 CI 的失败原因。完成前不得把
生产标记为已部署：

- [x] 将 `master` 设置为默认受保护分支，启用 `CI / required-ci`、CODEOWNERS 路径合同、stale approval 失效和禁止 force-push/删除；当前单人模式的 PR 审批数为 0，不把 CODEOWNERS 误报为独立 reviewer。
- [ ] 合并含 `.github/CODEOWNERS` 的分支后运行只读 `python3 ci/scripts/verify-github-readiness.py --repository LiXuD/data-manager-hub --review-mode solo-maintainer --output evidence/github-readiness.json`；审计必须同时证明四个 Environment、单人 review 合同、required check、受保护分支策略和三个在线部署 Runner label，失败时不得启用 production Environment。
- [x] 创建 `dev`、`staging`、`production`、`plugin-signing` GitHub Environment；staging/production/plugin-signing 的 required reviewer 为 `LiXuD` 且 `prevent_self_review=false`，production wait timer 为 1800 秒。该配置允许单人自审但不提供独立复核；仍需配置独立 `nonprod-deploy`/`prod-deploy`/`plugin-signing` Runner。
- [ ] 将来增加独立 reviewer 后，切回双人模式：PR 至少 1 个审批、启用 CODEOWNERS review、恢复 stale approval 失效，并把 staging/production/plugin-signing 的 `prevent_self_review` 设为 `true`。
- [ ] 先应用 `deploy/rbac/overlays/dev|staging|production`，由 overlay 预置 `dmh-deployer`/`dmh-runtime` ServiceAccount，并将 `dmh-deployer` Role 绑定到 ARC Runner ServiceAccount；受保护 ARC Runner 使用 `dmh-deployer` token，Chart 业务 Pod 使用无权限且不自动挂载 token 的 `dmh-runtime` ServiceAccount。再部署 ARC ephemeral Runner，验证 kubeconfig 实际身份为 `system:serviceaccount:<namespace>:dmh-deployer`、只能操作所属 namespace，不能 list/watch/create/update/patch/delete Secret，且 fork PR 无法调度该 Runner；`preflight-cluster.sh` 还必须以该身份对 command override 做 API Server `dry-run=server`，确认 admission 已完成 type-check 后才允许创建真实 Job。
- [ ] 在 Kubernetes 1.30+ 以 cluster-scoped 权限应用 `deploy/admission` 的 ValidatingAdmissionPolicy；验证 dmh Job 只能使用受信 data-manager-hub GHCR digest 镜像、单一容器、镜像 entrypoint 和 allowlist 参数，并只能使用 `dmh-runtime`、`dmh-ghcr-pull`、`dmh-acceptance` 允许边界，拒绝任意 Secret/ConfigMap 环境注入、非 allowlist 直接环境变量、ConfigMap/DownwardAPI/PVC/Secret volume、hostPath/CSI volume、非零 backoff 和 host namespace，只允许三个固定 `emptyDir` 挂载；Pod 固定 UID/GID 10001、RuntimeDefault seccomp、禁止提权并 drop `ALL` capabilities，acceptance 允许的临时可写 root 仍必须限制为 acceptance digest 且无 token。
- [ ] 创建并轮换 `dmh-runtime`、`dmh-internal-auth`、`dmh-connector-truststore`、`dmh-ghcr-pull`、`dmh-acceptance`、`dmh-snapshot-verifier`，不把值写入仓库或 Actions artifact；`dmh-runtime` 至少包含 `NACOS_SERVER_ADDR`，其中 `dmh-internal-auth` 必须包含六个 `INTERNAL_AUTH_*_SECRET`/`INTERNAL_AUTH_TOKEN_URI`、`PLATFORM_ENCRYPTION_MASTER_KEY`、`public.pem` 和 `private.pem`，并验证 Java Pod 能以 UID 10001 读取挂载文件。
- [ ] 配置 GHCR 包权限和保留策略：production digest/Manifest、九镜像与 Build Manifest 的 OCI SemVer 别名永不删除，候选、SBOM、provenance 至少 365 天；完成一次 retention audit，并在真实 GHCR 回读十个别名的 digest 一致性。
- [ ] 配置插件签名/KMS、Nexus/S3 adapter、TrustStore，并用真实插件 receipt 完成一次签名回读。
- [ ] 配置 Nacos namespace/immutable Group、PostgreSQL16、Redis、Kafka、Ingress/TLS、StorageClass 和 Prometheus 指标/短期 bearer Token；非 loopback Prometheus URL 无 Token 必须保持 fail-closed。
- [x] 已在 GitHub Actions Repository Secret 配置 `NVD_API_KEY`（只记录名称，不记录值）；PR #5 的 GitHub-hosted CI run `32806667253` 已通过不泄露值的 API preflight、OWASP Dependency-Check/CycloneDX、CodeQL 和 `CI / required-ci`，并上传 SARIF/JSON 供应链证据；NVD 数据目录已保存为 Actions cache，后续 PR 不应重复冷启动。
- [x] OWASP Dependency-Check/CycloneDX 供应链门禁：Spring Boot/Cloud/Alibaba 已升至兼容的 3.5.16/2025.0.3/2025.0.0.0 组合，并修复 HttpClient、HttpCore、Jackson、Commons Lang 等可用补丁；2026-08-24 扫描无未抑制 CVSS≥7 结果。`CVE-2025-7962` 对 `org.eclipse.angus:angus-activation:2.0.3` 属于 Angus Mail SMTP 的错误 CPE 归属，已用版本精确 suppression 记录 jar 内容与适用边界；依赖升级后必须重新审查，不得把 suppression 当作通用 waiver。Nacos/Prometheus/Tomcat/Kotlin/Validator 的已审计 CPE 误匹配同样仅按精确组件+CVE suppression 记录理由；OSS Index 未配置独立认证令牌，已在 Maven profile 中显式关闭并由 NVD/CodeQL/npm audit 分担门禁，不能误报为完整 OSS Index 覆盖。
- [ ] 部署并验证 PostgreSQL snapshot/PITR adapter 与 `DMH_SNAPSHOT_SIGNATURE_VERIFIER`；生产快照必须由晋级流程绑定成功 staging Deployment 时间戳，完成快照恢复到新实例、Liquibase 校验和、Secret 切换与 Helm rollback 演练。
- [ ] 在 dev→staging 完成真实 acceptance、登录态 UI、容量、Access Pod/PVC、Nacos/制品仓库故障演练；在 production 完成无流量彩排、具体 digest 回滚和至少两次成功发布，连续观察 14 天。

开发阶段每次改动只需先执行：

```bash
./mvnw -B -ntp verify \
  -pl '!data-platform-test/data-platform-test-api,!data-platform-test/data-platform-test-service,!data-platform-test/test-fixtures/external-connector-plugin'
cd data-platform-web
npm ci
npm run lint
npm test
npm run build
cd ..
git diff --check
```

完整多服务集成、迁移矩阵、Docker、Helm、Kubernetes、供应链、部署和生产回滚检查均属于
生产部署前置事项，当前不进入提交级 CI。

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
| 连接器粗粒度产品模型阶段 0—4 | 已实现并通过隔离自动化验收；未生产发布 | 2026-08-20 |
| CI/CD 开发阶段本地运行闭环 | 已完成；非生产/生产 CD 未验证 | 2026-08-25 |

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

## 连接器粗粒度插件与配置简化（阶段 0—4 已验收，阶段 5 控制面与隔离链路已验收）

详细实施方案见 [连接器粗粒度插件模型与配置简化优化设计](docs/2026-08-12-connector-product-model-simplification-design.md)。
本方案保留现有六阶段运行时和治理能力，把普通产品模型收敛为“选择一个连接器插件、固定一个版本、填写一份配置”。阶段 0—4 已完成代码和隔离自动化验收；阶段 5 的 inventory、逐厂商 CAS 控制、三域观察门禁、隔离真实链路和容量观察入口已验证，但生产迁移与观察仍未执行；阶段 6 已完成写入口保护和隔离回滚验证，最终退役仍是生产退役门禁，不能因代码存在而标记完成。

| 阶段 | 状态 | 验收门槛 |
|---|---|---|
| 0. [基线与可转换性清点](docs/2026-08-12-connector-product-model-simplification-design.md#171-阶段-0基线与可转换性清点) | 已实现并通过隔离自动化验收 | 高级 API/UI 基线、只读 converter、三类 fixture 和分页 inventory 已落地；生产库实际分类仍属于阶段 5 发布准备 |
| 1. [Manifest v2 与插件开发 SDK](docs/2026-08-12-connector-product-model-simplification-design.md#172-阶段-1manifest-v2-与插件开发-sdk) | 已实现并通过模块测试 | v1/v2 双读、高层单入口 SDK、Managed Session、`platform-core`、TestKit 与真实 runtime 示例均通过 |
| 2. [Masterdata Spec 控制面与 V049](docs/2026-08-12-connector-product-model-simplification-design.md#173-阶段-2masterdata-spec-控制面与-v049) | 已实现并通过隔离数据库验收 | Spec 草稿/校验/测试/发布/历史/回滚/升级预检、确定性编译及 V049/U049 fresh/upgrade/HALT 矩阵通过 |
| 3. [Generic HTTP 2.0 与转换](docs/2026-08-12-connector-product-model-simplification-design.md#174-阶段-3generic-http-20-与转换) | 已实现并通过隔离数据库与模块测试 | `generic-http:2.0.0`、V050/U050、转换预检/CAS、Legacy inventory 和离线对等 fixture 已落地 |
| 4. [前端简化工作区](docs/2026-08-12-connector-product-model-simplification-design.md#175-阶段-4前端简化工作区) | 已实现并通过 lint/typecheck/Vitest/build 与管理员登录态浏览器验收 | 普通页面只展示插件、固定版本和一次 Schema 表单；阶段、能力、顺序、TRANSPORT 和摘要移至管理员诊断页；隔离浏览器已完成保存、校验、受控测试、发布、历史和回滚 |
| 5. [受控发布与逐厂商迁移](docs/2026-08-12-connector-product-model-simplification-design.md#176-阶段-5受控发布与逐厂商迁移) | 控制面、隔离真实链路和容量基线已验证，生产验收未完成 | `/vendor/config/connector-spec/inventory`、prepare/start-observation/observe/complete/rollback 和 Access/Billing 聚合门禁已落地；`run-api-e2e.sh` 已验证单 HTTP、Token+业务请求、有限轮询、请求/响应/错误/缓存/计费和主备实际厂商事实，`observe-capacity.sh` 已验证 8 并发/32 请求；仍必须在目标环境逐厂商完成真实迁移、容量、滚动升级和观察窗口 |
| 6. [旧高级写入口收口](docs/2026-08-12-connector-product-model-simplification-design.md#177-阶段-6旧高级写入口收口) | 已实现可切换退役门禁，生产最终切换未实施 | raw 写入口不再允许从空白创建新的 `ADVANCED_LEGACY` 草稿（返回 `LEGACY_DRAFT_REQUIRED`），继续阻止覆盖 SIMPLE；`CONNECTOR_LEGACY_WRITE_RETIRED=true` 且活动 Legacy、Legacy 草稿、未结束迁移均为 0 时，raw PUT/POST 写/测试/发布/回滚返回 410；门禁未满足返回 409，raw validate 保持只读例外 |

迁移数据库验收入口为 `verify-v049-connector-product-spec.sh` 和 `verify-v050-generic-http.sh`。两者只创建名称受限的临时 PostgreSQL 数据库，覆盖 fresh、前版本升级、重复执行、漂移/HALT 原子性、安全回滚与重新应用，并在退出时清理；这不是任何生产数据库的迁移记录。阶段 5 控制面还要求登录态调用新迁移 API，生产厂商事实仍需单独形成证据。

### 2026-08-28 隔离真实链路证据

当前隔离运行已启动 PostgreSQL、Redis、Kafka、Nacos、Identity、Masterdata、Access 双实例、
Billing、Governance、Gateway、Web 和本地 TLS 制品/厂商 fixture。通过 Gateway 已验证：

- 管理员登录、签名 Manifest v2 导入/校验/预加载/激活、Legacy 转换、Spec CAS 保存、校验、
  受控测试和发布；
- 管理员登录态浏览器已完成接口管理 → 主备厂商 → 简化连接器表单的真实操作，覆盖保存、校验、受控测试、发布、版本历史、Simple 回滚和 Legacy 回滚；页面未出现 stageKey/capability/order/enabled/TRANSPORT 编辑控件，两个 Access 实例均显示 READY；
- Caller/Product/API Key/Scene、接口授权审批和缓存审批；
- OpenAPI 单条/批量请求、真实厂商响应、HTTP 错误、响应解析错误、缓存命中不再访问厂商、
  CallRecord/BillingEvent 实际接口身份、插件版本、流水线版本与快照摘要，以及主备配置/版本摘要对等；
- `run-api-e2e.sh` 在一轮隔离 API 链路中验证 Legacy inventory 分类、单 HTTP、Token+业务请求、有限轮询和备用配置真实调用：
  `apiE2e=passed`、22/22 条 CallRecord 具备接口身份和连接器版本事实、6/6 条 BillingEvent 具备接口身份、总额 1.25000000、缓存命中 1 次，
  错误码覆盖 `BUSINESS_REJECTED`、`RESPONSE_PARSE_ERROR`、`TRANSPORT_HTTP_ERROR`、
  `TRANSPORT_CONNECTION_ERROR`，备用路由返回 `READY` 且实际厂商事实已落库；迁移控制面按
  `PREPARED → OBSERVING → READY → STABLE` 通过观察门禁，Legacy inventory 返回 3 个隔离配置且目标配置分类为
  `LOSSLESS_CONVERTIBLE`；
- 同一轮链路还直接读取 HTTPS 厂商 fixture 计数器：缓存未命中使主厂商请求增加 1，缓存命中计数保持不变；4 个错误向量均命中主厂商且备用计数增加 0；熔断后主厂商继续有错误请求并仅增加 1 次备用厂商请求，最终计数为 `vendor=24/echo=22/fallback=2/token=2/business=2/asyncSubmit=2/asyncPoll=4`；
- 两个 Access 实例均 READY，单实例直接请求与服务发现状态可读，V051 已修复完整连接器错误码
  在异步 CallRecord 落库时被旧列宽截断的问题，V052 已将真实调用的规范接口身份写入 CallRecord。
- connector-e2e/observe-capacity.sh 已提供隔离容量观察入口；当前 fixture 以并发 8 发起
  32 个真实 Gateway 请求，32/32 返回 HTTP 200 且业务成功、32/32 写入 CallRecord，且 32/32 具备接口身份、
  插件版本、流水线版本和快照摘要事实，0 个错误记录，客户端 p95 为 183.2ms。这是单机隔离基线，不是生产容量结论。
- acceptance 镜像已加入 `ConnectorProductFlowTest`，并由 `runtime-contract.v1.yaml` 强制
  `dmh-acceptance` 提供完整的目标配置、请求向量、错误/缓存/主备断言和容量阈值；缺少任一键即
  fail-closed，不会把只有旧 UAPI smoke 的 Job 当作连接器发布验收。该测试只读取预批准目标，已在
  同一隔离多服务环境以完整 Secret 向量通过 `2/2`；生产仍需配置真实 Secret 并运行 Job。

这证明的是一个签名单插件 fixture 的隔离 API/多服务/浏览器链路，不等于生产厂商迁移完成。仍缺生产
inventory、生产观察窗口、生产容量/滚动升级和生产回滚演练；阶段 6 的可切换退役门禁已实现，但生产
尚未满足事实门禁，因此当前仍保留既有 Legacy 兼容入口。raw validate 继续是只读例外。

## 仍需执行的生产发布门禁

以下是部署环境的必做检查，不是待开发功能：

- 通过环境变量或密钥系统提供 `NACOS_SERVER_ADDR`、数据库和 Redis 的连接及凭据；`docker-compose.yml` 仅用于本地开发和测试。
- 在真实集成环境显式开启外部 API 测试：`mvn test -Dintegration.tests=true` 或 `INTEGRATION_TESTS=true`。
- SkyWalking 生产环境使用持久化后端，不使用本地 compose 中的 H2 存储。
- 在生产制品库和部署 TrustStore 中配置经审批的签名公钥、仓库前缀、证书链和 Access 持久缓存卷；隔离验收使用的一次性凭据已经销毁。
- 按生产 Access 实例规模执行容量、滚动升级、制品库故障和告警联动演练，并依据生产变更流程决定上线窗口；这些是环境/发布工作，不是待开发功能。
- 在目标环境运行只读 `/vendor/config/connector-spec/inventory`，逐项确认 `LOSSLESS_CONVERTIBLE/REQUIRES_DEDICATED_PLUGIN/MUST_REMAIN_LEGACY`，不得用隔离 fixture 代替生产事实清点。
- 对目标厂商完成真实登录态工作区的选择、升级预检、保存、校验、测试、发布、历史、回滚和 Legacy 转换，并调用阶段 5 控制面核对 CallRecord/Billing/缓存/主备副作用；隔离 API/多服务链路和管理员浏览器交互已完成，目标环境生产事实仍需单独验收。
- 阶段 5 观察窗口、生产容量和回滚演练完成前，不执行阶段 6 的 raw 入口物理删除。
- 生产阶段 5 门禁完成后，将 `CONNECTOR_LEGACY_WRITE_RETIRED=true` 写入 Masterdata Secret/Nacos；服务会先读取活动 Legacy、Legacy 草稿和未结束迁移事实，只有三者均为 0 才返回 HTTP 410，事实查询异常或门禁未满足不会误报退役成功。
- 生产部署阶段重新启用高级门禁后，才执行隔离数据库迁移回归、架构扫描、供应链、Helm/RBAC/Admission 和外部平台验证；开发阶段只执行上文基础 Java/Web CI。

## 文档职责

| 文档 | 维护内容 |
|---|---|
| `README.md` | 项目入口、模块边界、快速启动和已完成里程碑 |
| `CODE_WIKI.md` | 当前架构、模块职责和关键实现说明 |
| `docs/API.md` | 对外 HTTP API 契约 |
| `docs/DEPLOYMENT.md` | 本地与生产部署要求 |
| `docs/2026-08-22-ci-cd-pipeline-design.md` | 生产部署前的 CI/CD 设计蓝图；当前开发阶段只以 `.github/workflows/ci.yml` 的编译与测试为准 |
| `docs/runbooks/database-recovery.md` | PostgreSQL 快照/PITR 恢复、角色、验证、演练和禁止自动 rollback 的事故流程 |
| `docs/runbooks/release-deployment.md` | Manifest 晋级、Nacos/Helm/Access 发布顺序、门禁、回滚和发布证据 |
| `docs/2026-07-23-deep-cleanup-review.md` | 本轮清理范围、架构决策和回归证据 |
| `sql/MIGRATIONS.md` | V001—V052 的迁移、V049/U049、V050/U050、V051/U051 和 V052/U052 前向恢复要求 |
| `docs/2026-08-03-external-request-connector-plugin-upgrade-design.md` | Bumblebee 参考、实施前基线、现已落地架构和隔离验收边界 |
| `docs/2026-08-12-connector-product-model-simplification-design.md` | 粗粒度插件产品模型、阶段 0—5 控制面与隔离链路证据、阶段 5 生产迁移与阶段 6 退役门禁 |
历史实施计划、已验收报告和过期性能样本不保留在当前知识库，需要追溯时请查阅 Git 历史。
