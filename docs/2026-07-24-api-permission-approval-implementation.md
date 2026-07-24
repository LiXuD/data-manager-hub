# 接口调用权限审批实施与验收清单

> 依据：`docs/2026-07-23-api-permission-approval-design.md`
> 状态：功能开发与全量回归已完成
> 开始日期：2026-07-24

## 0. 完成口径

“已完成”指设计文档第 19 节的生产核心验收标准已落地：申请、审批、自动授权、撤回、驳回、撤销、到期、紧急授权、租户/候选组/自审控制、幂等与乐观锁、引擎版本和重启恢复、运行时权限一致性、迁移与旧入口收口。

设计中明确列为 V1 非目标或建议项的在线 BPMN 设计器、Camunda 8 适配器、邮件/短信/企业 IM 通知、跨域通用审批平台和自动推荐不属于本次完成范围。流程路由和 BPMN 版本继续采用 Git 评审、Liquibase 与发布流程管理，Flowable 原生管理接口不对外开放。

## 1. 验收矩阵

| 场景 | 授权与所有权 | 契约 | 数据变化 | 可见结果 | 负向路径 | 运行时副作用 | 完成证据 |
|---|---|---|---|---|---|---|---|
| 创建草稿 | 登录用户拥有 `api-permission:apply`，Caller 属于当前租户且用户可管理 | 创建申请 DTO/VO | application、items、CREATE action | 我的申请出现 DRAFT | 跨租户、Key 不属于 Caller、接口不存在 | 无流程实例、无授权 | Controller/Service 测试、前端验收 |
| 提交申请 | 草稿属于本人；identity 确认 user-caller；接口 active | `Idempotency-Key`，submit 响应包含流程实例 | 状态 IN_REVIEW、SUBMIT action、Flowable instance/task | 审批中心出现首任务 | 重复待审、已有授权、下游失败、无流程路由 | 启动固定版本 BPMN | 集成测试、流程历史 |
| 认领任务 | 用户有 approve 权限且属于 candidate group | claim task | Flowable assignee | 待办显示已认领 | 非候选人、跨租户、重复认领 | 无授权 | Flowable 集成测试 |
| 完成中间节点 | 当前用户是 assignee，禁止自审 | complete task + 白名单 formData | action、引擎历史、下一任务投影 | 展示下一节点 | 非法 decision、过期 task、并发完成 | 创建下一 User Task | 多节点/网关测试 |
| 最终批准 | 最终节点合法处理人 | complete task | grant upsert、申请 EFFECTIVE、GRANT action | 当前授权可见 | 接口停用、Key 失效、重复授权 | OpenAPI 权限即时生效 | 事务测试、OpenAPI/文档回归 |
| 驳回 | 当前任务合法处理人 | REJECT + 必填意见 | REJECT action、申请 REJECTED、流程结束 | 申请显示驳回轨迹 | 空意见、任务冲突 | 不产生授权 | 集成测试 |
| 撤回 | 本人、流程处于允许撤回节点 | cancel | CANCELED action、流程终止 | 申请显示已撤回 | 已进入开通或已结束 | 不产生授权 | 集成测试 |
| 到期 | 系统任务 | 无外部请求 | grant EXPIRED、EXPIRE action | 当前授权显示到期 | 扫描延迟 | 运行时按 expireAt 立即拒绝 | 时间边界测试 |
| 撤销 | `api-permission:revoke`，同租户 | revoke + reason | grant REVOKED、REVOKE action | 当前授权显示撤销 | 跨租户、重复撤销 | OpenAPI/文档立即 403 | 回归测试 |
| 紧急授权 | `api-permission:emergency-grant` | emergency grant DTO | grant + EMERGENCY action | 限时授权可见 | 超 24h、缺工单/原因 | OpenAPI 即时生效 | 权限/期限测试 |
| 流程版本升级 | `process-manage`，经 Git 评审发布 | BPMN 版本 | 新部署版本 | 新实例走新版本 | 旧实例误迁移 | 旧实例继续原版本 | 版本并存测试 |

## 2. 强制架构约束

