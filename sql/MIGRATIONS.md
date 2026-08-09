# SQL 迁移约定

> 适用于 `sql/migrations/`、`sql/rollbacks/` 与 `sql/changelog/db.changelog-master.xml`。

## 编号规则

- 迁移文件命名为 `V<编号>__<描述>.sql`，**编号必须唯一**，不得与已有文件重号。
- 当前最新迁移为 **V047**；新迁移一律从 **V048** 起顺延编号。
- **禁止重命名已执行的迁移文件**：文件路径参与 Liquibase changeset 的执行记录与校验（checksum），重命名会破坏已部署环境的校验。
- 历史事实说明：`V007__add_permission_tables.sql` 与 `V007__create_interface_param.sql` 重号是既成历史，保持原样，不再新增任何重号。
- 编号唯一性由 `migrate-db.sh` 在 `update`/`dry-run`/`validate`/`baseline` 时自动校验：除历史 V007 外若存在重号，迁移直接失败。
- 可单独执行 `./migrate-db.sh check-numbering`，在不连接数据库的情况下校验编号；V007 文件集合必须严格等于上述两个历史文件。

## Changeset 与 rollback

- 每个新变更必须是 `db.changelog-master.xml` 中的**独立 changeset**，并带**显式 `<rollback>` 块**（与 changelog 文件头注释一致），rollback 脚本放在 `sql/rollbacks/U<编号>__<描述>.sql`。
- 实际执行顺序以 **`db.changelog-master.xml` 中的声明顺序为准**，与文件编号或目录列表顺序无关。

## 连接器迁移 V042—V047

| 版本 | 事实与失败关闭边界 | 回退策略 |
|---|---|---|
| V042 | 插件目录、厂商连接器版本、受控测试、逐实例激活及调用/计费追踪基础 | 仅无任何运行事实时允许 U042，否则前向恢复 |
| V043 | 迁移控制和 Access/Billing observation | 仅无迁移事实时允许 U043 |
| V044 | 为存量配置建立绑定并强制 PLUGIN-only；缺失可迁移材料 HALT | U044 始终拒绝恢复双运行时 |
| V045 | 删除旧请求、认证、映射配置列 | U045 始终拒绝伪造旧列数据 |
| V046 | 保留旧快照的 `V1_DERIVED` 与新发布的 `V2_EMBEDDED` 完整性 | U046 拒绝删除历史完整性事实 |
| V047 | 升级前验证目录/步骤/调用/计费一致性，冻结制品、发布版本和物理删除 | U047 拒绝原地解除保护 |

V046 不改写既有 `pipeline_snapshot/snapshot_hash/call_record/billing_event`；V1 通过新增
`hash_algorithm/integrity_hash` 派生解释，新发布 V2 在每个步骤固化 Artifact/Manifest/Schema 摘要。
V047 要求 `plugin_id/plugin_version` 成对为空或存在，`hash_algorithm/integrity_hash` 同样成对；非空
插件对在迁移前必须能对应目录。检查失败时整个 changeset 原子 HALT，禁止手工“修好”历史后标记执行。

连接器数据库发布最少执行：

```bash
./migrate-db.sh check-numbering
./migrate-db.sh validate
./migrate-db.sh backup
./migrate-db.sh dry-run
./migrate-db.sh update
./migrate-db.sh status
```

发布流水线还必须在独立 PostgreSQL 分别验证 V001—V047 fresh、V046→V047 upgrade、重复 update、
坏目录/坏完整性 HALT 且无部分写入、合法状态变化、非法 UPDATE/DELETE、DRAFT 可编辑和应用查询兼容。

## 恢复边界

- `init.sql` 与 **V001–V024** 区间属于单一 baseline changeset（`baseline-2026-07-22`），其恢复边界为 **U000 整库重建**（`sql/rollbacks/U000__drop_baseline.sql`），不支持区间内单个迁移的独立回滚。
- V025 及之后虽然都有显式 rollback，但拒绝脚本也是合法恢复边界；不能把“存在 U 文件”解释为允许
  破坏性逆迁移。
- V047 冻结连接器制品与历史调用/计费解释链，不能在原库原地解除保护。回退必须先停写，
  将经校验的 V047 前全量备份恢复到新库并验证 Liquibase 校验和与连接器历史，再切换应用；
  若无可用备份，只允许新增前向迁移修复，禁止修改既有 V047 changeset 或历史事实。
