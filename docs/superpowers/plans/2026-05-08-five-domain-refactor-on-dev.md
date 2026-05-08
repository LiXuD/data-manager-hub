# 数据平台五域收敛重构计划（基于 origin/dev）

## 基线确认

- 计划基线分支：`origin/dev`
- 核对时间：`2026-05-08`
- 远端有效分支仅保留：`master`、`dev`、`test`
- `origin/master` 与 `origin/test` 当前指向同一提交
- `origin/dev` 相对 `origin/master` 额外前进 `42` 个提交

结论：后续重构必须基于 `origin/dev` 推进，不能继续基于旧的 `claude/master` / `codex-five-domain-restructure` 快照。

## 当前 dev 主线结构

根工程当前仍为单层聚合，模块如下：

- `data-platform-api`
- `data-platform-common`
- `data-platform-gateway`
- `data-platform-vendor`
- `data-platform-caller`
- `data-platform-call`
- `data-platform-billing`
- `data-platform-graylog`
- `data-platform-interface`
- `data-platform-log`
- `data-platform-monitor`
- `data-platform-iam`
- `data-platform-tenant`
- `data-platform-trace`
- `data-platform-quality`
- `data-platform-security`
- `data-platform-sdk`
- `data-platform-test`

说明：

- `dev` 主线已经引入大量新功能，但总体仍是“多单体模块并列”的结构。
- 历史上曾存在部分 `api/service` 子模块痕迹，但在 `dev` 主线中并未形成统一稳定的契约分层方案。
- 旧五域 worktree 通过物理迁移和删除 legacy 目录实现了新的骨架；该方案不能直接快进到 `dev`，需要按 `dev` 当前代码重新落地。

## 新增功能纳入范围

本次必须纳入重构范围的新功能：

### 主数据相关

- `interface` 接口管理
- `InterfaceParam` / 参数映射
- `VendorParamsMapping`
- `VendorHealth`
- `ConfigVersion`
- `GrayRule`

### 访问链路相关

- `data-query`
- `data-test`
- `interfaceCode` 驱动的查询链路
- `RateLimitService`
- 厂商远程调用编排

### 计费相关

- `BillingType`
- 标准 / 阶梯 / 动态计费计算
- `BillingReconciliation`
- `TenantBudget`
- `BudgetAlertService`
- `BudgetScheduler`

### 身份与安全相关

- `iam`
- `tenant`
- `security`
- 登录 / 鉴权
- 加解密
- 签名能力
- 租户脱敏规则

### 观测治理相关

- `monitor`
- `log`
- `quality`
- `trace`

### 其他

- `sdk`
- `test` 模块里的 API 测试与单测
- `web` 端新增路由与页面能力

## 五域目标映射

### 1. masterdata

合并：

- `vendor`
- `interface`
- `graylog`
- 与主数据直接相关的配置能力

保留的核心职责：

- 厂商信息
- 接口定义
- 参数映射
- 灰度规则
- 厂商配置
- 厂商健康信息

说明：

- `dev` 当前没有独立 `datatype` 模块；数据类型能力已分散在 `vendor/billing/call`，后续统一归口到 `masterdata` 或共享契约。

### 2. access

合并：

- `caller`
- `call`

保留的核心职责：

- 调用方
- API Key
- 调用记录
- 数据查询
- 测试查询
- 流控
- 远程编排

约束：

- `access` 只依赖 `masterdata-api` 和 `billing-api`
- 不允许直接依赖 `vendor/billing` 的 service、mapper、entity

### 3. billing

保留独立域：

- `billing`

保留的核心职责：

- 计费规则
- 日账单
- 预算
- 对账
- 计费计算器

### 4. identity

合并：

- `iam`
- `tenant`
- `security`

说明：

- `dev` 主线已经存在聚合式 `iam`，因此这里不再沿用旧方案里的 `user + role` 独立模块设想，而是以 `iam` 为基础收口到 `identity`

保留的核心职责：

- 用户
- 角色
- 权限
- 认证
- 加密
- 签名
- 脱敏

