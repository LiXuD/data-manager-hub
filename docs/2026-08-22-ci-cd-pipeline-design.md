# data-manager-hub 生产部署前 CI/CD 设计蓝图

**文档日期**：2026-08-22
**状态**：当前开发阶段只启用 `.github/workflows/ci.yml` 的后端/前端编译与测试；本文保留生产部署前的 GHCR、Kubernetes、Helm、Nacos、Prometheus、快照和回滚设计，相关 Workflow 目前不自动触发
**目标平台**：生产部署前再评估 GitHub Actions、GHCR、Kubernetes、Helm、Nacos 和 Prometheus 的组合
**当前部署手册**：[DEPLOYMENT.md](DEPLOYMENT.md)（本地开发和未来生产部署说明分开维护）

> 本文不是当前开发阶段的提交门禁。它记录生产部署前可能采用的设计和已有实现蓝图；
> 没有真实集群、GHCR、生产 Secret、快照恢复和生产发布证据时，不能把仓库内容描述为生产已部署。

## 1. 审查报告吸收与本方案的判断

外部《CI-CD方案对抗式审查报告.md》指出的 P0 问题已逐项映射到代码：

| 审查问题 | v2.0 的落地位置 | 处理结论 |
|---|---|---|
| manifest 定稿与跨 Job 交接不闭合 | `build-release.yml` 的 `finalize` 输出 `manifest_digest`；部署 Job 只接受 digest | 在九个镜像、SBOM、provenance、插件回执全部完成后才定稿并推送 OCI Manifest |
| deployability 被路径过滤绕过 | `classify-changes.sh` 对任意源码变更强制 `deployability=true`；`required-ci` 汇总 | docs-only 才可跳过重任务，required check 始终有成功/失败结论 |
| Access StatefulSet 没有逐 Pod 等待 | `ci/scripts/access-rollout.sh` 使用 `partition` 递减并等待 Ready | 不依赖 StatefulSet 原生滚动的隐含语义 |
| docs-only master 提交仍可能误生成制品 | `build-release.yml` guard 重新分类 source change；非源码变更不构建镜像，相关 gate evidence 标记 `not-applicable` | 不把未执行的门禁伪造为 passed，也不产生无意义 Manifest |
| 生产快照只有输入没有创建责任 | `create-snapshot-receipt.sh` + 受保护 Runner 的 adapter 接口 | 没有 adapter 时 production Job fail-closed；人工 Snapshot ID 不是替代品 |
| 数据恢复只有一句口号 | [database-recovery.md](runbooks/database-recovery.md) | 明确角色、RPO/RTO、恢复到新实例、校验和、演练与禁止动作 |
| GHCR 清理会删掉生产 digest | `ci/policy/ghcr-retention.yaml` + `verify-ghcr-retention.py` | Release digest、生产 Build Manifest 和 SemVer OCI 别名永不删除，候选/SBOM/provenance 至少 365 天 |
| 本仓库复核发现的落地边界 | `runtime-contract.v1.yaml`、`_deploy-reusable.yml`、`manifest.py`、Helm/NetworkPolicy | 独立补齐 `production→prod` Spring profile 映射、Job 创建时一次性注入 Secret（避免 PodTemplate 不可变）、部署前重新计算 source/tree/config 摘要、Prometheus 跨 namespace 抓取边界，以及 Job 直接环境变量/ConfigMap/非预期 volume 注入的 fail-closed 限制 |

报告中关于 canary、CodeQL 增量优化和云厂商 API 的建议没有直接照搬：v1 选择零停机滚动，
CodeQL 保留源码变更时的安全门，并增加夜间完整扫描，
先用 `maxUnavailable=0`、Access 逐 Pod、15 分钟发布门禁和 60 分钟持续告警控制风险；流量型 canary 保留为
billing/governance 的后续能力。Prometheus、快照和制品仓库采用可替换 adapter，而不把某个
云厂商 SDK 硬编码进业务仓库。

## 2. 发布不变量和边界

### 2.1 必须始终成立的不变量

1. **Build once, promote by digest**：dev、staging、production 使用相同的九个 OCI digest，
   晋级不重新构建。
2. **Manifest 是唯一交接对象**：部署输入是 `sha256:<64 hex>` 的 OCI BuildManifest，不能
   传 branch、tag 或未经验证的 JSON 文件。
3. **代码、配置、迁移同一版本**：Manifest 固化 source tree、toolchain、changelog、Nacos
   bundle、镜像和插件回执摘要。
4. **数据库只前向恢复**：Liquibase 迁移可以重入，流水线不执行 rollback、restore、clear
   checksum、release lock 或 repair；应用回滚不等于数据库回滚。
5. **Secret 不进仓库、不进 Manifest、不进日志**：Helm 只保存 Secret 名称，值由集群预创建
   Secret 或外部 Secret 管理器提供。
6. **状态分层报告**：仓库实现、隔离验证、环境就绪、生产就绪分别报告，不把单元测试或
   Helm template 误报成线上发布。

### 2.2 首轮不做的事情

- 不把 `docker-compose.yml` 当生产模板；
- 不在 Chart 中安装 PostgreSQL、Redis、Kafka、Nacos、SkyWalking、Prometheus；
- 不修改业务 API、领域边界和连接器产品模型；
- 不自动创建业务 Secret；
- 不在数据库迁移失败时自动反向迁移或重建生产库；
- 不在本次仓库变更中修改 `docs/DEPLOYMENT.md`。

### 2.3 生产阶段治理（当前延期）

如果生产阶段启用本方案，本仓库由 `LiXuD` 一人维护，且项目采用 AI VibeCoding；不创建虚假
reviewer、机器人账号或“自审即独立复核”的证明。治理合同明确区分“可追责的单人发布”与“双人职责分离”：

- `master` 仍要求 `CI / required-ci` 成功，启用管理员强制执行、线性历史、会话解决，禁止
  force-push 和删除；PR 审批数为 `0`，不声称存在 CODEOWNERS 独立审批，CODEOWNERS 只保留
  责任路径元数据；
- `dev`、`staging`、`production`、`plugin-signing` Environment 已建立并限制到受保护分支；
  后三个 Environment 的 required reviewer 配置为 `LiXuD`，`prevent_self_review=false`，允许
  同一维护者完成发布确认。`production` 额外保留 1800 秒（30 分钟）wait timer，给维护者留出
  人工核对、暂停或取消窗口；
- 生产仍只能通过手工 `workflow_dispatch` 晋级，并继续强制 Manifest digest、SnapshotReceipt、
  SLO/告警、attestation、Nacos/迁移/回滚等门禁；Environment 自审不替代这些技术门禁；
- 单人模式不等价于双人复核，风险由强制 CI、不可变制品、最小权限 Runner、等待窗口、可审计
  Deployment Receipt 和可演练的恢复 Runbook 降低。未来新增独立维护者时，切回双人模式需将
  PR 审批数恢复为至少 1、启用 CODEOWNERS/过期审批失效，并将生产 Environment 设置为
  `prevent_self_review=true`。

## 3. 当前基线和已验证事实

仓库当前为五域结构：`masterdata`、`access`、`billing`、`identity`、`governance`，另有
Gateway、Web、dbops 和 acceptance 运维/验收镜像。版本合同位于：

- `ci/contracts/runtime-contract.v1.yaml`：九个组件、端口、模块、Nacos Data ID、Secret 引用、
  副本数和生产要求；
- `ci/contracts/migration-policy.v1.yaml`：PostgreSQL 16、V048—V050 迁移模式和恢复边界；
- `ci/toolchain.lock.yaml`：Java 21、Maven 3.9.15、Node 22.19.0、npm 10.9.3、Helm 3.19.0、
  kubectl 1.33.0、actionlint 1.7.12、promtool 2.54.0 及基础镜像/校验工具 digest；
- `.mvn/wrapper/maven-wrapper.properties`：Maven 分发 URL 和 SHA-256；`mvnw` 不信任 Runner 预装
  Maven，每次执行都校验缓存归档并重新解包校验通过的 3.9.15 分发包，避免持久 Runner 上被篡改的
  同版本二进制绕过工具链锁；
- `ci/policy/coverage-baseline.json`：12 个 Java/Web 模块的 line/branch 初始基线，下降超过
  0.5 个百分点失败；修改该文件会强制同时运行后端和前端覆盖率，并要求新数字不高于同一运行
  的真实报告，避免只抬高 JSON 数字绕过门禁；
- `ci/policy/ghcr-retention.yaml`：制品保留合同。

以下是此前完成的仓库/隔离验证记录，保留作生产部署前参考；它们不属于当前开发阶段提交门禁，
也不表示外部平台或生产环境已经启用：