- 审批业务、申请表和最终授权均归属 access 域。
- user-caller 关系只通过 identity-api 内部 Feign 契约查询。
- 接口摘要只通过 masterdata-api 内部 Feign 契约查询。
- `*-api` 不引入 Flowable、MyBatis、数据库或其他运行时重依赖。
- Flowable 原生 REST、Actuator 流程管理端点和数据库不通过 Gateway 暴露。
- 业务代码只依赖 `ApprovalEnginePort`，不在 Controller/业务 Service 散落 Flowable API。
- `api_key_interface` 是运行时授权事实表，流程引擎不是 OpenAPI 鉴权数据源。
- 关键审批动作写入不可变业务动作表，不能只依赖引擎 History。

## 3. 实施阶段

| 阶段 | 状态 | 退出条件 |
|---|---|---|
| Flowable 兼容性 POC | 已完成 | Flowable 7.1.0 在 JDK 21、Boot 3.4.13 下完成部署、候选组、认领、完成和历史测试 |
| V026 与授权模型 | 已完成 | fresh/upgrade/repeat/V026/Flowable/V025/full rollback、backup/restore/baseline 回归通过 |
| 跨域资格校验 | 已完成 | identity-api 提供 user-caller/role 契约，masterdata-api 提供接口批量与选项契约 |
| 申请与流程启动 | 已完成 | 草稿、幂等提交、撤回、流程定义版本和当前任务投影已实现 |
| 任务与自动授权 | 已完成 | 候选组认领、自审拦截、租户隔离、批准、驳回、自动授权、撤销与到期任务已实现 |
| 前端闭环 | 已完成 | 我的申请、草稿编辑/提交、审批待办、授权台账、紧急授权和旧管理员入口收口已实现 |
| 全量回归 | 已完成 | Maven、前端生产构建、迁移、架构扫描和桌面/移动端运行态验收通过 |

## 4. 回归证据（2026-07-24）

| 维度 | 结果 |
|---|---|
| 后端全仓测试 | `mvn test`：25 个 Reactor 模块成功，279 个测试，0 失败/错误 |
| 审批引擎 | 单节点、顺序多节点、条件网关、并行网关、并行多实例会签、V1/V2 并存、重启恢复、动态节点策略测试通过 |
| 前端 | ESLint、Vue TypeScript 检查和 Vite 生产构建通过；`npm audit` 0 漏洞 |
| 数据库 | dry-run、fresh update、重复 update、V026、Flowable workflow schema、V025/full rollback、重放、backup/restore、baseline 全部通过 |
| 架构边界 | `arch-scan.sh` 通过；跨域仅经 identity-api/masterdata-api Internal Feign，Gateway 内部路径、Flowable REST 和 Access Actuator 均不可达 |
| 真实服务 E2E | 隔离 PostgreSQL 上完成 OPEN/RENEW、幂等重放/冲突、并发提交、认领、批准、驳回、撤回、撤销、紧急授权、自然到期、服务重启恢复 |
| 运行时权限 | 批准后单条、批量、文档列表/详情立即可见；撤销/到期后立即 403；到期扫描延迟不影响实时拒绝 |
| 并发与持久化 | MyBatis 乐观锁插件启用；申请状态更新失败关闭；可空任务投影、草稿字段和授权生命周期字段可显式清空 |
| 代码卫生 | `git diff --check` 通过；Flowable/Spring Boot/MyBatis/Jackson 受控依赖树验证通过 |

本轮第一性原理回归中发现并修复了上线阻断问题：乐观锁插件缺失、Flowable schema 污染业务查询、默认租户流程实例未覆盖为业务租户、完成任务投影无法清空、调用方文档错误只写业务码未写 HTTP 状态、紧急授权复用记录无法清空撤销/申请字段、申请并发更新结果未检查，以及草稿编辑和紧急授权前端入口缺失。所有修复均有自动化或真实服务回归证据。

## 5. 风险门禁

- `hasInterfacePermission` 静态影响为 CRITICAL；修改前必须再次运行 GitNexus impact，
  并覆盖单条、批量、接口详情和文档下载。
- POC 选定 Flowable 7.1.0，并锁定版本以匹配当前项目依赖基线。
- 不通过强制覆盖 Spring/MyBatis/Liquibase/Jackson 依赖来“解决”Flowable 冲突。
- 已执行 Liquibase changeset 不修改；新增独立 V026/U026 和 Flowable vendor changeset。
- 存在审批实例或审批授权后，回滚脚本必须拒绝删除业务和流程事实。
