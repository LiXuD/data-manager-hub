# SQL 迁移约定

> 适用于 `sql/migrations/`、`sql/rollbacks/` 与 `sql/changelog/db.changelog-master.xml`。

## 编号规则

- 迁移文件命名为 `V<编号>__<描述>.sql`，**编号必须唯一**，不得与已有文件重号。
- 新迁移一律**从 V030 起**顺延编号。
- **禁止重命名已执行的迁移文件**：文件路径参与 Liquibase changeset 的执行记录与校验（checksum），重命名会破坏已部署环境的校验。
- 历史事实说明：`V007__add_permission_tables.sql` 与 `V007__create_interface_param.sql` 重号是既成历史，保持原样，不再新增任何重号。
- 编号唯一性由 `migrate-db.sh` 在 `update`/`dry-run`/`validate`/`baseline` 时自动校验：除历史 V007 外若存在重号，迁移直接失败。
- 可单独执行 `./migrate-db.sh check-numbering`，在不连接数据库的情况下校验编号；V007 文件集合必须严格等于上述两个历史文件。

## Changeset 与 rollback

- 每个新变更必须是 `db.changelog-master.xml` 中的**独立 changeset**，并带**显式 `<rollback>` 块**（与 changelog 文件头注释一致），rollback 脚本放在 `sql/rollbacks/U<编号>__<描述>.sql`。
- 实际执行顺序以 **`db.changelog-master.xml` 中的声明顺序为准**，与文件编号或目录列表顺序无关。

## 恢复边界

- `init.sql` 与 **V001–V024** 区间属于单一 baseline changeset（`baseline-2026-07-22`），其恢复边界为 **U000 整库重建**（`sql/rollbacks/U000__drop_baseline.sql`），不支持区间内单个迁移的独立回滚。
- V025 及之后的变更按各自 changeset 的显式 rollback 独立回滚。