| 验证 | 结果 | 说明 |
|---|---|---|
| `./mvnw -B -ntp verify` | 通过 | 28 个 Reactor 项目；635 个后端测试、0 failure、0 error、2 skipped；本次隔离验证使用 Java 21.0.7，既有设计 skip 保持不变 |
| Web `npm ci`、lint、69 个 Vitest、coverage、build | 通过 | CI 锁定 Node 22.19.0/npm 10.9.3，并在 Workflow 中逐项校验版本；本次本机隔离复核使用 Node 24.19.0/npm 11.16.0（只作为兼容性验证，不替代 CI 锁定工具链）；V8 coverage 已写入基线 |
| OWASP Dependency-Check + CycloneDX/许可证 profile | 通过（真实扫描无未抑制 CVSS≥7 结果） | 2026-08-24 Java 21.0.7 扫描生成 SARIF/JSON；Spring Boot/Cloud/Alibaba 已升级到兼容的 3.5.16/2025.0.3/2025.0.0.0 组合，并锁定 HttpClient 5.6.4、HttpCore 5.4.3、Jackson 2.18.9、Commons Lang 3.18.0、Nacos logback adapter 1.1.5。`CVE-2025-7962` 对 `org.eclipse.angus:angus-activation:2.0.3` 属于 Angus Mail SMTP 的错误 CPE 归属，已按精确包坐标和版本 suppression，并在 jar 内容中确认没有 SMTP/mail 实现；该 suppression 不是通用 waiver，依赖升级后必须重新审查。Nacos/Prometheus/Tomcat/Kotlin/Validator 的 CPE 误匹配仅按精确组件+CVE suppression 记录理由；OSS Index 因未配置独立认证令牌被显式关闭，NVD/CodeQL/npm audit 仍是阻断门禁 |
| `python3 -m unittest discover -s ci/tests -v` | 通过 | 64 个 CI 合同回归测试，覆盖 manifest、OCI 引用、不可变 Manifest tag、OCI SemVer 别名幂等/漂移拒绝、source/security 分类、docs-only build guard、受保护插件签名 Job、插件签名回执、镜像证据严格白名单、Workflow YAML/shell（含重复 YAML key 拒绝）、CI/Release 九镜像矩阵与 RuntimeContract 一致性、禁止 `pull_request_target` 和 PR 调度 self-hosted、Maven wrapper 与完整运行时工具链锁定、覆盖率基线/非有限值与未来 waiver 篡改防护、CODEOWNERS 关键路径保护、release-gates policy 防阈值放宽、namespace RBAC/admission policy（含 namespace/host namespace boundary）、显式 Prometheus 跨 namespace NetworkPolicy、严格迁移拒绝无 Liquibase 历史的数据库、集群 preflight（实际 Runner 身份、命名 Secret 读取及 Secret 列表/变更拒绝）、Nacos plan/apply/verify/漂移、Prometheus baseline/gate、gate sample 的环境/source SHA/Manifest 绑定、未知 gate 字段和非法 JSON 拒绝、持续告警规则、live image digest、Access 逐 Pod、初始 partition 防并行滚动与 connector readiness 配置绑定、Helm policy、内部认证 Secret、私有 Job（只允许 dbops/acceptance digest 且按镜像限制 entrypoint）、acceptance 离线依赖闭包、快照创建/恢复回执和 recovery position 类型、Job Failed 快速失败、非 dev Nacos loopback fail-closed 和 release gate、发布权限分层、夜间 CodeQL、SemVer 单调性、GHCR retention 保护规则、基础镜像双架构检查和新迁移 changelog 引用防漏、全零 push base fail-closed、Markdown 相对链接和本地锚点、Snapshot verifier 仅限受保护 Runner、GitHub 分支保护/CODEOWNERS/Environment 审批/Secret 名称/在线 Runner 外部状态审计 |
| 本机 OCI Registry 2 Build Manifest/alias 集成 | 通过 | 真实 registry 实测 canonical Manifest push、同 tag 同内容复用、同 tag 内容篡改拒绝，以及 ORAS digest→SemVer tag 保持同一 descriptor；临时 registry 已清理，不代表 GHCR 权限/attestation 已验证 |
| `arch-scan.sh`、契约、Workflow YAML/shell、Markdown、Action pin、release-gates policy 和 Prometheus rule 检查 | 通过（历史记录） | 此前 6 个 Workflow、84 个 `run` 块由 `check-workflows.py` 解析并经 `bash -n`；`check-markdown-links.py` 校验 23 个 Markdown 文件的相对链接和本地锚点；这些高级检查当前不属于开发阶段 required CI |
| 本机 Docker PostgreSQL 16 严格迁移集成 | 通过 | 临时 PostgreSQL 16 容器执行 `preflight`、首次 `update`、重复 `update`、`status`；人为持有 `DATABASECHANGELOGLOCK` 后 `preflight` 以非零退出并拒绝自动清锁；容器和数据库自动清理，未触碰项目现有数据库 |
| `verify-v048-routing.sh` | 通过 | fresh、upgrade、repeat、duplicate、ambiguous、rollback 矩阵；隔离数据库自动清理 |
| `verify-v049-connector-product-spec.sh` | 通过 | fresh、upgrade、repeat、HALT、U049/reapply 矩阵 |
| `verify-v050-generic-http.sh` | 通过 | fresh、upgrade、repeat、drift/HALT、U050/reapply 矩阵；隔离数据库自动清理 |
| Helm lint/template/policy | 通过 | dev、staging、production 三套 values；staging/production 使用全零 SHA 作为静态 source-SHA 占位；无集群连接 |
| 本机 kind Kubernetes 1.31 集成 | 通过 | 真实 API 应用 dev/staging RBAC 与 ValidatingAdmissionPolicy；策略 type-check 延迟后，`dmh-runtime` Job 实际执行成功，只有 dbops/acceptance digest 可创建且 dbops/acceptance entrypoint 参数按镜像绑定，command 覆盖、直接 `NACOS_SERVER_ADDR`/ConfigMap 环境注入、ConfigMap/下行 API/持久卷挂载、dbops 可写 root、acceptance 只读 root、非 data-manager-hub 镜像、缺少 `dmh-ghcr-pull`、hostPath、CSI volume 和 Secret 注入的 Job 均被拒绝；Runner preflight 现在要求 kubeconfig 实际身份为 `system:serviceaccount:<namespace>:dmh-deployer`，先以 server dry-run 验证 entrypoint boundary，再拒绝 Secret list/watch/create/update/patch/delete；`list secrets=no`/命名 Secret `get=yes` 已实测；临时 cluster 已删除，不代表目标集群已连接 |
| `NACOS_MODE=plan`（dev/staging/prod） | 通过 | 每个环境渲染 7 个 Data ID；plan 不含内部 `__PROJECT_ROOT__`/生成密钥占位符且不写入 Nacos，prod 只保留明确的环境变量占位符；dev 本地密钥仍只写 `.runtime` |
| `publish-nacos-config.sh` apply/verify 合同桩 | 通过 | 临时 Nacos HTTP API 桩验证 7 个 Data ID 的首次发布、幂等重跑、verify 和内容漂移 fail-closed；未连接真实 Nacos |
| 本机 Docker Nacos 2.3.2 集成 | 通过 | 临时 namespace 实测 `plan → apply → 幂等 apply → verify`，直接篡改一个 Data ID 后 verify 返回非零并拒绝漂移；测试 namespace、配置和容器已清理，不代表共享/生产 Nacos |
| Docker build/runtime | 部分通过 | 本机 Docker Desktop 已完成九个组件的 linux/amd64 和 linux/arm64 `--load` 构建；CI 在九个镜像逐一构建前以 `check-base-image-platforms.py` 实际检查锁定 digest 同时包含两种平台，并在 required CI 阶段验证每个 Dockerfile、构建上下文和入口层；Web 已在本机容器通过 `/healthz` 和首页检查，全部 Java/dbops/acceptance 镜像确认 UID 10001。dbops 镜像在 read-only root、无 Maven 网络的临时 PostgreSQL 16 环境中完成 preflight→首次迁移→重复迁移→status（28 changesets，重复执行 0）；acceptance 镜像在无网络模式下用预取依赖完成 `-DskipITs=true` 的 Maven 编译/离线生命周期预检，真实 Failsafe 验收仍需 staging/production 服务、登录凭据和数据断言。GHCR 推送、CI Runner 多架构矩阵、镜像签名/attestation 和九镜像制品全量回读仍需 GitHub Runner 证据 |
| 开发阶段本地运行闭环 | 通过（阶段性成果） | Mac Docker 运行 PostgreSQL 16、Redis 7、Kafka 3.7、Nacos 2.3.2，端口仅绑定 loopback；Mac 本地启动六个后端服务和 Vite 前端，六个 `/actuator/health` 均为 `UP`，前端首页返回 200、代理未认证请求按预期返回 401；本地数据库 V001—V050 已迁移并通过 validate。该证据只覆盖开发运行态，不代表非生产或生产 CD 已部署 |
| GitHub/GHCR/Kubernetes/Nacos/Prometheus/生产回滚 | 未验证 | GitHub 基础治理和 NVD Secret 已配置，PR #5 的 hosted CI run `32806667253` 已完成安全门；仍需要在线 Runner、GHCR 推送/回读、Secret、集群和真实流量 |
| 外部平台状态审计 | 部分就绪 | `master` 已要求 `CI / required-ci`，单人模式审批数为 0；`dev`、`staging`、`production`、`plugin-signing` Environment 已配置分支策略和 `LiXuD` 自审（production wait timer 1800 秒）；当前分支的 CODEOWNERS 尚未合并到 `master`，在线 Runner 数为 0，GHCR Build Manifest、kubectl context、Nacos/Prometheus/快照平台仍未验证 |

## 4. 当前开发阶段 CI

当前提交级 CI 只负责验证代码能否编译、测试和构建：

| 文件 | 触发 | 责任 | 受信边界 |
|---|---|---|---|
| `.github/workflows/ci.yml` | PR、`dev`/`master` push、手工 | Java 编译/单元测试、Web `npm ci/lint/test/build`、`CI / required-ci` | GitHub 托管 Runner；不访问部署 Runner、生产 Secret 或外部环境 |

