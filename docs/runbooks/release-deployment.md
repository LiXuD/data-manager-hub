# data-manager-hub 发布与回滚 Runbook

**状态**：仓库脚本/Workflow 已实现并通过隔离验证；等待真实 Kubernetes、Nacos、GHCR 和 Prometheus 环境演练。
**适用范围**：dev、staging、production 的 Build Manifest 晋级、应用回滚和发布后门禁。
**原则**：只按不可变 digest 晋级；数据库迁移只前向恢复，应用 rollback 不反向迁移数据库。

当前可执行的手工/本地部署仍以 [DEPLOYMENT.md](../DEPLOYMENT.md) 为准。本文是 CI/CD 的受保护
Runner 操作合同，不是绕过 GitHub Environment 审批的手工发布命令。

## 1. 发布前检查

发布负责人必须确认：

- source SHA 是 `master` 上的 40 位小写 commit，并且 `CI / required-ci` 成功；
- Build Manifest digest、九个镜像 digest、SBOM、provenance 和 attestation 均可从 GHCR 回读；
- production 版本尚未占用，且十个 OCI SemVer 别名（九个镜像 + Build Manifest）将由 digest 发布并回读为同一 digest；
- 目标 namespace、使用 `dmh-deployer` token 的受保护 Runner、业务 Pod 使用且不自动挂载 token 的
  `dmh-runtime` ServiceAccount、预创建 Secret 和
  `deploy/admission` ValidatingAdmissionPolicy 已通过 preflight；
- staging/production 的 Nacos Group 是 `DMH_<ENV>_<SOURCE_SHA>`，不存在内容漂移；
- production 另有新鲜、同实例、同 changelog digest 的 SnapshotReceipt，且其 `completedAt` 不早于
  成功 staging Deployment 的时间戳；
- staging/production 有 Prometheus URL 与短期 bearer token，或受保护的 gate sample 文件；非 loopback
  Prometheus URL 无 token 时必须 fail-closed（mTLS/其他认证需先适配为短期 bearer token）；
- 上一成功 Deployment Receipt 和 Helm revision 可查询，回滚目标明确。

只允许从 `promote-staging.yml` 或 `promote-production.yml` 触发晋级。不要把 tag、可变镜像 tag、
本地 JSON 或人工填写的 Snapshot ID 作为部署输入。

## 2. 自动发布顺序

Reusable deploy 按以下顺序执行，任一步失败都会停止后续步骤：

1. checkout source SHA，锁定 Helm/kubectl 版本；
2. 检查 namespace、ServiceAccount、Secret 元数据和 Runner RBAC；preflight 必须确认 kubeconfig 实际身份为
   `system:serviceaccount:<namespace>:dmh-deployer`，而不是仅凭 cluster-admin 的 `can-i` 结果放行；
3. 按 Manifest digest 拉取并重新计算 canonical Manifest digest；
4. 验证 required CI、source/tree/toolchain/changelog/Nacos digest 和所有 attestation；
5. production 创建或验证 SnapshotReceipt；
6. 用 dbops 镜像依次执行 Nacos `plan`、`apply`、`verify` 一次性 Job；
7. 按 Manifest migration mode 执行 strict migration Job；
8. 用精确镜像 digest 渲染 Helm，执行 lint、policy、upgrade；
9. Access StatefulSet 逐 ordinal 等待 revision、Ready 和 connector readiness；
10. 从实际 workload template 复核九个运行镜像 digest；
11. staging/production 执行 acceptance Job 和 15 分钟 release gate；
12. 写入 GitHub Deployment Receipt；production 随后发布不可变 OCI SemVer 别名、Git tag、Release Record 并上传完整证据。

