# 数据管理平台 - 当前任务清单

**最后更新**: 2026-08-25
**当前状态**: `dev` 已完成五域收敛、OpenAPI 与单一接口契约整改、服务间最小权限认证、接口调用权限审批闭环和版本化计费。外部请求连接器插件化阶段 0—5 已全部实现；连接器“一个粗粒度插件 + 一份配置”的产品模型阶段 0—4 也已完成代码实现和隔离自动化验收，包括 Manifest v2/高层 SDK、V049/V050、`connectorSpec` 控制面、`generic-http:2.0.0`、Legacy 转换/清点和简化前端工作区。设计阶段 5 的逐厂商生产迁移、容量/滚动升级观察以及阶段 6 的旧高级入口最终退役仍未执行。CI/CD v2.0 的仓库合同、Workflow、Dockerfile、Helm、严格迁移、快照回执、发布门禁、不可变 OCI SemVer 别名、恢复 Runbook 和只读 GitHub 前置审计脚本已落地；OWASP/CycloneDX 供应链扫描已通过，`CVE-2025-7962` 仅对 `org.eclipse.angus:angus-activation:2.0.3` 的错误 CPE 归属做了版本精确、可审计 suppression，`NVD_API_KEY` Secret 名称已配置，需由下一次 CI 实际扫描证明可用。GitHub 单人维护者模式的分支保护和四个 Environment 已配置；ARC Runner/RBAC、GHCR、Nacos、Prometheus、签名仓库、快照 adapter、真实 staging/prod 发布和生产回滚仍未验证。上述结论不表示已经部署到生产环境。

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

## CI/CD 流水线落地（仓库实现已落地，外部环境未验证）

详细方案见 [CI/CD 流水线设计](docs/2026-08-22-ci-cd-pipeline-design.md)。
目标技术栈为 GitHub Actions、GHCR、Kubernetes、Helm、Nacos 和 Prometheus，采用一次构建、
同一 digest 依次晋级 dev、staging、production。仓库侧已经实现对应 Workflow、脚本、Chart 和
Runbook；外部平台尚未连接，因此以下“已实现”只表示代码和隔离证据，不表示生产可用。

| 阶段 | 状态 | 完成门槛 |
|---|---|---|
| 0. 仓库治理和部署基础设施 | 仓库合同已实现，GitHub 单人治理已配置 | `master` 保护、required checks、四个 GitHub Environment、隔离 Runner 和 namespace RBAC 生效 |
| 1. CI 门禁 | 已实现并隔离验证 | Java/Web、coverage ratchet、架构、V048—V050、九镜像 Docker build/smoke、依赖扫描和 Helm policy 在 GitHub required check 生效 |
| 2. 制品供应链 | 已实现，GHCR/签名仓库待连接 | 九镜像、多架构、SBOM、provenance、attestation、Manifest digest 交接和生产 OCI SemVer 别名可在 CI 实际推送并复验 |
| 3. 非生产 CD | Workflow/Chart 已实现，集群未验证 | dev 自动部署、staging 单人自审+等待窗口、acceptance、容量和失败演练留存真实证据 |
| 4. 生产 CD | Workflow/Runbook 已实现，生产未启用 | SemVer、SnapshotReceipt、SLO、真实生产发布、具体 digest 回滚和至少两次成功发布完成 |

### CI/CD 外部落地待办

以下事项不能由仓库文件自证，完成前不得把流水线标记为“生产已部署”：

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
- [x] 已在 GitHub Actions Repository Secret 配置 `NVD_API_KEY`（只记录名称，不记录值）；下一步必须重新运行 PR #5 的完整 CI，确认 OWASP Dependency-Check 使用 API Key 后在合理时间内完成并保留 SARIF/JSON 证据。
- [x] OWASP Dependency-Check/CycloneDX 供应链门禁：Spring Boot/Cloud/Alibaba 已升至兼容的 3.5.16/2025.0.3/2025.0.0.0 组合，并修复 HttpClient、HttpCore、Jackson、Commons Lang 等可用补丁；2026-08-24 扫描无未抑制 CVSS≥7 结果。`CVE-2025-7962` 对 `org.eclipse.angus:angus-activation:2.0.3` 属于 Angus Mail SMTP 的错误 CPE 归属，已用版本精确 suppression 记录 jar 内容与适用边界；依赖升级后必须重新审查，不得把 suppression 当作通用 waiver。Nacos/Prometheus/Tomcat/Kotlin/Validator 的已审计 CPE 误匹配同样仅按精确组件+CVE suppression 记录理由；OSS Index 未配置独立认证令牌，已在 Maven profile 中显式关闭并由 NVD/CodeQL/npm audit 分担门禁，不能误报为完整 OSS Index 覆盖。
- [ ] 部署并验证 PostgreSQL snapshot/PITR adapter 与 `DMH_SNAPSHOT_SIGNATURE_VERIFIER`；生产快照必须由晋级流程绑定成功 staging Deployment 时间戳，完成快照恢复到新实例、Liquibase 校验和、Secret 切换与 Helm rollback 演练。
- [ ] 在 dev→staging 完成真实 acceptance、登录态 UI、容量、Access Pod/PVC、Nacos/制品仓库故障演练；在 production 完成无流量彩排、具体 digest 回滚和至少两次成功发布，连续观察 14 天。