以下 Workflow 和脚本是生产部署前蓝图，当前不由开发 CI 触发：

| 文件/能力 | 当前状态 |
|---|---|
| `build-release.yml`、`connector-plugin-supply-chain.yml`、`scheduled-security.yml` | 保留为手工/未来阶段使用 |
| `_deploy-reusable.yml`、`promote-staging.yml`、`promote-production.yml` | 保留生产部署前设计，当前不自动晋级 |
| Docker、GHCR/OCI、签名、Helm/Kubernetes、Nacos、快照和回滚 | 延期到生产部署前 |

当前 CI 使用的第三方 Action 均锁定完整 commit SHA，默认只申请 `contents: read`；生产阶段重新启用
构建、制品或 attestation 时，再按对应 Job 的最小权限要求开启额外权限。

生产阶段重新启用上述能力时，需恢复对应的合同、矩阵和环境验证；当前开发阶段不以制品或部署
矩阵作为提交成功条件。

以下 4.2—4.5 以及后续章节只保留生产部署前的设计细节，当前不由 `ci.yml` 执行。

### 4.2 变更分类与跨文件防绕过

`ci/scripts/classify-changes.sh` 不使用 Workflow `paths:` 过滤，先比较完整 Git 历史，再输出
`backend/frontend/migration/security/deployability/plugin/external_plugin/docs/full/source_change`。
`plugin` 表示宿主插件运行时安全范围，`external_plugin` 才表示必须提供外部签名 receipt。只要有源码、
POM、package lock、配置、Docker、Chart、CI 或迁移变化，deployability 门必须运行；Java/Web 源码
和依赖清单还必须触发 security 门，POM/package lock 变化会额外执行依赖安全扫描。不相关重任务通过
`required-ci` 以 `success` 结论收敛，不能留下 GitHub required check 的 `skipped/neutral` pending。
`source_change=true` 但 deployability 不是 success 时，汇总 Job 直接失败。
CI 将分类结果以绑定 source SHA 和 push base 的 `ci-classification-<run_id>` artifact 保存；Build Release
只消费上游成功 `CI / required-ci` 对应的这一份结果，并用同一 base 计算迁移模式，避免一次 push 包含
“源码提交 + 文档提交”时只比较最后一个父提交而错误跳过制品或把门禁标成 `not-applicable`。
分类脚本同时把规范化后的 `base_ref`/`source_sha` 写入 Job outputs 和 artifact；覆盖率、策略与迁移
门禁统一消费该值。首个分支 push 的全零 `github.event.before` 会回退到可验证的 `HEAD^`，不能让
某个 Job 因事件元数据特殊而绕过或误判变更范围；若仓库确实没有父提交，脚本直接 fail-closed，
不会把无效的全零 SHA 当成 Git 对象或把变更误判为空。

### 4.3 后端、Web、覆盖率和策略文件

后端：

```bash
./mvnw -B -ntp verify
python3 ci/scripts/check-coverage.py --scope backend --enforce-changed-baseline --base-ref "$BASE_REF"
bash arch-scan.sh
```

输出 Surefire/Failsafe、JaCoCo XML 和 Reactor 摘要。Web：

```bash
test "$(node --version)" = v22.19.0
test "$(npm --version)" = 10.9.3
npm ci
npm run lint
npm run test:coverage
python3 ci/scripts/check-coverage.py --scope frontend --enforce-changed-baseline --base-ref "$BASE_REF"
npm run build
```

`CI / docker` 在 deployability 变更时按矩阵无推送构建全部九个组件
（`gateway`、`masterdata`、`access`、`billing`、`identity`、`governance`、`web`、`dbops`、
`acceptance`，`linux/amd64`、`--load`），用真实 BuildKit 解析每个 `COPY`、依赖安装和入口层；
Web 还执行 `/healthz`/首页 smoke，acceptance 执行离线依赖闭包预检。合并到 master 后
`Build Release` 再把九个组件以 `linux/amd64,linux/arm64` 推送 GHCR。这样每个 Dockerfile 的
路径/目录错误会在 required check 阶段暴露，而不是等到生产晋级。

`check-coverage.py` 同时读取 JaCoCo 和 V8 summary；每个生产模块必须有基线，line/branch 任一
下降超过 0.5pp 失败，且基线/报告中的 line、branch 必须是 0—100 的有限数值，`NaN`/`Infinity`
会 fail-closed。覆盖率基线文件一旦出现在本次变更中，分类器会强制后端和前端 Job，
`--enforce-changed-baseline` 再要求每个新声明值不超过同一次真实报告；因此不能靠编辑 JSON
抬高门槛。`verify-policy-files.py` 再比较当前基线与 base ref，禁止放宽阈值、删除
已有模块或降低数字；`CODEOWNERS` 保护基线、waiver、Workflow、Maven Wrapper/root POM、Nacos 配置、迁移和部署文件，
并由 `verify-policy-files.py` 在 required CI 中检查关键路径仍有 owner。waiver 必须
有 ID、负责人、原因、创建日期和不超过 30 天的到期日，过期即失败。

### 4.4 迁移门禁

PR/Push CI 执行：

```bash
IMMUTABLE_FROM_VERSION=51 bash ci/scripts/check-migration-immutability.sh
bash ci/scripts/strict-migration.sh preflight
bash ci/scripts/strict-migration.sh update
bash ci/scripts/strict-migration.sh update
bash ci/scripts/strict-migration.sh status
```

迁移 Job 在同一 PostgreSQL 16 隔离服务上继续执行 `verify-v048-routing.sh`、
`verify-v049-connector-product-spec.sh` 和 `verify-v050-generic-http.sh` 全量 fresh/upgrade/repeat/
HALT/drift/reapply 矩阵；因此 V048—V050 的真实回归不再只依赖夜间任务，迁移或全量变更会直接阻断
required CI。矩阵脚本只创建名称受限的临时数据库并在退出时清理，不接触项目现有数据库。

`check-migration-immutability.sh` 使用 NUL 分隔的 Git diff，检测已保护迁移/rollback 的 M/D/R/C，
并用 XML 解析比较已有 changelog changeset body（允许只追加新 changeset）；`check-changelog-references.py`
还要求新加入的 `V*.sql/U*.sql` 文件必须被至少一个 XML changelog 的 `sqlFile/include` 引用，且新
`V051+` migration 必须存在对应 `U<编号>__*.sql` 并由同一 changeset 的显式 `<rollback>` 引用，防止“文件存在但未进入 Liquibase 执行图”或只写了正向 SQL 却没有恢复合同；
`migrate-db.sh check-numbering` 保留历史 V007 例外。V001—V050 是现有
仓库基线，V051 起新迁移必须追加独立 changeset、编号和显式 `<rollback>`；即使 rollback 脚本只
负责拒绝原地逆迁移，也要在 changeset/Runbook 中说明生产事故采用新实例恢复或前向修复的边界；
“支持前向恢复”不能替代 Liquibase rollback 合同。发布阶段通过 `detect-migration-mode.sh` 把本次
提交固化为 `NONE` 或 `FORWARD`；检测到修改已发布文件为 `BLOCKED`，不会生成可晋级 Manifest。

`strict-migration.sh` 的边界是明确的：只调用 `validate/status/update/updateSQL`，不调用
`migrate-db.sh update` 的历史修复逻辑；preflight 还会只读确认应用表不能脱离
`DATABASECHANGELOG` 存在，并拒绝缺失或已持有的 `DATABASECHANGELOGLOCK`；数据库连接只做最多三次指数退避重试，Liquibase lock 或
迁移本身不自动重试、不自动清 lock。一次 Job 失败后由 DBA 判断状态，禁止 Actions 自动 repair、
restore 或 reverse migration。部署创建的 migration/Nacos/acceptance Job 统一设置
`backoffLimit=0`，避免 Kubernetes 默认重试把一次失败误变成重复迁移或重复验收；migration/Nacos Job
使用 `readOnlyRootFilesystem=true`，仅把 `/tmp`、`/workspace/target` 和 `/workspace/.runtime` 挂载为
`emptyDir`，让 Maven 报告、Liquibase 临时文件和 dev Nacos 临时密钥有明确的可写边界。
acceptance 镜像例外：它在 Job 内执行 Maven/Failsafe 多模块生命周期，会写入多个 module-local
`target` 目录，因此只允许受信、digest 固定的 acceptance 镜像使用 UID 10001 的临时可写容器层；
它仍关闭 token automount、禁止特权/提权、使用 `dmh-runtime,dmh-acceptance` Secret，Job 结束后不保留
任何写入。dbops 镜像在 build stage 预取 Maven/Liquibase 依赖，Job 启动时把 seed 的内容复制到
`/tmp/maven-repo`（不能把 seed 目录再嵌套一层），并通过 `MAVEN_OFFLINE=true` 让
`strict-migration.sh` 使用 Maven offline mode；CI 迁移 Job 保持在线解析能力以应对冷缓存。
dbops 缺少制品会在访问数据库前 fail-closed，不会在迁移过程中访问公共 Maven mirror，也不会回写
`/home/dmh/.m2`。