Nacos、迁移和 acceptance Job 名称含 `GITHUB_RUN_ID-GITHUB_RUN_ATTEMPT`，`backoffLimit=0`，固定使用
`dmh-runtime` ServiceAccount；镜像必须是 Manifest 已验证的
`ghcr.io/lixud/data-manager-hub-(dbops|acceptance)@sha256:<64 位小写 digest>`，且 admission 只允许单一受信容器、
dbops 的 `migrate|preflight|status|update-sql|nacos` 参数或 acceptance 的无参数 image entrypoint。dbops
只能注入 `dmh-runtime`，acceptance 必须注入 `dmh-runtime,dmh-acceptance`；禁止直接覆盖 `NACOS_SERVER_ADDR`、
通过 ConfigMap 注入环境变量或挂载非预期 volume；只允许 `tmp`、`workspace-target`、`runtime` 三个 `emptyDir`
及固定路径。迁移/Nacos Job 使用 read-only root；
acceptance 因 Maven/Failsafe 会写多个 module-local `target`，仅允许 acceptance digest 使用 UID 10001
的临时可写容器层；acceptance 镜像在构建阶段预取依赖并以 Maven offline mode 执行，dbops 迁移镜像
则把 seed 内容复制到 `/tmp/maven-repo` 后同样以 offline mode 执行，缺少依赖直接失败且不访问公共
Maven mirror。两者仍禁止提权、特权、hostPath/CSI、任意 Secret 和 ServiceAccount token。失败 Job
保留现场，不自动重试或复用名称。

## 3. 失败处置

| 失败阶段 | 自动动作 | 人工动作 |
|---|---|---|
| Manifest、attestation、Nacos plan/verify | 停止，不改线上 workload | 修复制品或配置漂移；不能手工改 Manifest |
| Nacos apply 部分存在 | fail-closed，禁止补写 | 保留旧 Group，清点半发布状态后创建新 SHA Group |
| strict migration | 停止，不 repair/clear lock/rollback | DBA 检查 `DATABASECHANGELOG`，选择 forward-fix 或新实例恢复 |
| Helm upgrade timeout | 若有上一 deployed revision，`helm rollback --wait` | 检查事件、PVC、Secret 和 probes；首次安装保留失败现场 |
| Access Pod 或 connector readiness | 停止 partition 递减 | 恢复上一 revision，核对插件缓存/PVC/TrustStore |
| acceptance 或 release gate | 回滚应用和 Nacos Group | 保留 gate JSON、日志和 failure receipt，确认是否需要前向修复 |
| snapshot verifier | production 不进入 Helm | 重新创建/验证同实例、未过期 SnapshotReceipt |

自动 rollback 的范围只有应用 Helm revision、Access rollout 和 Nacos Group value；数据库 changeset
永远不 reverse。回滚后旧 Nacos Group 和旧 GHCR digest 必须保持可读，直到事故关闭和保留窗口结束。

### 3.1 生产具体 digest 回滚彩排

生产启用前必须用一份已经成功发布过的具体 Build Manifest 和 Helm revision 做一次无业务流量彩排。
以下变量必须来自已归档的成功 `DeploymentReceipt`，不能现场猜测或填写 tag：

```bash
export NAMESPACE=dmh-prod
export GOOD_REVISION=42
export GOOD_SOURCE_SHA=0123456789abcdef0123456789abcdef01234567
export GOOD_MANIFEST_DIGEST=sha256:<64 位小写 digest>
export WORKDIR="$(mktemp -d)"
manifest_ref="ghcr.io/lixud/data-manager-hub-build-manifest@${GOOD_MANIFEST_DIGEST}"

oras manifest fetch --descriptor "$manifest_ref" \
  | jq -e --arg digest "$GOOD_MANIFEST_DIGEST" '.digest == $digest'
mkdir -p "$WORKDIR/release-manifest"
oras pull "$manifest_ref" -o "$WORKDIR/release-manifest"
MANIFEST="$WORKDIR/release-manifest/build-manifest.v1.json"
python3 ci/scripts/manifest.py verify --require-required-ci --manifest "$MANIFEST"
test "$(jq -r '.metadata.gitSha' "$MANIFEST")" = "$GOOD_SOURCE_SHA"
test "$(helm get values data-manager-hub -n "$NAMESPACE" --revision "$GOOD_REVISION" -o json \
  | jq -r '.global.manifestDigest')" = "$GOOD_MANIFEST_DIGEST"

helm rollback data-manager-hub "$GOOD_REVISION" -n "$NAMESPACE" --wait --timeout 20m
NAMESPACE="$NAMESPACE" STATEFULSET=data-manager-hub-access bash ci/scripts/access-rollout.sh
NAMESPACE="$NAMESPACE" MANIFEST="$MANIFEST" bash ci/scripts/verify-live-images.sh
test "$(kubectl -n "$NAMESPACE" get configmap/data-manager-hub-release \
  -o jsonpath='{.data.nacosGroup}')" = "DMH_PROD_${GOOD_SOURCE_SHA}"
```

