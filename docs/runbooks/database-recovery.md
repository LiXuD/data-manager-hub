# data-manager-hub 数据库恢复 Runbook

**状态**：设计版，尚未完成真实备份恢复演练，不等同于当前生产操作手册。
**适用数据库**：PostgreSQL 16。
**目标**：外部事务一致性快照 + PITR/binlog 等价恢复能力，RPO ≤ 5 分钟，RTO ≤ 60 分钟。

当前生产发布仍以 [DEPLOYMENT.md](../DEPLOYMENT.md) 为准。只有平台、DBA 和值班负责人完成本文演练并把证据写入 CI/CD Release Record 后，才能启用 production CD。

## 1. 触发条件和禁止动作

进入本 Runbook 的条件包括：

- Liquibase changeset 执行状态不确定或出现非事务 changeset 失败；
- 生产数据被错误写入、删除或结构与应用版本不兼容；
- 应用回滚后无法兼容已执行的数据库迁移；
- 关键数据一致性检查失败。

自动化流水线禁止：

- 调用 `migrate-db.sh restore`；
- `DROP DATABASE`、原地重建数据库或清空生产表；
- 自动清理 Liquibase lock；
- 在状态不确定时自动重试迁移；
- 把 rollback SQL 当作数据恢复方案。

## 2. 责任分工

| 角色 | 责任 |
|---|---|
| 发布负责人 | 冻结发布、维护沟通、记录时间线和 Release ID |
| DBA | 选择快照、执行新实例恢复/PITR、核对 schema 和关键数据 |
| 平台负责人 | 准备新数据库 Secret、切换 Kubernetes 配置、执行流量切换 |
| 应用负责人 | 验证上一 Build Manifest、接口和连接器业务行为 |
| 值班负责人 | 决定是否回滚、确认观察窗和事故关闭 |

任何一人不能单独完成生产数据库恢复和流量切换。

## 3. 恢复前证据

必须取得并保存：

- production Deployment Receipt；
- 当前和上一成功 Release Record；
- 迁移前后 schema version 和 changelog digest；
- 外部快照 Snapshot Receipt；
- 事故开始时间、最后一个正确写入时间和目标 PITR 时间点；
- 应用、数据库、Nacos、Prometheus 和 Kubernetes 事件日志。

Snapshot Receipt 至少包含：

```json
{
  "snapshotId": "backup-2026-08-22T10:00:00Z",
  "sourceInstanceId": "prod-postgres-01",
  "engine": "postgresql",
  "engineVersion": "16.x",
  "completedAt": "2026-08-22T10:02:00Z",
  "expiresAt": "2026-08-23T10:02:00Z",
  "consistency": "TRANSACTION_CONSISTENT",
  "recoveryPosition": "0/16B6A80",
  "sourceSchemaVersion": "V050",
  "changelogDigest": "sha256:...",
  "verificationStatus": "VERIFIED",
  "signature": "sha256:..."
}
```

`recoveryPosition` 由 PostgreSQL adapter 填写 WAL LSN 或归档位置；验证器暂时兼容历史
`walLsn`/`gtidExecuted` 字段，但新 receipt 必须使用 `recoveryPosition`。生产流水线在迁移前
调用受保护 Runner 上的 `ci/scripts/create-snapshot-receipt.sh`，由环境内 adapter 创建、校验、签名，
再由 `DMH_SNAPSHOT_SIGNATURE_VERIFIER` 验证 receipt 的签名真实性并输出 receipt；验证器同时拒绝
未声明的额外字段，避免把 Runner 本地敏感信息带入 SnapshotReceipt artifact。人工填写 Snapshot ID
不能替代这一步。

## 4. 恢复流程

### 4.1 冻结写入

1. 创建事故频道并记录 UTC 时间。
2. 停止新的 production 发布和定时任务。
3. Gateway 切换到维护或只读模式。
4. 确认写入请求已经排空，保留最后一个可接受写入时间点。
5. 对故障数据库做一份只读取证快照，不覆盖原快照。

### 4.2 恢复到新实例