acceptance 的离线边界同样是可执行合同：`docker/acceptance.Dockerfile` 除了执行
`dependency:go-offline` 和 `dependency:resolve-plugins`，还显式预取 Maven 3.5.4/3.2.5
两套 Surefire JUnit Platform provider 及当前 JUnit Platform launcher；否则 Surefire 的动态
provider 选择会把依赖留到 Job 运行时，离线模式会在第一个上游模块才失败。复制 seed 前删除
`_remote.repositories` 与 `*.lastUpdated` 元数据，避免构建器使用的私有 mirror ID 污染运行层的
离线解析。根 `pom.xml` 的 `acceptance-skip-unit-tests` profile 只跳过 Surefire，不跳过
Failsafe；因此 acceptance Job 默认执行 `integration.tests=true` 的真实验收测试，且不会运行依赖
仓库根目录的单测。构建阶段使用 `-DskipITs=true` 仅做依赖/编译预检，运行层入口固定 `-o`；
没有 `GATEWAY_URL`、测试账号和真实集群依赖时，验收 Job 必须失败，不能降级为“无测试成功”。

### 4.5 安全门禁

- Maven `connector-supply-chain-scan` profile 执行 OWASP Dependency-Check、CycloneDX/许可证报告；
- 安全 Job 先用 `ci/scripts/verify-nvd-api.sh` 以不泄露值的方式验证 `NVD_API_KEY`，再执行扫描；NVD 数据目录显式缓存，扫描成功后保存缓存，避免后续 PR 重复冷启动；
- CodeQL Java + JavaScript/TypeScript；
- CodeQL Java 构建使用仓库锁定的 `./mvnw`，不调用会改写 wrapper 分发版本的自动构建器；
- PR/Push 的安全门随源码变更执行，夜间 `scheduled-security.yml` 再执行完整 Java + JavaScript/TypeScript CodeQL 扫描；
- npm lockfile 使用 `npm ci`，高危/严重依赖在安全 Job 失败；
- tracked source 的私钥、云密钥和 GitHub token pattern guard；
- SBOM、provenance、attestation 与 Manifest 一起上传；
- `verify-release-gates-policy.py` 固定 baseline/acute/blocking/enhanced 窗口、99.9% SLO 和错误预算阈值，拒绝通过修改 `release-gates.yaml` 放宽 traffic、5xx、p95 或 restart 门槛；
- `security-waivers.yaml` 与 coverage waiver 走同一到期校验；
- Dependabot 更新 Maven、npm、Actions 和 Docker 基础镜像，所有修改仍需 required CI。

## 5. 构建、插件和不可变 Manifest

### 5.1 九个镜像

`build-release.yml` matrix 固定构建：gateway、masterdata、access、billing、identity、governance、
web、dbops、acceptance。Java 镜像用 Maven 3.9.15 + Temurin 21 builder，运行层 Temurin 21 JRE；
Web 用 Node 22.19.0 builder + nginx-unprivileged。每个镜像：

- `linux/amd64,linux/arm64`；
- 基础镜像完整 digest；
- 非 root、drop ALL capabilities、无凭据；常驻 Java/Web 服务使用只读根文件系统（需要写入的 Access
  插件缓存使用 PVC），acceptance 仅在受信 digest 的一次性 Job 中获得临时可写容器层；
- Buildx GHA cache，避免多架构构建每次从零开始；
- 镜像 digest、从该 digest 直接生成的 CycloneDX SBOM OCI Artifact、provenance 进入单个证据文件；
  SBOM 不读取可变的 `sha-<sourceSha>` tag，避免并发重跑发生 TOCTOU。

基础镜像 digest 必须指向同时包含 `linux/amd64` 与 `linux/arm64` 的 OCI index，而不是某一个
架构的单 manifest。升级基础镜像时先执行 `docker buildx imagetools inspect <reference>@<digest>`
确认两个平台，再同步 `ci/toolchain.lock.yaml`、Dockerfile 的 `FROM` 默认值和 Workflow 服务镜像；
任一处不一致都由 `verify-toolchain-lock.py`/`check-dockerfiles.py` 阻断，不能让构建器静默回退到
错误架构。

dbops 镜像明确安装 `curl/jq/openssl/postgresql-client`，在构建阶段预取并固化 Maven/Liquibase 依赖，
运行阶段通过 `/tmp/maven-repo` + offline mode 执行严格迁移，包含严格迁移、Nacos 发布和回执校验工具；
acceptance 镜像只作为 Job，不是常驻服务。其运行层仍保留测试源码和多模块 target 目录，使用
UID/GID 10001 的临时可写容器层；`MAVEN_CONFIG=/tmp/maven` 与 `/opt/maven-repo` 让 Maven
不回写用户 home。发布前至少执行一次下列本地离线预检，确认没有冷缓存或 mirror 元数据依赖：

```bash
docker run --rm --platform linux/arm64 --entrypoint mvn dmh-local-acceptance:ci \
  -B -ntp -o -pl data-platform-test/data-platform-test-service -am \
  -Dintegration.tests=true -Dacceptance.skip.unit.tests=true -DskipITs=true verify
```

该预检只证明依赖闭包、编译和离线生命周期可运行；真实 acceptance 仍必须在 staging/production
目标集群通过 Failsafe、服务健康、数据断言和 SLO gate，不能把预检当作环境验收。

### 5.2 Manifest 定稿和防 TOCTOU

`finalize` Job 在收齐恰好九份 image evidence 后才执行：

1. `collect-image-evidence.py` 检查恰好九个组件、`ghcr.io/lixud/data-manager-hub-<component>@sha256:<64 hex>` 及对应 SBOM/provenance 仓库前缀、digest 引用格式和 `reference/sbom/provenance` 严格字段白名单，拒绝把 Runner 本地额外字段带入 Manifest；`manifest.py` 在部署前再次执行同一仓库前缀约束，并对 metadata/spec/migration/image/verification/required-ci 执行版本化字段白名单校验，只允许五类门禁状态和 required-ci 回执；
2. `hash-tree.py` 对 Git tracked source、`sql/changelog`、`nacos-config` 和 toolchain lock 做确定性 SHA-256；
3. `detect-migration-mode.sh` 计算迁移模式；
4. `collect-plugin-receipts.py` 验证外部签名插件的坐标、JAR digest、签名 digest 和指纹；
5. `build-release` 先用 `gh api` 回读触发它的 CI run，要求实际存在且成功的 `CI / required-ci`，
   再固定到该 source SHA 最早的成功 required-ci run 并写入 `spec.verification.requiredCi`，避免只相信
   `workflow_run.conclusion`，也让同一 source 的发布重跑保持 Manifest 字节级幂等；
6. `manifest.py create` 生成 canonical JSON；
7. ORAS 以 `application/vnd.dmh.build-manifest.v1+json` 推送 OCI Artifact；
8. 对 Manifest 自身生成 provenance attestation，并把 `manifest_digest` 作为 Job output 传给 dev。

Build Release 对 `sha-<sourceSha>` Manifest tag 采用 create-or-reuse 语义：tag 已存在时必须回读
OCI layer digest 并与当前 canonical 文件完全一致，否则直接失败，禁止 rerun 或并发任务重指向
同一 source SHA 的 Manifest。Manifest 的 `runId` 和 `runAttempt` 固定为 source SHA 最早成功的
required-ci run 及其真实 attempt，`generatedAt` 固定为 Git commit 时间；构建内容（镜像、SBOM、插件回执）若重跑产生
不同 digest，则按不可重现发布失败，而不是偷偷改写已有 tag。Nacos、migration、acceptance Job 名称
同时带 `GITHUB_RUN_ID-GITHUB_RUN_ATTEMPT`，避免工作流重跑复用未清理的 Job 名称。

`spec.verification.requiredCi` 只允许 `runId`、`runAttempt`、`conclusion` 三个字段；部署验证同时拒绝
未知 gate 或回执字段，避免 Runner 本地数据进入可晋级 Manifest。部署 Job 在 checkout 前先校验
environment、Runner Group、40 位 source SHA、Manifest digest 和
production SemVer 输入，避免 workflow-call 参数把任意 ref 或 Runner 标签带入受保护步骤；随后从 digest 拉取 Manifest，重新计算 canonical digest，并要求
`spec.verification.requiredCi.conclusion=success`，逐镜像回读 OCI descriptor，确认远端 digest 与 Manifest
完全相等，再执行 `gh attestation verify` 并回读对应 SBOM descriptor，
再将 Manifest 中的 image ref 注入 Helm。Helm rollout 后 `verify-live-images.sh` 从 Deployment/StatefulSet
实际 Pod template 读取 image digest，防止 tag 或中间变量造成 TOCTOU。

### 5.3 插件发布边界