### 5. governance

合并：

- `monitor`
- `log`
- `quality`
- `trace`

保留的核心职责：

- 告警
- 审计
- 质量规则
- 质量评分
- 数据血缘

## 共享模块重构方向

`dev` 主线中的 `data-platform-api` 与 `data-platform-common` 需要重新拆分：

### `data-platform-common-contract`

承载：

- `Result`
- `PageResult`
- 错误码
- 常量
- 远程 DTO / VO / BO
- Feign 契约接口

### `data-platform-common-web`

承载：

- Web 公共配置
- 全局异常
- 拦截器
- Jackson 配置

### `data-platform-common-persistence`

承载：

- MyBatis 公共配置
- 审计字段处理
- 基础持久化设施

### `data-platform-common-runtime`

承载技术型运行时能力：

- Vendor Adapter
- Circuit Breaker
- Retry
- 非业务性的 HTTP 调用基础设施

约束：

- 不放跨域业务实体
- 不放业务规则
- 不放跨服务 service 实现

## 执行顺序

### Phase 1：先重排根骨架

目标：

- 根 `pom.xml` 改造成新的五域聚合结构
- 保留 `web/test/sdk/gateway`
- 引入统一版本与 Enforcer 规则

本阶段只改工程骨架，不做大规模业务搬迁。

### Phase 2：拆共享层

目标：

- 从 `data-platform-api` 与 `data-platform-common` 中拆出：
  - `common-contract`
  - `common-web`
  - `common-persistence`
  - `common-runtime`

说明：

- 先完成依赖方向改造，再做业务域迁移，否则会反复返工。

### Phase 3：迁主数据与访问链路

优先级最高：

- `masterdata`
- `access`
- `billing`

原因：

- 这三者构成核心调用主链
- 当前耦合最严重
- 新功能大多挂在这条链路上

### Phase 4：迁身份域

- `identity`

重点处理：

- `iam` 聚合收口
- `tenant/security` 并入
- 认证和签名能力外提为契约

### Phase 5：迁治理域

- `governance`

重点处理：

- `monitor/log/quality/trace` 收口
- 保持只做观测与治理，不承载主业务编排

### Phase 6：迁 sdk / test / web

- `sdk` 改为纯 jar 产物
- `test` 改为按五域组织测试
- `web` API client 和路由切到新的网关出口

### Phase 7：删除 legacy 目录与旧依赖

完成条件：

- 不再有模块直接依赖旧服务实现
- 不再有 legacy 模块参与 reactor
- 不再有全包扫描

## 当前风险点

### 风险 1：旧五域 worktree 不能直接 cherry-pick

原因：

- `dev` 主线已经改过大量文件路径和模块结构
- 旧重构分支通过“物理删除 legacy 目录”实现骨架更新
- 直接挑拣会覆盖 `dev` 新功能

处理：

- 以 `origin/dev` 为基线重新分阶段重放重构

### 风险 2：`iam` 已成为新的聚合中心

原因：

- 原始五域计划假设 `user/role/tenant/security` 仍然分散
- `dev` 已演化出 `iam`

处理：

- `identity` 以 `iam` 为主干，不再机械沿用旧域拆法

### 风险 3：共享模块边界不清

原因：

- `data-platform-common` 当前混有技术能力、业务实体和通用工具

处理：

- 先拆共享层，再做服务收口

## 下一步执行入口

下一步从以下动作开始：

1. 在 `origin/dev` worktree 中重写根 `pom.xml` 为五域聚合
2. 新建四类共享模块骨架
3. 建立 `masterdata/access/billing/identity/governance` 五域父模块和 `api/service` 子模块
4. 暂不立即删除旧模块目录，先通过受控迁移逐步切换依赖方向

这份文档是后续重构的唯一基线说明。执行过程中的所有模块迁移，应以本文件为准。

## 协作规则

重构期间的分支职责、同步频率、允许 / 禁止的改动范围，见：

- [2026-05-08-refactor-branching-policy.md](./2026-05-08-refactor-branching-policy.md)
