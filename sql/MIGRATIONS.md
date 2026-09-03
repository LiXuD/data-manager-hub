# SQL 迁移约定

> 适用于 `sql/migrations/`、`sql/rollbacks/` 与 `sql/changelog/db.changelog-master.xml`。

## 编号规则

- 迁移文件命名为 `V<编号>__<描述>.sql`，**编号必须唯一**，不得与已有文件重号。
- 当前最新迁移为 **V058**；新迁移一律从 **V059** 起顺延编号。
- **禁止重命名已执行的迁移文件**：文件路径参与 Liquibase changeset 的执行记录与校验（checksum），重命名会破坏已部署环境的校验。
- 历史事实说明：`V007__add_permission_tables.sql` 与 `V007__create_interface_param.sql` 重号是既成历史，保持原样，不再新增任何重号。
- 编号唯一性由 `migrate-db.sh` 在 `update`/`dry-run`/`validate`/`baseline` 时自动校验：除历史 V007 外若存在重号，迁移直接失败。
- 可单独执行 `./migrate-db.sh check-numbering`，在不连接数据库的情况下校验编号；V007 文件集合必须严格等于上述两个历史文件。

## Changeset 与 rollback

- 每个新变更必须是 `db.changelog-master.xml` 中的**独立 changeset**，并带**显式 `<rollback>` 块**（与 changelog 文件头注释一致），rollback 脚本放在 `sql/rollbacks/U<编号>__<描述>.sql`。
- 实际执行顺序以 **`db.changelog-master.xml` 中的声明顺序为准**，与文件编号或目录列表顺序无关。

## 连接器与安全收尾迁移 V042—V058

| 版本 | 事实与失败关闭边界 | 回退策略 |
|---|---|---|
| V042 | 插件目录、厂商连接器版本、受控测试、逐实例激活及调用/计费追踪基础 | 仅无任何运行事实时允许 U042，否则前向恢复 |
| V043 | 迁移控制和 Access/Billing observation | 仅无迁移事实时允许 U043 |
| V044 | 为存量配置建立绑定并强制 PLUGIN-only；缺失可迁移材料 HALT | U044 始终拒绝恢复双运行时 |
| V045 | 删除旧请求、认证、映射配置列 | U045 始终拒绝伪造旧列数据 |
| V046 | 保留旧快照的 `V1_DERIVED` 与新发布的 `V2_EMBEDDED` 完整性 | U046 拒绝删除历史完整性事实 |
| V047 | 升级前验证目录/步骤/调用/计费一致性，冻结制品、发布版本和物理删除 | U047 拒绝原地解除保护 |
| V048 | 校验存量接口/厂商绑定后，建立接口主/备用配置引用、有效绑定唯一性和删除保护 | U048 拒绝原地恢复旧路由约束 |
| V049 | 增加 Manifest v2 索引投影、`SIMPLE_CONNECTOR` Spec/编译事实和测试门禁，并前向扩展 V047 不可变保护 | 仅无任何 SIMPLE/v2 事实时允许 U049；否则事务 HALT |
| V050 | 种入与宿主静态事实逐字段一致的内置 `generic-http:2.0.0` 目录版本；不覆盖已有目录行 | 仅无目录漂移、其它版本及任何控制面/运行/调用/计费引用时允许 U050 |
| V051 | 将 `call_record.error_code` 扩展到 `VARCHAR(64)`，保存完整连接器错误码 | U051 拒绝原地缩回列宽 |
| V052 | 为 `call_record` 增加可空 `interface_id` 及观察查询索引，绑定调用事实与规范接口身份 | U052 始终拒绝删除已登记的接口身份事实 |
| V053 | 补齐管理路由权限词汇、显式动作权限及内置管理员授权 | U053 始终拒绝撤销已生效的管理权限矩阵 |
| V054 | 为同厂商/接口/会计方向的计费发布建立持久锁事实，串行化并发首次发布 | U054 始终拒绝移除已使用的发布锁事实 |
| V055 | 为操作日志补齐租户范围字段和按租户/时间查询索引 | U055 始终拒绝删除租户审计范围 |
| V056 | 保存配置版本的明文/密文状态元数据，避免恢复时丢失安全语义 | U056 始终拒绝恢复不安全的配置版本语义 |
| V057 | 将告警记录类型扩展到 `VARCHAR(50)`，保存完整领域告警类型 | U057 拒绝原地缩回告警类型列宽 |
| V058 | 按 `permission_code` 将 API Key 权限挂回 `caller:view`，修复 V053 的历史 ID 父级引用 | U058 始终拒绝恢复 ID 依赖的错误父级 |

V046 不改写既有 `pipeline_snapshot/snapshot_hash/call_record/billing_event`；V1 通过新增
`hash_algorithm/integrity_hash` 派生解释，新发布 V2 在每个步骤固化 Artifact/Manifest/Schema 摘要。
V047 要求 `plugin_id/plugin_version` 成对为空或存在，`hash_algorithm/integrity_hash` 同样成对；非空
插件对在迁移前必须能对应目录。检查失败时整个 changeset 原子 HALT，禁止手工“修好”历史后标记执行。