签名插件不是普通 GitHub artifact。`build-release` 在检测到 `external_plugin=true` 时才调度受保护的
`plugin-signing` Environment/Runner，并要求环境 Secret `DMH_PLUGIN_SIGNING_ADAPTER` 指向一个绝对路径
可执行的编排 adapter。该 adapter 在受保护环境内逐个调用 `ci/scripts/sign-and-publish-plugin.sh`，
私钥只在该 Job/KMS，Actions 不读取私钥或 Nexus 凭据；adapter 必须把远端回读后的 receipt 写入
临时 `plugin-receipts/`，再由 Workflow 上传到 finalize Job。
上传前必须回读 JAR SHA-256、detached signature SHA-256 和 fingerprint，写入 `plugin-receipts/*.json`；
脚本配置 `NEXUS_URL` 时由受保护凭据上传并下载回读，使用其它仓库 adapter 时必须提供非空的
`PLUGIN_REPOSITORY` 和受保护的 `PLUGIN_REMOTE_VERIFY_COMMAND`，由 verifier 返回远端 JAR/签名
digest；任何远端回读不一致都会 fail-closed。签名过程使用 SHA-256 摘要，避免把完整 JAR
作为 RSA 原文输入；
插件 receipt 缺字段或 digest 不合法时 Manifest 创建失败；当变更分类为
`external_plugin=true`（`plugins/`、`plugin-artifacts/`、`plugin-receipts/`、JAR 或 detached
signature 变更）而没有至少一份签名 receipt 时，`build-release` 也会在 Manifest 创建前
fail-closed，不能用空插件列表绕过签名门禁。宿主侧 `data-platform-common-runtime`、Access、
Masterdata 和 SPI/testkit 的代码变更仍会触发 `plugin=true` 的安全检查，但不会凭空要求一个
外部插件 receipt；内置 `generic-http:2.0.0` 仍由 V050 静态事实验证，不把它伪装成外部上传插件。

## 6. Helm/Kubernetes/Nacos

### 6.1 Chart 和命名空间

Chart 位于 `deploy/helm/data-manager-hub`，dev/staging/prod values 分开。非生产集群使用
`dmh-dev`、`dmh-staging`，生产使用 `dmh-prod`。Chart 管理：

- 六个 Java 服务和 Web Deployment；
- Access StatefulSet、headless Service、每 Pod 独立 `connector-plugin-cache` PVC；
- Service、PDB、ServiceAccount、release ConfigMap、NetworkPolicy；
- startup/liveness/readiness probes 和 requests/limits；
- values 仅包含 `existingSecrets` 名称，绝不写密码、JWT、私钥或 token。
- Chart 使用由 `deploy/rbac/overlays/*` 预置的 `dmh-deployer`（发布 Runner）和
  `dmh-runtime`（业务 Pod）ServiceAccount；受保护 ARC Runner 使用 `dmh-deployer` token 访问所属
  namespace，业务 Pod 使用 `dmh-runtime` 且不自动挂载 token。Helm 通过 `lookup` 发现平台已
  预置的身份，避免第一次 preflight 前由 Helm 创建 Runner 身份；若本地安装未预置，`keep` 策略只作
  兼容性兜底。业务 Pod 不复用 Runner 的 RBAC 身份，Runner 的 Role/RoleBinding 不随应用 Helm
  release 隐式提升，统一由目标 namespace 的 RBAC overlay 管理。

`check-helm-policy.py` 对 dev/staging/prod render 结果执行：镜像必须来自
`ghcr.io/lixud/data-manager-hub-*` 且使用 `@sha256`、Pod 必须使用 UID/GID
10001、RuntimeDefault seccomp，容器必须 non-privileged、禁止 privilege escalation、只读根文件系统并
drop ALL capabilities，必须 probes/resources、禁止 hostPath 和疑似明文 Secret。NetworkPolicy 默认只允许
同 namespace、指定 ingress namespace、指定依赖 namespace、DNS 和私有网段；平台部署时必须把
`dmh-infra`/Ingress 标签与真实网络 CIDR 对齐。staging/production 的 Helm render 还必须包含
`DMH_STAGING_<40 位 master SHA>`/`DMH_PROD_<40 位 master SHA>`；Chart helper 和 policy script
都会拒绝 `*_LOCAL` 或其它可变 Group，CI 的静态 render 只使用全零 SHA 作为占位输入，实际部署由
Manifest 的 source SHA 覆盖。

### 6.2 Nacos 不可变 Group

`publish-nacos-config.sh` 支持 `NACOS_MODE=plan|apply|verify`：

- `plan` 只渲染并打印 Data ID/digest，不写 Nacos，也不要求可达的 `NACOS_SERVER_ADDR`；
- `NACOS_CONFIG_DRY_RUN=true` 是离线渲染兼容入口，即使 `NACOS_MODE=apply` 也只打印摘要、不创建
  namespace；它不能替代真正的 `apply`，正式 Job 必须关闭该开关；
- `apply` 只允许创建不存在的 `NACOS_GROUP`，已存在且内容完全相同则幂等返回，内容不同直接失败；
- 同一 Group 的 Data ID 不允许部分存在：发现半发布状态直接停止，不能把失败重试变成追加写；
- `verify` 只读校验 namespace 和所有 Data ID，不存在或 digest 不一致失败，绝不会创建 namespace。
- `NACOS_SERVER_ADDR` 的 `https://`/`http://` scheme 与 `NACOS_SCHEME` 必须一致；显式 HTTPS 地址
  不能被环境变量降级为明文 HTTP。

staging/prod 的 `NACOS_GROUP` 必须分别匹配 `DMH_STAGING_<40 位 master SHA>` 或
`DMH_PROD_<40 位 master SHA>`；脚本拒绝 `DEFAULT_GROUP` 和任意可变组，避免手工命令绕过不可变配置交接。

仓库目前维护 `dev` 与 `prod` 两套非秘密配置；`staging` 使用 prod-safe 内容作为来源，脚本
在发布时把 Data ID 后缀从 `-prod` 映射为 `-staging`，因此应用的 `SPRING_PROFILES_ACTIVE=staging`
仍然加载明确的 staging Data ID，不会误读 prod Group。

逻辑环境名与 Spring profile 有显式映射：`dev→dev`、`staging→staging`、`production→prod`。
因此生产 Deployment 使用 `SPRING_PROFILES_ACTIVE=prod`、Nacos namespace `prod` 和 `-prod.yml` Data ID，
而 GitHub/Kubernetes 仍以 `production` 作为审批与命名空间逻辑环境。

部署使用 `DMH_<ENV>_<SOURCE_SHA>` Group，Helm `global.nacosGroup` 写进 release ConfigMap 和
revision；reusable deploy 会按 plan→apply→verify 顺序创建三个一次性 dbops Job：plan 离线且
不访问 Nacos，apply 写入配置，verify 只读复核 7 个 Data ID。三个 Job 都使用 `backoffLimit=0`、
`dmh-runtime` ServiceAccount 和带 `GITHUB_RUN_ID-GITHUB_RUN_ATTEMPT` 的唯一名称；旧 Group 在
回滚窗口内保留，不能清理后再声称可回滚。Secret 仍由 Kubernetes Secret 提供，Nacos bundle
只存非秘密配置。

### 6.3 Access 逐 Pod 滚动

原生 StatefulSet 不保证自定义 `connectorRuntimeReadiness` 逐 Pod 等待，因此发布流程显式：

1. 读取副本数，设置 `partition=replicas-1`；
2. 等待最高 ordinal Pod 的 `controller-revision-hash` 等于 StatefulSet `updateRevision` 且 Ready，并可执行受保护 `ACCESS_SMOKE_COMMAND`；
3. partition 递减一个 ordinal；
4. 重复直到 partition=0；
5. 任一 Pod readiness/插件同步失败，停止并进入 Helm 回滚步骤。

Chart 的静态 Helm policy 同时要求 Access 在进入发布前就以 `partition=replicas-1` 渲染；这避免
Helm 更新镜像时先以 `partition=0` 启动原生并行滚动，之后工作流再 patch 已经太晚。发布完成后
脚本才把 partition 留在 `0`，供后续普通 StatefulSet reconciliation 使用。

其他 Deployment 使用 `maxUnavailable=0,maxSurge=1,minReadySeconds=60`；production/staging 默认 2
副本，PDB `minAvailable=1`。零停机滚动是 v1 选择，billing/governance 的 canary 作为后续能力，
不能在没有流量分配与回滚证据时伪造“金丝雀”。

## 7. 数据库迁移、快照和恢复

### 7.1 CD 顺序

dev/staging 不创建 production SnapshotReceipt；production 部署固定顺序（并在进入 Helm 前创建和验证
SnapshotReceipt）：

```text
拉取并验证 Manifest
  -> verify 所有 attestation
  -> production 创建/验证 SnapshotReceipt
  -> dbops Nacos immutable group
  -> dbops strict migration Job
  -> Helm render/lint/policy
  -> Helm upgrade
  -> Access partition rollout
  -> live digest + acceptance + SLO gate
```

数据库迁移成功后应用失败，只回滚应用/Nacos 到兼容 schema 的上一 revision；不执行数据库 reverse。
迁移失败或状态不确定则停止发布、保留 lock/日志，由 DBA 按 Runbook 选择前向修复或新实例恢复。

### 7.2 SnapshotReceipt 责任链

production 输入 `snapshot_receipt_path` 只是预创建回执的兼容入口；默认路径要求受保护 Runner
配置 `DMH_SNAPSHOT_ADAPTER_BIN`。`create-snapshot-receipt.sh` 调 adapter，adapter 必须自己完成：

- 目标生产实例识别；
- PostgreSQL 事务一致快照/PITR/WAL 位置创建；
- 快照可恢复性校验、加密/保留期校验；
- 使用环境内签名密钥输出 JSON Receipt。