1. DBA 创建与生产同 major version、同加密策略的新 PostgreSQL 实例。
2. 将 Snapshot Receipt 交给 `ci/scripts/verify-snapshot-receipt.py` 验证，确认来源实例、过期时间、加密密钥和一致性状态。
3. 通过 `ci/scripts/restore-snapshot.sh` 调用受保护 restore adapter，强制恢复到与生产不同的新实例，并保存 verified result：

   ```bash
   bash ci/scripts/restore-snapshot.sh \
     --adapter /protected/bin/postgres-restore-adapter \
     --receipt snapshot-receipt.json \
     --source-instance prod-postgres-01 \
     --target-instance prod-postgres-recovered-$(date -u +%Y%m%d%H%M%S) \
     --schema-version V050 \
     --changelog-digest sha256:<manifest-changelog-digest> \
     --signature-verifier /protected/bin/verify-snapshot-signature \
     --max-age-hours 168 \
     --output restore-result.json
   ```

   `--max-age-hours` 是恢复流程相对于快照保留策略的显式上限（示例为 7 天），不改变
   production 发布前 2 小时的新鲜快照门禁；应按 DBA 批准的保留窗口填写，不能用无限大值绕过
   `expiresAt` 或签名校验。

4. 根据事故时间选择 PITR 目标时间或 WAL LSN；默认恢复到迁移开始前最后一个确认正确的时间点。
5. 恢复结束后禁止业务连接，只允许验证账号。
6. 记录新实例指纹，不覆盖旧实例。

### 4.3 结构和数据验证

```bash
DB_HOST=<new-instance> \
DB_PORT=5432 \
DB_NAME=dataplatform \
DB_USERNAME=<verification-user> \
DB_PASSWORD=<provided-in-cluster> \
bash ci/scripts/strict-migration.sh status
```

验证项：

- `DATABASECHANGELOG` 版本和 checksum 与目标快照一致；
- 核心表、索引、约束和分区数量符合恢复前基线；
- tenant、user、role、permission、vendor、connector、billing 等关键表行数没有异常突变；
- 抽样业务不变量通过；
- 只读执行关键查询，不能在验证阶段写入业务数据。

### 4.4 应用验证和切换

1. 使用上一成功 Build Manifest 在隔离 namespace 启动应用。
2. 将应用的数据库 Secret 指向新实例的只读/验证端点。
3. 执行 acceptance 镜像：登录、权限、连接器、调用记录、计费事实和数据查询。
4. 平台负责人创建新的 Secret 版本，GitHub Actions 只引用 Secret 名称，不读取 Secret 值。
5. Helm 回退到上一成功 revision，并指向新数据库。
6. 先开放合成租户，再开放少量真实流量。
7. 观察 15 分钟急性指标和 60 分钟阻断指标。
8. 通过后恢复正常写入，保留旧数据库和新实例用于审计。

## 5. 迁移失败处置矩阵

| 状态 | 处理 |
|---|---|
| 连接失败且未获得 Liquibase lock | 最多重试三次，指数退避 |
| lock 冲突 | 停止流水线，由 DBA 核对活跃迁移和 lock 时间 |
| changeset 已确认成功 | 重新执行 strict status/update，不执行 repair |
| changeset 已部分执行或状态不确定 | 冻结写入，使用新实例恢复或人工 forward-fix |
| ONLINE_COMPATIBLE 迁移成功、应用失败 | 回退应用和 Nacos，保留扩展结构 |
| MAINTENANCE_REQUIRED/破坏性迁移成功 | 不允许只回退应用，执行本 Runbook |
| 数据污染 | 取证、选择 PITR 时间点、恢复新实例并切流 |

## 6. 演练要求

生产 CD 启用前至少完成：

1. 每月至少一次快照恢复演练；
2. 一次迁移中途故障和不确定状态演练；
3. 一次上一版本应用指向恢复数据库的验收；
4. 一次 Kubernetes Secret 版本切换和 Helm rollback；
5. 记录实际 RPO、RTO、恢复人、快照 ID、目标 LSN、验证结果和遗留问题。

演练失败时，production Environment 保持禁用，不能用人工口头确认替代证据。

## 7. 关闭条件

- 关键数据和 schema 校验通过；
- acceptance 和合成租户全部通过；
- 15/60 分钟观察窗通过；
- 业务负责人确认数据恢复时间点；
- 事故报告、快照、日志和 Release Record 已归档；
- 旧数据库保留期限和销毁责任已明确。