V048 先校验 `vendor_config.interface_id`、同接口同厂商重复绑定、旧 `api_interface.vendor_id` 唯一解析、
旧 `fallback_vendor_id` 唯一解析及接口内回退一致性；任一歧义都 HALT。通过后回填
`primary_vendor_config_id`/`fallback_vendor_config_id`，并用复合外键、检查约束和触发器保证配置存在、
未删除、属于当前接口且主备不同。新绑定的唯一性只约束未删除记录；接口状态默认值改为 `inactive`。
V048 rollback 始终拒绝原地逆迁移，必须恢复经验证的 V048 前备份或新增前向修复迁移。

V049 先校验 V047/V048 history、trigger 和存量连接器完整性，再以“可空列 → Legacy 回填 → 成对约束/
索引 → 不可变函数前向替换”的顺序执行。既有 `pipeline_snapshot::text`、`snapshot_hash`、
`hash_algorithm` 和 `integrity_hash` 不得改写；已有 Manifest v2 行会 HALT，不能伪装回填为 v1。
SIMPLE DRAFT 保存 Spec/编译快照但 `snapshot_hash/hash_algorithm/integrity_hash` 仍为空，发布版本才固化
`V2_EMBEDDED` 三类摘要。

V050 的唯一代码事实源是 `GenericHttpConnectorMetadata`。SQL 中的 canonical Manifest、Schema、权限、
capability 顺序、builtin URI/签名和三个 SHA-256 必须与该类一致；精确已有 parent/version 可幂等 no-op，
只存在精确 parent 时仅补 version，任何漂移、partial state 或其它 `generic-http` 版本都原子 HALT。

连接器数据库发布最少执行：

```bash
./migrate-db.sh check-numbering
./migrate-db.sh validate
./migrate-db.sh backup
./migrate-db.sh dry-run
./migrate-db.sh update
./migrate-db.sh status
./verify-v049-connector-product-spec.sh
./verify-v050-generic-http.sh
```

`verify-v049-connector-product-spec.sh` 将 current changelog 的 V050 exact seed 事务回滚后，严格停在 V049
表面验证 V001—V049 fresh、V048→V049、重复 update、Legacy 回填、约束/冻结和 U049 success/HALT；它
不会把后续 generic v2 行排除出 V049 回填断言。`verify-v050-generic-http.sh` 验证 V001—V050 fresh、
V049→V050、重复执行、parent-only 补 version、目录漂移/其它版本 HALT、Java static/compiler/resolver
合成契约，以及 U050 对 Spec、pipeline、测试、激活、调用和计费引用的 HALT 原子性。两脚本只创建并
清理严格命名的 `dataplatform_v049_*_regression`/`dataplatform_v050_*_regression` 临时库；通过不代表
生产数据库已经迁移。

## 恢复边界

- `init.sql` 与 **V001–V024** 区间属于单一 baseline changeset（`baseline-2026-07-22`），其恢复边界为 **U000 整库重建**（`sql/rollbacks/U000__drop_baseline.sql`），不支持区间内单个迁移的独立回滚。
- V025 及之后虽然都有显式 rollback，但拒绝脚本也是合法恢复边界；不能把“存在 U 文件”解释为允许
  破坏性逆迁移。
- V047 冻结连接器制品与历史调用/计费解释链，不能在原库原地解除保护。回退必须先停写，
  将经校验的 V047 前全量备份恢复到新库并验证 Liquibase 校验和与连接器历史，再切换应用；
  若无可用备份，只允许新增前向迁移修复，禁止修改既有 V047 changeset 或历史事实。
- U049 仅在不存在 v2/SIMPLE 插件投影、SIMPLE connector 和 SIMPLE test fact 时执行；任一引用都会
  HALT 且保留完整 V049 surface。存在产品事实时采用应用/版本回滚或前向修复。
- U050 只删除 exact 未引用 seed；任何 connector Spec/快照、test binding、activation、call_record、
  billing_event、其它版本或目录漂移都会 HALT。已发布 Generic 事实不得通过删目录“回退”。

V051 将 `call_record.error_code` 从 `VARCHAR(20)` 扩展到 `VARCHAR(64)`，覆盖完整平台连接器错误码（包括
`TRANSPORT_CONNECTION_ERROR`），避免真实熔断/主备路径在异步调用记录落库时丢失证据。该迁移只扩容，不改写历史值；
U051 拒绝原地缩回，必须通过经验证的备份恢复或新增前向迁移处理。

V052 为 `call_record` 增加可空 `interface_id`，由 Access 从已解析的接口路由写入，并建立按厂商、接口、流水线、
快照和时间过滤的观察索引。该迁移不改写历史调用；旧记录保持可读，新增真实调用必须携带规范接口身份，供阶段 5
观察门禁避免跨接口聚合。U052 拒绝删除该列和索引，必须通过备份恢复或新的前向迁移处理。

V053—V057 是本轮安全和运行正确性收尾的独立前向迁移：分别固化管理权限矩阵、计费发布串行化锁、操作日志租户范围、配置版本加密元数据和告警类型宽度；每个 changeset 都保持已执行历史不可逆。V058 不改写 V053 的执行记录，只按稳定的 `permission_code` 修复 API Key 权限目录父级，fresh、升级和重复执行均必须保持幂等。U053—U058 均为拒绝式 rollback，真实恢复使用经过验证的备份或新增 forward-recovery changeset。