随后 `verify-snapshot-receipt.py` fail-closed 检查：PostgreSQL 16 major、source instance、
`TRANSACTION_CONSISTENT`、schema version、changelog digest、`VERIFIED`、`expiresAt` 晚于 `completedAt`、最大 2 小时年龄、未过期、
`completedAt` 不早于成功 staging Deployment 的完成时间、
`recoveryPosition`（兼容旧 `walLsn/gtidExecuted`）和 signature 格式。production 还必须由受保护
Runner 的 `DMH_SNAPSHOT_SIGNATURE_VERIFIER` 可执行程序验证签名真实性；adapter、verifier 任一未配置
或回执字段不符，production Environment 不能继续。验证后的完整 receipt 会复制到工作区并上传为
`snapshot-receipt-production-<run_id>` artifact，Deployment Receipt 同时记录其 SHA-256，避免只保留
一个不可审计的 Snapshot ID。

### 7.3 恢复 Runbook

[database-recovery.md](runbooks/database-recovery.md) 是事故流程，不是自动化 rollback：

- 发布负责人冻结写入和发布；DBA 恢复到同 major 的**新** PostgreSQL 实例；
- 通过 SnapshotReceipt 选择 PITR/WAL 目标，不覆盖旧库；
- 用 `strict-migration.sh status`、`DATABASECHANGELOG`、关键表/索引/分区和业务不变量校验；
- 用上一成功 Manifest 在隔离 namespace 验收，再切换 Secret/Helm；
- 记录实际 RPO/RTO、快照 ID、recovery position、验证结果；
- 每月至少一次快照恢复演练，演练失败时 production Environment 保持禁用。

## 8. 环境晋级和回滚

### 8.1 dev 自动部署

master CI 成功后自动调用 reusable deploy：验证 Manifest/attestation、发布 Nacos Group、执行 strict
migration（有迁移时）、render exact digest、Helm upgrade、Access 逐 Pod、live image 校验并写
GitHub Deployment Receipt。dev 不要求生产快照和 15 分钟门禁，但失败仍停止后续步骤。

### 8.2 staging 晋级

`promote-staging.yml` 只接受用户输入的完整 master SHA，并通过 GitHub Deployment API 找到同 SHA
成功 dev 记录，取回其中的 Manifest digest。staging Environment 审批后复用同一 digest；acceptance
镜像在集群 Job 中执行，成功后收集 Prometheus baseline/current gate。没有 Prometheus URL 时，必须
由受保护 Runner 提供 gate JSON 文件，路径不存在直接失败。

### 8.3 production 晋级

`promote-production.yml` 校验：

- SHA 是 40 位小写 master commit；
- version 匹配 `vMAJOR.MINOR.PATCH` 且 GitHub tag/release 不存在；
- version 必须严格大于仓库中已有的最高 SemVer tag；非 SemVer tag 不参与比较，首个 release 允许从任意合法版本开始；
- 版本占用检查同时探测 Release 和 Git tag；只有两个 API 都明确返回 HTTP 404 才视为未占用，权限、限流、网络或解析错误均 fail-closed；
- 同 SHA staging Deployment success；
- production snapshot adapter/receipt；
- 当前单人模式下 production Environment 由 `LiXuD` 自审（`prevent_self_review=false`），并等待
  1800 秒；这不是独立复核。切回双人模式时才要求非发起人审批；
- Manifest、镜像、插件和 attestation 全部复验。

部署成功后才发布十个不可变 OCI SemVer 别名（九个运行时镜像和一个 Build Manifest），再创建
指向同一 source SHA 的 Git SemVer tag、Release Record 和 `release-record.v1.json`。OCI 别名发布使用
`oras tag <source@digest> <version>`，逐一回读 descriptor 并拒绝已存在但指向其他 digest 的 tag；它不
重新构建、不复制层、不接受 mutable source-SHA tag。读取目标 tag 时只有明确的 404/manifest-unknown
才按“尚不存在”处理；权限、网络、解析等未知错误直接 fail-closed，绝不把异常误判为空 tag。别名证据写入 `release-aliases.v1.json` 并随
Release 发布。若任一别名冲突，发布 Job fail-closed，生产不会生成 Git Release；已完成的同 digest
别名可安全重试，失败版本不复用。

### 8.4 自动回滚边界

Helm upgrade 开始后任何 Access、acceptance 或 gate 失败，reusable workflow 的 failure step 使用
前一 deployed revision 执行 `helm rollback --wait`，并再次执行 Access rollout。因为 `nacosGroup`
是 Helm value，旧配置组随 revision 一起恢复；旧 Group 不被删除。数据库变化保留，必须 forward-fix
或进入事故 Runbook。首次安装没有 previous revision 时不自动卸载，保留失败现场供人工处置；无论
失败发生在 Helm 前还是之后，都会上传 `deployment-failure-receipt-<environment>-<run_id>`，记录
Manifest、namespace、旧 revision 和是否尝试回滚。

## 9. 发布后门禁、SLO 和制品保留

`observability/release-gates.yaml` 定义 baseline 30m、acute 15m、blocking 60m、enhanced 24h；部署前
通过 collector 的 `--window baseline` 使用 30 分钟 histogram `increase` 计算基线 p95，发布后
默认使用 acute 15 分钟 gate，避免把单个 5 分钟瞬时值误当作发布基线；
`observability/prometheus-rules.yaml` 提供可导入 Prometheus 的持续告警组，覆盖错误预算、p95、
OOM、重启和 connector readiness，安装到平台 Prometheus 前只需按实际指标标签做一次映射。
Helm NetworkPolicy 通过 `networkPolicy.metricsNamespaceLabels` 显式允许监控 namespace 抓取指标，
默认标签为 `kubernetes.io/metadata.name: monitoring`；平台 overlay 必须按实际 Prometheus namespace
替换该值，不能用 `0.0.0.0/0` 或 namespace-wide allow 规则绕过边界。
`collect-release-gates.py` 从 Prometheus HTTP API 采集 acute 15 分钟请求数（traffic）、15 分钟 OOM/restart、5 分钟滑动
5xx 比例和 p95、ready/desired、connector readiness；p95 使用 5 分钟滑动窗口捕捉刚发布后的尖峰，traffic/restart
仍绑定完整 15 分钟窗口。readiness 使用所有 Pod 的 `min`，不能由一个健康 Pod 掩盖另一个失败 Pod；p95 上限为
`max(p95AbsoluteSeconds, baseline_p95*p95Multiplier)`，默认值为 `max(1s, baseline_p95*1.2)`，而不是把当前坏值当基线。
除 loopback 隔离测试外，Prometheus URL 必须使用 HTTPS 且提供
`DMH_PROMETHEUS_BEARER_TOKEN`；脚本拒绝把 bearer token 发送到非 loopback 的明文 HTTP，
也拒绝没有 token 的非 loopback HTTPS。若平台采用 mTLS 或其他认证方式，必须先由受保护 Runner
适配为短期 bearer token，不得静默绕过该门禁。
`release-gates.py` 还按 `observability/release-gates.yaml` 的 `p95AbsoluteSeconds` 与 `p95Multiplier`
绑定 `baselineP95Seconds` 和 `p95LimitSeconds`（默认即 `max(1s, baseline*1.2)`），并要求
`collectedAt` 是最近 20 分钟内的带时区时间戳，防止受保护 runner 复用过期或放宽阈值的 gate JSON；
每份 gate sample 还必须绑定 `environment`、当前 40 位 `sourceSha` 和当前 Build Manifest
`manifestDigest`；workflow 输入的 gate sample 先经过 `normalize-release-gates.py` 严格字段白名单校验，
随后由 `release-gates.py` 再次比对这三个值，未知字段或跨发布复用的旧样本直接拒绝，不会把 runner
本地文件中的额外 JSON 字段复制到 Deployment Receipt 或 GitHub payload；
其余 fail-closed 条件为：15 分钟请求数≥100、error≤1%、p95 不超基线绑定上限、ready=desired、
无新 OOM、重启≤1、connector readiness=UP、synthetic=passed。Workflow 默认执行 acute 15 分钟
同步 gate；blocking 60 分钟和 enhanced 24 小时由 Prometheus 持续告警规则负责，不把一次 gate
误报为完整观察窗口。持续 SLO 目标为 99.9%，并使用 14.4/6 burn-rate 告警；一次 gate 不能替代
`ci/policy/ghcr-retention.yaml` 的硬规则：

- production Release digest、BuildManifest、SemVer OCI 别名和被 Deployment Receipt 引用的 digest 永不删除；
- candidate、SBOM、provenance 至少保留 365 天；
- SemVer tag 不回指其他 digest；
- 任何 GHCR retention 修改先审计，不能用自动清理补救空间压力。

## 10. Secret、RBAC 和外部环境合同

平台必须预创建并按 namespace 隔离：`dmh-runtime`、`dmh-internal-auth`、
`dmh-connector-truststore`、`dmh-ghcr-pull`、`dmh-acceptance`、`dmh-snapshot-verifier`，以及需要时的
`dmh-artifact-repository`。Actions 不执行 `kubectl get secret`、不打印环境变量、不把 Secret 上传
Artifact。

`dmh-runtime` 至少提供 `NACOS_SERVER_ADDR`（以及数据库、Redis、Kafka 等运行时连接项）。Java Pod
通过该 Secret 的 `envFrom` 获取 Nacos 地址，Chart 不再把 `nacos:8848` 这类默认地址写入生产
Deployment；Java 和 dbops Nacos entrypoint 会在 staging/prod 缺少该值或仍指向 loopback 时
fail-closed，dbops Job 复用同一运行时 Secret。

`ci/contracts/runtime-contract.v1.yaml` 将 dbops 的 Secret 边界拆成两类：Job 的
`secretRefs` 只有 `dmh-runtime`，生产快照验签所需的 `dmh-snapshot-verifier` 记录在
`runnerSecretRefs`，只由受保护部署 Runner 使用，绝不通过 `create-private-job.sh` 注入 dbops。

