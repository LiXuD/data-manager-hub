# Dev 环境闭环审查报告（2026-08-30）

## 结论

以“本地开发环境可重复跑通真实业务闭环”为验收目标，当前结论为**已达到**，置信度高。
该结论只覆盖 Mac/Ubuntu 开发运行方式、隔离数据库、六服务、Vite 前端和 Dev MVP 夹具；不外推为
staging/production 已发布，也不外推为真实厂商、生产容量、滚动升级、故障恢复或回滚已经验证。

## 直接证据

| 层次 | 已验证事实 | 判定 |
|---|---|---|
| 编译与单测 | 后端 25 个 Reactor 模块 `verify` 成功；前端 lint、69 个 Vitest、构建成功；CI 合同 65/65 成功 | confirmed |
| 数据库 | fresh 隔离库迁移到 V052，`pendingMigrations=0` | confirmed |
| 业务运行态 | 3 家厂商、2 个调用系统、2 类数据、2 个接口、3 个连接器配置；2 个审批授权、12 条调用记录、3 条计费事件、计费金额 ¥1 | confirmed |
| 产品页面 | 真实浏览器登录后读取连接器诊断、授权台账、调用记录、计费、审计和监控；各页面无 console error | confirmed |
| 可重复演示 | `--demo` 返回后，前端和六服务仍监听；状态文件为 600；`--stop-runtime` 后七个监听、隔离库和运行目录均清理 | confirmed |
| 开发合并门禁 | `dev` 和 `master` 均要求 `CI / required-ci`，管理员同样受约束，禁止 force-push 和删除 | confirmed |
| 安全依赖同步 | `ci/contracts/dev-security-sync.v1.json` 固化从 `master` 选择性回合并的 Maven、npm 和 Action 版本；Spring Kafka 3.3.16、Netty 4.1.137.Final、Tomcat 10.1.59 已通过依赖扫描 | confirmed |
| 默认分支调度 | PR #27 已合入 `master`（merge SHA `e7a317f5dfb92dec827300ab2f7b47e4c6ee6167`）；工作日调度已 active，手工 run `33289826840` 从 `dev` SHA `b3a7343be809e245d2696dd7200f96698693bad4` full-build 通过并上传报告 artifact | confirmed |

机器报告由 `data-platform-test/test-fixtures/dev-mvp/verify-dev-closure.sh` 生成，报告 v2 记录源码 SHA、
dirty 状态、UTC 起止时间、耗时、`full-build`/`skip-build` 和 `keepRunning`，因此不能再把旧制品复用
误报为 fresh 构建。最终合并证据必须使用 PR 最终 SHA 的 `CI / required-ci`，并在合入 `dev` 后执行
一次 `DEV_MVP_SKIP_BUILD=false` 的 fresh 闭环。

## 发现与边界

1. **P2，非阻断前端债务**：浏览器没有 console error，但侧栏图标会重复产生 Vue runtime compiler warning。
   根因位于 `data-platform-web/src/views/layout/index.vue` 的运行时 `template` 图标对象；当前构建使用
   runtime-only Vue。最小修复是把图标改为编译期 SFC、render function 或现成图标组件，并给登录后的
   布局烟雾测试增加 console warning 断言。
2. **远端重复性必须持续观察**：`.github/workflows/dev-mvp-e2e.yml` 已通过 PR #27 镜像到默认分支
   `master`，状态为 active，明确 checkout `dev`；首次 Ubuntu 手工 run 已成功。它只按工作日定时或手工执行，
   故意不作为 PR required check；每次运行必须 full-build，并只上传不含凭据的报告。后续需观察成功率、耗时和
   artifact 保留情况，不能把一次成功外推为长期稳定性。
3. **生产能力未验证**：真实厂商 inventory/CAS 迁移、观察窗口、生产容量、双 Access 滚动升级、旧入口
   最终退役、staging/production 发布、PITR/快照、制品签名和具体 digest 回滚仍是后续门禁。

## 下一步开发方向

1. 先稳定 Dev MVP：观察定时 E2E 的成功率和耗时，失败必须保留安全报告与精确日志，禁止静默重试掩盖回归。
2. 补齐浏览器自动化：把登录、授权台账、调用记录、计费和 6/6 监控状态收敛成可重复的只读烟雾测试。
3. 清理前端 warning，并把“无 console error/warning”纳入浏览器烟雾证据。
4. 只有在产品决定进入发布准备后，再按 `PENDING_TASKS.md` 的生产门禁推进真实厂商、容量、滚动升级、
   故障和回滚；当前不要用生产条件反向阻塞 dev 功能迭代。