仓库内每次改动仍应先执行 `./mvnw -B -ntp verify`、Web 的 `npm ci/lint/test:coverage/build`、
`verify-v048-routing.sh`、`verify-v049-connector-product-spec.sh`、`verify-v050-generic-http.sh`、
`bash arch-scan.sh`、Helm lint/template/policy、`python3 ci/scripts/verify-rbac.py`、
`python3 ci/scripts/verify-admission-policy.py`、
`python3 ci/scripts/check-workflows.py`、
`python3 ci/scripts/check-workflow-matrices.py`、
`python3 ci/scripts/verify-observability-rules.py`、
`python3 ci/scripts/verify-release-gates-policy.py` 和 `git diff --check`；staging/production 的 Helm
render 必须通过当前 source SHA 的 immutable Nacos Group 覆盖参数；覆盖率基线变更还必须让
同一次真实报告满足新数值，不能只编辑 `coverage-baseline.json`。Docker 本机验证可用
`docker buildx build --load --platform linux/arm64 -f docker/web.Dockerfile ...` 并执行 `/healthz`，
本机已完成九个组件的 linux/amd64 与 linux/arm64 构建；仍需 GitHub Runner 留存双架构九镜像推送、签名/attestation 和 GHCR 回读证据。

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

## 连接器粗粒度插件与配置简化（阶段 0—4 已实现）

详细实施方案见 [连接器粗粒度插件模型与配置简化优化设计](docs/2026-08-12-connector-product-model-simplification-design.md)。
本方案保留现有六阶段运行时和治理能力，把普通产品模型收敛为“选择一个连接器插件、固定一个版本、填写一份配置”。阶段 0—4 已完成代码和隔离自动化验收；阶段 5—6 是生产迁移与退役门禁，不能因代码存在而标记完成。