`dmh-internal-auth` 的数据合同必须同时满足应用环境变量和文件挂载：
`INTERNAL_AUTH_TOKEN_URI`、`INTERNAL_AUTH_ACCESS_SECRET`、`INTERNAL_AUTH_BILLING_SECRET`、
`INTERNAL_AUTH_MASTERDATA_SECRET`、`INTERNAL_AUTH_IDENTITY_SECRET`、
`INTERNAL_AUTH_GOVERNANCE_SECRET`、`PLATFORM_ENCRYPTION_MASTER_KEY`，以及二进制/文本文件键
`public.pem`、`private.pem`。Helm 将后两个键以 `0440` 只读 Secret 卷挂载到
`/run/secrets/dmh/internal-auth/`；路径由 Deployment 固定为该目录，避免把工作区绝对路径发布到
Nacos。`private.pem` 只供 Identity 读取，Secret/RBAC 配置必须限制其 namespace 和读取主体。

Job 的 Secret 注入也有固定边界：dbops 的迁移/Nacos Job 使用 `dmh-runtime`；acceptance Job 同时
注入 `dmh-runtime,dmh-acceptance`，其中 `dmh-acceptance` 至少提供 `GATEWAY_URL`、`TEST_USERNAME`
和 `TEST_PASSWORD`。`create-private-job.sh` 在 Job 创建时显式写入
`serviceAccountName=dmh-runtime`、`automountServiceAccountToken=false` 和 `envFrom`，不继承
namespace 的 `default` ServiceAccount，也不在 Job 已创建后修改不可变 PodTemplate；Runner 只负责
创建/观察 Job，不把 `dmh-deployer` 身份传给迁移、Nacos 或 acceptance 进程。
该脚本同时拒绝非 `@sha256:<64 位小写 digest>` 的镜像引用；Job 不接受 `latest`、分支 tag 或
未经 Manifest 校验的可变引用。
脚本和 `ValidatingAdmissionPolicy` 还执行镜像到 Secret 的一一对应：dbops/Nacos/迁移 Job 只能注入
`dmh-runtime`，acceptance Job 必须同时注入 `dmh-runtime` 与 `dmh-acceptance`；即使 Runner 误传了
一个允许名称但不属于该镜像的 Secret，API Server 也会在 Job 创建前拒绝，不能依赖脚本调用约定。

Reusable deploy 在任何 GHCR 登录或 Job 创建前执行 `ci/scripts/preflight-cluster.sh`：检查目标
namespace、供 Runner 使用的 `dmh-deployer` 与 Job/业务 Pod 使用的 `dmh-runtime` ServiceAccount、按环境所需 Secret 的**存在性**和 Runner 对
Deployment/StatefulSet/Job/Service/ConfigMap/ServiceAccount/PVC/NetworkPolicy/PDB 的 get/patch/create/delete RBAC，以及
Pod 日志和 list 子资源权限；Helm 固定使用 `HELM_DRIVER=configmap` 保存 release revision，Runner
对 Secret 只拥有六个预创建名称的 get 权限，不得 list/create/update/patch/delete Secret；只请求 Secret
的资源名和 RBAC 能力，不读取 `.data`，因此失败时也不会把 Secret 值带入日志。dev
只要求运行时/内部认证/TrustStore/GHCR pull Secret，staging 额外要求 acceptance，production
再要求 snapshot-verifier。Namespace 必须由平台预先创建；Workflow 不使用 Helm
`--create-namespace`，避免 Runner 为了部署而申请 cluster-scoped `namespaces` 权限。

仅靠 Role 的 `resourceNames` 不能约束 Job spec 中引用哪个 Secret；也不能阻止 Job 通过
`hostPath` 或 CSI Secret volume 间接读取节点/外部 Secret。因此仓库额外提供
`deploy/admission/job-secret-boundary.yaml`（要求 Kubernetes 1.30+ 的 admissionregistration/v1）。平台管理员必须先以 cluster-scoped 权限应用
`kubectl apply -k deploy/admission`，由 Kubernetes ValidatingAdmissionPolicy 在 `dmh-dev`、
`dmh-staging`、`dmh-prod` 拒绝非 `dmh-runtime` ServiceAccount、非零 backoff、非 dbops/acceptance 的 data-manager-hub GHCR digest 镜像、
多容器/任意 initContainer、覆盖受信镜像 entrypoint 的 `command`、dbops 之外的迁移参数、acceptance 的任意参数、任意 Secret/ConfigMap `envFrom`、非 allowlist 的直接环境变量或 `valueFrom`、非预期 ConfigMap/DownwardAPI/PVC/Secret volume、非 `dmh-ghcr-pull` 镜像拉取 Secret、hostPath/CSI volume 和 host namespace；只允许 `tmp`、`workspace-target`、`runtime` 三个 `emptyDir` 及其固定挂载路径。同时固定 Pod `runAsNonRoot=true`、UID/GID `10001`、`seccompProfile=RuntimeDefault`，并要求容器关闭提权、drop `ALL` capabilities。只有受信 acceptance digest 可以使用临时可写 root，dbops/Nacos digest 必须保持 `readOnlyRootFilesystem=true`。没有该 admission policy 时，Runner 的
Job 创建能力不足以证明 Secret 值不会被 Job 间接读取，production Environment 必须保持禁用。
`preflight-cluster.sh` 不只检查 YAML/资源存在性：它会以当前 `dmh-deployer` 身份对一个带受信 digest、固定 UID/GID 和完整安全上下文的临时 Job 做
`dry-run=server`，故意设置 `command` 覆盖并要求 API Server 返回 entrypoint-boundary 的精确拒绝；若
ValidatingAdmissionPolicy 尚未完成 type-check、绑定丢失或错误地放行，部署在创建任何真实 Job 前即失败。

必须在 GitHub/集群侧完成：

1. master 默认分支、required `CI / required-ci`、CODEOWNERS、禁止 force-push/删除和 stale approval；
   当前单人模式的 PR 审批数为 0，不把 CODEOWNERS 误报为独立 reviewer；
2. `dev`、`staging`、`production` Environments，其中 staging/production 由 `LiXuD` 自审且
   `prevent_self_review=false`；production 另加 1800 秒 wait timer。外部插件另外配置受保护的
   `plugin-signing` Environment 和只允许签名仓库/密钥的 Runner；
3. 先应用 `deploy/rbac/overlays/dev|staging|production`，由 overlay 预置 `dmh-deployer`、`dmh-runtime`
   ServiceAccount，并把 `dmh-deployer` Role 绑定到 ARC Runner ServiceAccount；Chart 内业务 Pod 使用
   独立且无权限的 `dmh-runtime` ServiceAccount，不得把 Runner 身份复用给业务容器。再部署
   `nonprod-deploy` 与 `prod-deploy` ARC ephemeral Runner，
   预装 kubectl/Helm/ORAS/gh/jq/Python3+PyYAML；
   reusable deploy 会读取 `ci/toolchain.lock.yaml`，在连接 GHCR 前拒绝 Helm/kubectl 版本漂移；SA 只允许
   所属 namespace 的 Deployment、StatefulSet、Job、Service、ConfigMap、ServiceAccount/Helm release
   及 rollout 读取；
4. GHCR write/read、Nexus/S3 插件仓库、签名/KMS、Access TrustStore、Prometheus URL/token；
5. PostgreSQL 16、Redis、Kafka、Nacos、Ingress/TLS、StorageClass、备份/PITR adapter；
6. production values 的真实 private CIDR、ingress/dependency namespace label、容量测试和 HPA 输入。

这些条件缺失时，仓库 CI 和 Helm 隔离检查仍可运行，但 production Environment 必须禁用。

## 11. 验收与失败演练矩阵

每次变更至少保留下列证据：

| 类别 | 必测失败场景 | 期望结果 |
|---|---|---|
| PR 边界 | fork 读取 Secret、调度 self-hosted、`pull_request_target` | 无部署权限，required CI 失败或拒绝 |
| 迁移 | 已保护 V051 修改、重复编号、Liquibase lock、V049/V050 HALT | CI/strict Job fail-closed，无 repair/半迁移 |
| 供应链 | 镜像 digest、SBOM、Manifest、插件 JAR/签名篡改 | attestation/Manifest 校验失败，不进入 Helm |
| Nacos | Group 不存在、同 Group 内容漂移、发布中断 | apply/verify 失败，旧 Group 保留 |
| Kubernetes | 缺 probe/resource/latest/root、Access Pod 失败、Helm timeout | policy 或 rollout 失败，回滚到 previous revision |
| 数据库 | 快照过期/实例不匹配、迁移状态不确定、PITR | production gate 拒绝，转 Runbook，不自动 restore |
| 观测 | traffic 不足、5xx、p95、OOM/restart、synthetic 失败 | release gate 失败，保留证据并告警 |
| 观测证据重放 | gate sample 的 environment/source SHA/Manifest digest 不匹配或带未知字段 | normalize/release-gates fail-closed，不进入 Deployment Receipt |
| 保留 | 试图删除 production digest/Manifest | retention policy 校验拒绝，必须审计 |

## 12. 外部平台启用后的最小验收命令