彩排证据必须包括 rollback 前后的 Helm revision、九个 live image digest、Nacos Group、Access 每个
ordinal 的 Ready/revision 结果和合成验收输出；数据库不执行 reverse migration。若任一校验失败，
production Environment 保持禁用，按数据库恢复 Runbook 处理，而不是继续扩大流量。

## 4. 只读诊断

以下命令不得输出 Secret value：

```bash
kubectl -n "$NAMESPACE" get deploy,statefulset,job,pod -o wide
kubectl -n "$NAMESPACE" get events --sort-by=.lastTimestamp
kubectl -n "$NAMESPACE" get configmap/data-manager-hub-release -o yaml
helm history data-manager-hub -n "$NAMESPACE"
kubectl -n "$NAMESPACE" logs job/"$JOB" --prefix
```

Secret 只能使用名称和 RBAC 元数据验证：

```bash
kubectl -n "$NAMESPACE" get secret dmh-runtime -o name
kubectl auth whoami -o json | jq -r '.status.userInfo.username'
kubectl -n "$NAMESPACE" auth can-i get secret/dmh-runtime
kubectl -n "$NAMESPACE" auth can-i list secrets
kubectl -n "$NAMESPACE" auth can-i patch secrets
kubectl -n "$NAMESPACE" auth can-i delete secrets
```

`whoami` 必须返回 `system:serviceaccount:<namespace>:dmh-deployer`，最后三条中 list/patch/delete
必须返回 `no`（命名 Secret 的 get 才允许为 `yes`）。禁止 `kubectl get secret -o yaml`、`jsonpath`、`base64 -d` 或把
环境变量写入 artifact。

## 5. 发布后观察与证据

staging/production 必须保存：

- source SHA、Manifest digest、九个 image digest 和 required-ci run/attempt；
- `release-aliases.v1.json`，记录九个镜像和 Build Manifest 的 SemVer alias→digest 映射；
- Nacos Group、7 个 Data ID digest、Nacos plan/apply/verify 日志；
- migration mode、Liquibase status、Job 名称和退出码；
- Helm previous/current revision、Access 每个 ordinal 的 revision/Ready 结果；
- live image digest、acceptance 输出、Prometheus baseline/current gate；
- SnapshotReceipt digest（production）、Deployment Receipt 和失败/rollback receipt。

外部 gate sample 在进入 Receipt 前必须通过 `ci/scripts/normalize-release-gates.py` 的严格字段白名单，并且包含
`environment`、当前 40 位 `sourceSha` 和当前 Build Manifest `manifestDigest`；`release-gates.py` 会再次
比对这三个值，防止把另一发布或另一环境的旧样本复用到当前晋级。未知字段按敏感数据处理并拒绝发布。
急性 gate 要求 15 分钟请求数至少 100、5xx 不超过 1%、p95 不超过基线绑定上限、ready 等于 desired、
无新 OOM、重启不超过 1、connector readiness 为 `UP`、synthetic 为 `passed`。60 分钟阻断窗口和
24 小时增强观察由 Prometheus 持续规则负责，不能用一次 gate 代替。

## 6. 生产启用门槛

以下任一项缺失，production Environment 保持禁用：

- GitHub Environment 审批和 protected runner 已验证；
- GHCR 九镜像双架构推送、Manifest 和 attestation 已真实回读；
- Kubernetes RBAC、admission policy、Secret boundary 和 namespace preflight 已演练；
- Nacos immutable Group、PostgreSQL snapshot/PITR、Prometheus gate 已连接；
- 至少一次失败回滚、一次迁移不确定状态、一次快照恢复到新实例演练；
- 至少两次成功 production 发布和一次具体 digest 回滚证据已归档。