| 阶段 | 状态 | 验收门槛 |
|---|---|---|
| 0. [基线与可转换性清点](docs/2026-08-12-connector-product-model-simplification-design.md#171-阶段-0基线与可转换性清点) | 已实现并通过隔离自动化验收 | 高级 API/UI 基线、只读 converter、三类 fixture 和分页 inventory 已落地；生产库实际分类仍属于阶段 5 发布准备 |
| 1. [Manifest v2 与插件开发 SDK](docs/2026-08-12-connector-product-model-simplification-design.md#172-阶段-1manifest-v2-与插件开发-sdk) | 已实现并通过模块测试 | v1/v2 双读、高层单入口 SDK、Managed Session、`platform-core`、TestKit 与真实 runtime 示例均通过 |
| 2. [Masterdata Spec 控制面与 V049](docs/2026-08-12-connector-product-model-simplification-design.md#173-阶段-2masterdata-spec-控制面与-v049) | 已实现并通过隔离数据库验收 | Spec 草稿/校验/测试/发布/历史/回滚/升级预检、确定性编译及 V049/U049 fresh/upgrade/HALT 矩阵通过 |
| 3. [Generic HTTP 2.0 与转换](docs/2026-08-12-connector-product-model-simplification-design.md#174-阶段-3generic-http-20-与转换) | 已实现并通过隔离数据库与模块测试 | `generic-http:2.0.0`、V050/U050、转换预检/CAS、Legacy inventory 和离线对等 fixture 已落地 |
| 4. [前端简化工作区](docs/2026-08-12-connector-product-model-simplification-design.md#175-阶段-4前端简化工作区) | 已实现并通过 lint/typecheck/Vitest/build | 普通页面只展示插件、固定版本、单表单、响应映射和只读计划；真实登录态浏览器工作区仍待完整 E2E |
| 5. [受控发布与逐厂商迁移](docs/2026-08-12-connector-product-model-simplification-design.md#176-阶段-5受控发布与逐厂商迁移) | 未实施 | 必须在目标环境先运行 inventory，再按厂商完成真实请求/响应/错误/缓存/计费/主备对等、容量和观察窗口 |
| 6. [旧高级写入口收口](docs/2026-08-12-connector-product-model-simplification-design.md#177-阶段-6旧高级写入口收口) | 未实施 | 阶段 5、回滚窗口和真实浏览器/API E2E 全部完成后，才可删除 raw 变更入口；raw validate 保持只读例外 |

迁移验收入口为 `verify-v049-connector-product-spec.sh` 和 `verify-v050-generic-http.sh`。两者只创建名称受限的临时 PostgreSQL 数据库，覆盖 fresh、前版本升级、重复执行、漂移/HALT 原子性、安全回滚与重新应用，并在退出时清理；这不是任何生产数据库的迁移记录。

## 仍需执行的生产发布门禁

以下是部署环境的必做检查，不是待开发功能：

- 通过环境变量或密钥系统提供 `NACOS_SERVER_ADDR`、数据库和 Redis 的连接及凭据；`docker-compose.yml` 仅用于本地开发和测试。
- 在真实集成环境显式开启外部 API 测试：`mvn test -Dintegration.tests=true` 或 `INTEGRATION_TESTS=true`。
- SkyWalking 生产环境使用持久化后端，不使用本地 compose 中的 H2 存储。
- 在生产制品库和部署 TrustStore 中配置经审批的签名公钥、仓库前缀、证书链和 Access 持久缓存卷；隔离验收使用的一次性凭据已经销毁。
- 按生产 Access 实例规模执行容量、滚动升级、制品库故障和告警联动演练，并依据生产变更流程决定上线窗口；这些是环境/发布工作，不是待开发功能。
- 在目标环境运行只读 `/vendor/config/connector-spec/inventory`，逐项确认 `LOSSLESS_CONVERTIBLE/REQUIRES_DEDICATED_PLUGIN/MUST_REMAIN_LEGACY`，不得用隔离 fixture 代替生产事实清点。
- 对目标厂商完成真实登录态工作区的选择、升级预检、保存、校验、测试、发布、历史、回滚和 Legacy 转换，并核对 CallRecord/Billing/缓存/主备副作用；当前本地浏览器验收受登录态与完整服务环境限制。
- 阶段 5 观察窗口、生产容量和回滚演练完成前，不执行阶段 6 的 raw 入口物理删除。
- 合入受保护主分支（当前 CI 监听 `master`）前执行 `./mvnw -B -ntp verify`、在 `data-platform-web` 执行 `npm ci`、`npm audit`、`npm run lint`、`npm test`、`npm run build`，并执行隔离数据库迁移回归和 `bash arch-scan.sh`。

## 文档职责

| 文档 | 维护内容 |
|---|---|
| `README.md` | 项目入口、模块边界、快速启动和已完成里程碑 |
| `CODE_WIKI.md` | 当前架构、模块职责和关键实现说明 |
| `docs/API.md` | 对外 HTTP API 契约 |
| `docs/DEPLOYMENT.md` | 本地与生产部署要求 |
| `docs/2026-08-22-ci-cd-pipeline-design.md` | CI/CD v2.0 合同、已落地仓库能力、环境前置条件、验收与回滚门禁；当前状态为仓库实现已落地、外部环境未验证 |
| `docs/runbooks/database-recovery.md` | PostgreSQL 快照/PITR 恢复、角色、验证、演练和禁止自动 rollback 的事故流程 |
| `docs/runbooks/release-deployment.md` | Manifest 晋级、Nacos/Helm/Access 发布顺序、门禁、回滚和发布证据 |
| `docs/2026-07-23-deep-cleanup-review.md` | 本轮清理范围、架构决策和回归证据 |
| `sql/MIGRATIONS.md` | V001—V050 的迁移、V049/U049、V050/U050 条件回滚与前向恢复要求 |
| `docs/2026-08-03-external-request-connector-plugin-upgrade-design.md` | Bumblebee 参考、实施前基线、现已落地架构和隔离验收边界 |
| `docs/2026-08-12-connector-product-model-simplification-design.md` | 粗粒度插件产品模型、阶段 0—4 实现证据、阶段 5—6 生产迁移与退役门禁 |
历史实施计划、已验收报告和过期性能样本不保留在当前知识库，需要追溯时请查阅 Git 历史。