这些命令只读回平台状态，不会创建 Environment、修改分支保护或写入集群；创建/变更动作必须由平台
管理员按变更单执行。每条命令的输出应作为阶段证据 artifact 保存，并与 source SHA、Manifest digest
和执行时间绑定。

### 12.1 GitHub 与 GHCR

先执行仓库提供的只读 GitHub 前置审计；它会校验默认分支、required check、单人/双人 review 合同、
CODEOWNERS 关键路径、Environment reviewer/分支策略、必需 Environment Secret **名称**和在线受保护
Runner label，并把不含 Secret 值的证据 JSON 写入指定路径。审计失败必须保持 production Environment 禁用：

```bash
python3 ci/scripts/verify-github-readiness.py \
  --repository LiXuD/data-manager-hub \
  --review-mode solo-maintainer \
  --output evidence/github-readiness.json
```

该脚本只调用 `gh api` 的读取接口，不创建或修改 GitHub 资源。`--review-mode solo-maintainer`
要求分支保护审批数为 0、不能声称 CODEOWNERS 独立审批；同时仍要求 `dev`、`staging`、
`production`、`plugin-signing` 四个 Environment，staging/production/plugin-signing 至少一个
配置 reviewer、受保护/自定义部署分支，以及 `nonprod-deploy`、`prod-deploy`、`plugin-signing`
三个在线 Runner label。单人模式还必须在人工证据中记录 staging/production/plugin-signing 的
`LiXuD` reviewer、`prevent_self_review=false` 和 production 的 1800 秒 wait timer；这些字段只
证明门禁已显式配置，不证明独立复核。staging/production/plugin-signing 还必须存在 Workflow
实际引用的 Prometheus、snapshot、plugin-signing Secret 名称。若平台采用不同命名，必须显式传入
`--require-*` 参数并把变更记录在平台
变更单中，不能通过删除检查项把审计变成 fail-open。Runner label 只能证明在线 Runner 注册了该
标签，不能替代组织级 Runner Group 的 fork/Environment 隔离审计；后者仍须按第 10 节的平台变更单
和 12.1 的 `gh run list` 证据复核。

```bash
source_sha="${SOURCE_SHA:?export the 40-character master SHA}"
manifest_digest="${MANIFEST_DIGEST:?export sha256:<manifest digest>}"
image_digest="${IMAGE_DIGEST:?export sha256:<image digest>}"
gh api repos/LiXuD/data-manager-hub/environments \
  --jq '{environments:[.environments[].name]}'
gh api repos/LiXuD/data-manager-hub/branches/master/protection \
  --jq '{required:[.required_status_checks.checks[].context],strict:.required_status_checks.strict,forcePush:.allow_force_pushes.enabled}'
gh run list --workflow CI --branch master --limit 20 \
  --json databaseId,headSha,conclusion,status,event
oras manifest fetch "ghcr.io/lixud/data-manager-hub-build-manifest@${manifest_digest}"
gh attestation verify \
  "oci://ghcr.io/lixud/data-manager-hub-gateway@${image_digest}" \
  -R LiXuD/data-manager-hub
```

验收结果必须同时满足：三个部署 Environment 和 `plugin-signing` 存在、`master` required check
包含 `CI / required-ci`、Manifest artifact type 正确、九个 image reference 与线上 digest 一致，且
每个镜像和 Manifest 的 attestation 都能回读。`gh run list` 中任何 source SHA 的 required-ci 失败或
缺失，都禁止调用 promote 工作流。

### 12.2 Kubernetes、RBAC 与 Secret 元数据

```bash
for ns in dmh-dev dmh-staging dmh-prod; do
  kubectl -n "$ns" get role/dmh-deployer rolebinding/dmh-deployer serviceaccount/dmh-deployer serviceaccount/dmh-runtime
  kubectl -n "$ns" auth can-i get deployments --as=system:serviceaccount:"$ns":dmh-deployer
  kubectl -n "$ns" auth can-i get pods/log --as=system:serviceaccount:"$ns":dmh-deployer
  kubectl -n "$ns" auth can-i list secrets --as=system:serviceaccount:"$ns":dmh-deployer
done
```

最后一条必须返回 `no`；再对六个已知 Secret 名称逐一执行
`auth can-i get secret/<name>`，应仅在该 namespace 返回 `yes`。不要使用 `kubectl get secret -o yaml`、
`jsonpath` 或把 `.data` 写入 artifact。应用验收还要确认 Pod 的 ServiceAccount 是 `dmh-runtime`、UID/GID
10001、`automountServiceAccountToken=false`，发布 Runner 使用 `dmh-deployer` 且 `HELM_DRIVER=configmap`。

### 12.3 Nacos、数据库与 Prometheus

```bash
source_sha="${SOURCE_SHA:?export the 40-character master SHA}"
NACOS_MODE=plan NACOS_GROUP="DMH_STAGING_${source_sha}" ./publish-nacos-config.sh staging
NACOS_MODE=verify NACOS_GROUP="DMH_STAGING_${source_sha}" \
  NACOS_SERVER_ADDR=https://nacos.staging.example ./publish-nacos-config.sh staging
db_instance="${PRODUCTION_DB_INSTANCE:?export the production instance id}"
changelog_digest="${CHANGELOG_DIGEST:?export sha256:<changelog digest>}"
staging_completed_at="${STAGING_COMPLETED_AT:?export the successful staging Deployment timestamp}"
python3 ci/scripts/verify-snapshot-receipt.py snapshot-receipt.json \
  --source-instance "$db_instance" --schema-version V050 \
  --changelog-digest "$changelog_digest" \
  --not-before "$staging_completed_at" \
  --signature-verifier /protected/bin/verify-snapshot-signature
curl -fsS "$PROMETHEUS_URL/api/v1/query" \
  --data-urlencode 'query=up{namespace="dmh-staging"}' \
  -H "Authorization: Bearer $PROMETHEUS_BEARER_TOKEN"
```

`plan` 不应产生 Nacos 写入；`verify` 必须看到完整 7 个 Data ID 且 digest 不漂移；快照回执必须是
`VERIFIED`、未过期、同一 PostgreSQL 实例和 changelog digest；Prometheus 查询为空、无 token 或指标
标签未映射均按 fail-closed 处理。

## 13. 分阶段实施标准

| 阶段 | 仓库状态 | 外部完成门槛 | 回退点 |
|---|---|---|---|
| 0. 开发阶段 CI | 当前启用 | Java/Web 编译、单元测试、前端构建和 `CI / required-ci` 稳定通过 | 修复代码后重新提交 |
| 1. 生产前 CI 扩展 | 暂不启用 | 需要时再评估迁移矩阵、架构、依赖扫描和完整集成测试 | 保持基础 CI |
| 2. 制品供应链 | 延期 | GHCR、多架构、签名、SBOM/attestation 和 Manifest 真实回读 | 不发布制品 |
| 3. 非生产 CD | 延期 | 集群、Nacos、真实 acceptance、容量和故障演练 | 不启用部署 Workflow |
| 4. 生产 CD | 延期 | snapshot adapter、SLO、RBAC、彩排、生产发布和具体 digest 回滚 | 保持 production 未启用 |

## 14. 本次实现与后续工作边界

当前开发阶段实际启用的内容只有：

- `.github/workflows/ci.yml` 的 Java 编译/单元测试；
- Web 的 `npm ci`、lint、单元测试和构建；
- `CI / required-ci` 汇总两个基础检查结果。

生产前设计中的镜像、部署、快照、回滚和外部平台内容继续保留在本文，当前不由开发 CI 自动执行。

## 15. 参考与文档职责

- [数据库迁移规范](../sql/MIGRATIONS.md)
- [数据库恢复 Runbook](runbooks/database-recovery.md)
- [当前任务清单](../PENDING_TASKS.md)
- [当前部署手册](DEPLOYMENT.md)
- [GitHub Deployments and Environments](https://docs.github.com/en/actions/deployment/targeting-different-environments/using-environments-for-deployment)
- [GitHub Artifact Attestations](https://docs.github.com/en/actions/security-for-github-actions/using-artifact-attestations)
- [Kubernetes ValidatingAdmissionPolicy](https://kubernetes.io/docs/reference/access-authn-authz/validating-admission-policy/)
- [Kubernetes Rolling Updates](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#rolling-update-deployment)
- [Helm upgrade](https://helm.sh/docs/helm/helm_upgrade/)

| 文档 | 职责 |
|---|---|
| `README.md` | 项目入口、模块与当前能力 |
| `PENDING_TASKS.md` | 当前未完成外部环境、生产迁移和证据 |
| `docs/DEPLOYMENT.md` | 当前可执行手工/本地部署说明 |
| 本文 | 生产部署前 CI/CD 设计蓝图、验收和回滚前置条件；当前开发阶段不启用 |
| `docs/runbooks/database-recovery.md` | 生产数据库事故恢复和演练 |
| `docs/runbooks/release-deployment.md` | Manifest 晋级、发布门禁、失败处置和回滚证据 |
| `deploy/rbac/overlays/*` | 三个 namespace 的 Runner Role/RoleBinding；只允许命名空间范围的发布资源和 Pod 日志读取 |
| `deploy/rbac/README.md` | `dmh-deployer` Runner token 与 `dmh-runtime` 业务身份的绑定、验证和隔离合同 |
| `deploy/admission/*` | Kubernetes ValidatingAdmissionPolicy；限制私有 Job 的 ServiceAccount、Secret、backoff 和 host namespace |
