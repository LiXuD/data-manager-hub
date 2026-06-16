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

## 执行进度

### 2026-05-15

- Phase 2 继续推进共享层拆分，已将业务 `*-api` 模块对旧 `data-platform-api` 的依赖切换到 `data-platform-common-contract`。
- 已确认除旧 `data-platform-api` 模块自身外，根工程中不再存在其他模块直接依赖 `data-platform-api`。
- 已将 `data-platform-common-contract` 作为 `Result/PageResult`、异常、枚举、常量和旧 `com.dataplatform.api.*` 兼容类的承载模块。
- 验证：`mvn -q -DskipTests compile` 与 `mvn -q -DskipTests test-compile` 均通过。
- 已将旧 `data-platform-api` 从根 reactor 退役，确认主构建不再需要旧 API 聚合模块。
- 已将 legacy `data-platform-common` 从根 reactor 与 `dependencyManagement` 退役，业务模块现在通过 `common-contract/web/persistence/runtime` 闭合依赖。
- 验证：退役 `data-platform-api` 与 `data-platform-common` 后，`mvn -q -DskipTests compile` 与 `mvn -q -DskipTests test-compile` 均通过。
- Phase 3 继续推进 `masterdata`：已新增 `data-platform-masterdata-service` 独立启动入口 `MasterdataApplication`，显式承接 `vendor/interface/graylog` 三个 legacy 包与 mapper 扫描，并排除旧启动类避免重复全包扫描。
- 已新增 `masterdata` 自有 `application.yml` / `application-dev.yml`，应用名固定为 `data-platform-masterdata`，端口暂沿用主数据入口 `8081`，mapper 扫描改为 `classpath*:mapper/*.xml` 以承接多个 legacy service jar。
- 验证：新增 `masterdata-service` 启动入口后，`mvn -q -DskipTests compile` 与 `mvn -q -DskipTests test-compile` 均通过。
- 已将 `vendor-api` 与 `interface-api` 的核心契约源码迁入 `data-platform-masterdata-api` 新包名下，并把 Feign 服务名统一指向 `data-platform-masterdata`。
- 已移除 `masterdata-api` 对旧 `vendor-api/interface-api/graylog-api` 的依赖，当前主数据新契约可独立编译；为避免过渡期 Feign Bean 冲突，新域 Feign `contextId` 已加 `masterdata` 前缀。
- 验证：主数据 API 契约迁入并去除旧 API 依赖后，`mvn -q -DskipTests compile` 与 `mvn -q -DskipTests test-compile` 均通过。
- 已在 `data-platform-masterdata-service` 新增 `MasterdataContractController`，实现 `masterdata-api` 聚合契约 `/masterdata/**`，当前通过本地 Java 调用委托旧 `VendorService`、`VendorConfigService`、`ApiInterfaceService`。
- 已将迁入的 `masterdata` DTO 去 Lombok 化，避免 JDK 21 下 Lombok 注解处理器不稳定影响契约模块输出。
- 验证：新增 `masterdata` 契约 Controller 并去 Lombok 后，`mvn -q -DskipTests compile` 与 `mvn -q -DskipTests test-compile` 均通过。
- 已将 `vendor/interface/graylog` 的 legacy service 源码和 `ApiInterfaceMapper.xml` 物理迁入 `data-platform-masterdata-service`，复制时移除旧启动类，避免多 main class 与旧全包扫描配置进入新域。
- 已切断 `data-platform-masterdata-service` 对旧 `data-platform-vendor-service`、`data-platform-interface-service`、`data-platform-graylog-service` 的依赖；当前仅短期保留旧 API 依赖承接尚未拆出的 legacy entity/内部 DTO。
- 验证：切断旧 service jar 依赖后，`mvn -q -DskipTests compile` 与 `mvn -q -DskipTests test-compile` 均通过。
- 已将 `vendor-api` 中带 MyBatis 注解的持久化 entity 迁入 `data-platform-masterdata-service`，并将旧内部 Controller 的 DTO 引用切换到 `masterdata-api`。
- 已将旧 `interface-api` Feign 自调用改为 `masterdata-service` 内部本地 `ApiInterfaceService` 调用，并移除空的 `graylog-api` 过渡依赖。
- 当前 `data-platform-masterdata` 目录内已无 `data-platform-vendor-api/service`、`data-platform-interface-api/service`、`data-platform-graylog-api/service` 依赖或旧 API 包引用。
- 验证：主数据域完全切断 legacy 小服务 API/service 依赖后，`mvn -q -DskipTests compile` 与 `mvn -q -DskipTests test-compile` 均通过。
- 已完成 `masterdata-service` 内部旧包名收敛：`vendor/interface_/graylog` 源码均已迁入 `com.dataplatform.masterdata.*` 包下，XML mapper namespace、`@MapperScan`、`type-aliases-package` 已同步更新。
- 已将 `MasterdataApplication` 的 Feign 扫描范围收窄到跨域 `com.dataplatform.log.api`，避免主数据服务把自身 `masterdata-api` Feign 注册为自调用客户端。
- 验证：完成 `masterdata-service` 包名收敛与 Feign 扫描收窄后，`mvn -q -DskipTests compile` 与 `mvn -q -DskipTests test-compile` 均通过。
- 已开始让 `masterdata-service` 的具体 Controller 实现 `masterdata-api` 细粒度 Feign 契约：
  - `VendorController implements VendorFeignClient`
  - `VendorConfigController implements VendorConfigFeignClient`
  - `ApiInterfaceInternalController implements ApiInterfaceFeignClient`
  - `MasterdataContractController implements MasterdataFeignClient`
- 已将 `VendorController` 与 `VendorConfigController` 的跨域返回从持久化 entity 切换为 `masterdata-api` DTO；保留原有辅助页面接口，但新契约不暴露数据库实体。
- 已调整 `VendorConfigFeignClient#list` 契约，保留当前控制器已有的筛选参数，避免迁移过程中丢失页面查询能力。
- 验证：细粒度契约 Controller 落地后，`mvn -q -DskipTests compile` 与 `mvn -q -DskipTests test-compile` 均通过。
- 已新增 `datatype` 契约：`DataTypeDTO`、`DataTypeCreateReqDTO`、`DataTypeUpdateReqDTO`、`DataTypeFeignClient`。
- 已将 `DataTypeController` 改为实现 `DataTypeFeignClient`，跨域返回统一切换为 `masterdata-api` DTO/PageResult，不再暴露 `DataType` 持久化实体。
- 当前 `masterdata-service` 已有 5 个 Controller 显式实现新域 Feign 契约：`MasterdataContractController`、`VendorController`、`VendorConfigController`、`DataTypeController`、`ApiInterfaceInternalController`。
- 验证：`datatype` 契约与 Controller 落地后，`mvn -q -DskipTests compile` 与 `mvn -q -DskipTests test-compile` 均通过。
- 已补齐 `graylog` 与接口管理契约：`GrayRuleDTO` / 创建更新 DTO、`GraylogFeignClient`、`ApiInterfaceManageFeignClient` 与接口参数 DTO。
- 已将 `GraylogController`、`ApiInterfaceController` 改为实现 `masterdata-api` 细粒度 Feign 契约，跨域返回统一切换为 `masterdata-api` DTO/PageResult。
- 已将 `data-platform-call-service` 从旧 `vendor-api/interface-api` 切换到 `data-platform-masterdata-api`，访问链路查询厂商配置和接口定义时只依赖主数据契约。
- 已确认非 legacy 业务模块中不再引用 `data-platform-vendor-api/service`、`data-platform-interface-api/service`、`data-platform-graylog-api/service` 或旧 `com.dataplatform.vendor/interface_.api` 包。
- 已从根 reactor 与 `dependencyManagement` 退役 `data-platform-vendor`、`data-platform-interface`、`data-platform-graylog` 三个旧小服务；目录暂保留为短期迁移对照，不参与主构建。
- 已同步更新测试注释，将厂商/数据类型/配置测试对应服务从 `data-platform-vendor-service` 改为 `data-platform-masterdata-service`，避免后续结构扫描误报。
- 验证：退役主数据 legacy 小服务后，`mvn -q -DskipTests compile` 与 `mvn -q -DskipTests test-compile` 均通过；排除退役目录后的旧依赖扫描无结果。
- 阶段结论：`masterdata` 域已完成从 `vendor/interface/graylog` 三个旧服务的代码迁入、契约收敛、调用方切换和根构建退役。
- 已进入 `access` 域收敛，将 `caller-api/call-api` 契约迁入 `data-platform-access-api`，包名统一为 `com.dataplatform.access.caller.api` 与 `com.dataplatform.access.call.api`，Feign 服务名统一指向 `data-platform-access`。
- 已将 `caller-service/call-service` 业务实现迁入 `data-platform-access-service`，包名统一为 `com.dataplatform.access.caller.*` 与 `com.dataplatform.access.call.*`，并新增独立启动类 `AccessApplication`。
- 已将 `spring.application.name` 固定为 `data-platform-access`，端口暂沿用访问入口 `8082`；`@MapperScan`、`@ComponentScan` 与 `type-aliases-package` 均收窄到访问域本地包。
- 已去除 `access-api` DTO 的 Lombok 依赖，契约层保持普通 Java Bean，仅保留 `common-contract`、`spring-web`、`openfeign` 轻量依赖。
- 已新增 `CallerContractController implements CallerFeignClient` 与 `CallContractController implements CallFeignClient`，通过 `/access/caller`、`/access/call` 暴露跨域契约端点；原页面接口 `/caller`、`/data`、`/call-record` 保持兼容。
- 已将访问域内部 `DataQueryController -> CallerFeignClient` 的自 Feign 调用改为本地 `ApiKeyService/ApiKeyInterfaceService` 调用；跨域仍只通过 `masterdata-api` 与 `billing-api` Feign 调用。
- 已从根 reactor 退役 `data-platform-caller` 与 `data-platform-call` 两个旧小服务；目录暂保留为短期迁移对照，不参与主构建。
- 已同步网关路由：`caller/call-record` 路由转向 `data-platform-access`；同时补齐前一阶段遗漏，将 `vendor/graylog/interface` 路由转向 `data-platform-masterdata`，避免运行期继续打到已退役小服务。
- 验证：退役访问域 legacy 小服务并切换网关路由后，`mvn -q -DskipTests compile` 与 `mvn -q -DskipTests test-compile` 均通过；排除退役目录后的旧小服务依赖与路由扫描无结果。
- 阶段结论：`access` 域已完成从 `caller/call` 两个旧服务的源码迁入、契约迁移、内部调用本地化和根构建退役。
- 已进入 `billing` 域聚焦清理，将 `billing-api` 中的 `BillingDaily/BillingRule/TenantBudget/BillingReconciliation` 持久化实体迁回 `data-platform-billing-service`。
- 已瘦身 `data-platform-billing-api`：移除 MyBatis、Lombok、Validation 依赖，DTO 改为普通 Java Bean，仅保留 `common-contract`、`spring-web`、`openfeign` 轻量契约依赖。
- 已将 `BillingFeignClient` 服务名统一为 `data-platform-billing`，并新增 `BillingCalculateReqDTO` / `BillingCalculateRespDTO` 与 `/billing/calculate` 计费计算契约。
- 已新增 `BillingContractController implements BillingFeignClient`，统一承接账单、规则、预算和计费计算远程契约；页面型 `BillingController` 保持原有 `/billing/**` 兼容入口。
- 已将 `BillingApplication` 的 Feign 扫描范围收窄到跨域 `com.dataplatform.log.api`，并将 `spring.application.name` 固定为 `data-platform-billing`。
- 已将 `access-service` 中本地 `common.billing.*` / `BillingRuleDO` 计费计算改为远程调用 `billing-api#calculateCost`，访问链路不再直接承载计费规则计算逻辑。
- 已同步网关路由：`/api/v1/billing/**` 转向 `data-platform-billing`。
- 验证：`mvn -q -DskipTests compile` 与 `mvn -q -DskipTests test-compile` 均通过；`billing-api` 中无 entity/MyBatis/Lombok/Validation；活跃业务模块中无 `common.billing` / `BillingRuleDO` 直接使用。
- 阶段结论：`billing` 域已完成契约层瘦身、持久化实体回迁、远程计费计算契约落地，以及访问链路对本地计费实现的解耦。
- 已进入 `identity` 域收敛，将 `tenant-service`、`iam-service`、`security` 源码迁入 `data-platform-identity-service`，包名统一为 `com.dataplatform.identity.tenant.*`、`com.dataplatform.identity.iam.*`、`com.dataplatform.identity.security.*`。
- 已新增 `IdentityApplication`，应用名固定为 `data-platform-identity`，端口暂沿用身份入口 `8086`；`@MapperScan` 收窄到 identity 本地域 mapper，Feign 扫描仅保留跨域 `com.dataplatform.log.api`。
- 已合并三套 Web 配置，移除重复 `WebMvcConfig` Bean，统一 CORS 与鉴权拦截器白名单。
- 已新增 `data-platform-identity-api` 契约：`TenantDTO`、`UserDTO`、`RoleDTO`、`LoginReqDTO`、`LoginRespDTO`、`EncryptionReqDTO` 与 `IdentityFeignClient`。
- 已新增 `IdentityContractController implements IdentityFeignClient`，通过 `/identity/**` 暴露租户、用户、角色、登录、加解密跨域契约；原页面接口 `/tenant`、`/user`、`/role`、`/permission`、`/auth`、`/security/encryption` 保持兼容。
- 已从根 reactor 退役 `data-platform-tenant`、`data-platform-iam`、`data-platform-security` 三个旧小服务；目录暂保留为迁移对照，不参与主构建。
- 已同步网关路由：`tenant`、`user/auth/role/permission`、`security` 路由均转向 `data-platform-identity`。
- 已修正 identity 环境配置：dev/prod 的 datasource、redis、sa-token、MyBatis type-aliases 均统一到 identity 域，不再保留旧 IAM 示例库配置。
- 验证：`mvn -q -DskipTests compile` 与 `mvn -q -DskipTests test-compile` 均通过；排除退役目录后的旧 `tenant/iam/security` 依赖与路由扫描无结果；`identity-api` 无 MyBatis/PostgreSQL/Nacos/Redisson/启动类/持久化注解。
- 阶段结论：`identity` 域已完成 `tenant/iam/security` 源码迁入、契约补齐、配置合并、网关切换和旧 reactor 退役。
- 已进入 `governance` 域收敛，将 `monitor-service`、`log-api/log-service`、`quality-service`、`trace-service` 源码迁入 `data-platform-governance-service`，包名统一为 `com.dataplatform.governance.monitor.*`、`com.dataplatform.governance.log.*`、`com.dataplatform.governance.quality.*`、`com.dataplatform.governance.trace.*`。
- 已将远程操作日志契约从旧 `data-platform-log-api` 迁入 `data-platform-governance-api`，Feign 服务名统一为 `data-platform-governance`；`masterdata/access/billing/identity` 的日志依赖均切换到 `data-platform-governance-api`。
- 已新增 `GovernanceApplication`，应用名固定为 `data-platform-governance`，端口暂沿用观测治理入口 `8085`；`@MapperScan` 与 `@ComponentScan` 均收窄到 governance 本地域包。
- 已合并治理域重复 Web 配置，统一鉴权白名单，并放行 `/log/internal/**` 供跨服务操作日志写入。
- 已新增 `governance-api` 域级契约：`AlertRuleDTO`、`QualityScoreDTO`、`DataLineageDTO` 与 `GovernanceFeignClient`，并新增 `GovernanceContractController implements GovernanceFeignClient`，通过本地 service 调用暴露告警规则、质量检查和数据血缘跨域接口。
- 已从根 reactor 退役 `data-platform-log`、`data-platform-monitor`、`data-platform-trace`、`data-platform-quality` 四个旧小服务；目录暂保留为迁移对照，不参与主构建。
- 已同步网关路由：`alert/log/trace/quality` 路由均转向 `data-platform-governance`。
- 验证：`mvn -q -DskipTests compile` 与 `mvn -q -DskipTests test-compile` 均通过；当前 root 聚合有效模块中无旧 `monitor/log/quality/trace` 依赖或路由残留；`governance-api` 无 MyBatis/PostgreSQL/Nacos/Redisson/启动类/持久化注解。
- 阶段结论：`governance` 域已完成 `monitor/log/quality/trace` 源码迁入、日志契约切换、域级契约补齐、网关切换和旧 reactor 退役。
- 阶段结论：五个核心业务域 `masterdata/access/billing/identity/governance` 均已完成 api/service 双模块骨架与主要 legacy 小服务收敛；下一步进入 `sdk` 去 Spring Boot 服务化与遗留物理目录清理评估。
- 已完成 `sdk` 去 Spring Boot 服务化：删除 `SDKApplication`、`SDKController`、`WebMvcConfig` 与 `application*.yml`，`data-platform-sdk` 现在仅保留普通 Java 生成器 Jar。
- 已将 `SDKGeneratorService` 改为无 Spring 依赖的 `com.dataplatform.sdk.generator.SDKGenerator`，并移除 `sdk` 模块对 `common-web`、`spring-boot-starter-web`、Nacos discovery/config 的依赖。
- 已删除 gateway 中 `/api/v1/sdk/**` 到 `data-platform-sdk(-service)` 的路由，避免把 sdk 继续作为可部署微服务。
- 已将测试侧 `SdkApiTest` 从 Gateway HTTP 接口测试改为 SDK Jar 生成器单元测试，并移除 `BaseTest` 中的 sdk-service 端口说明。
- 已删除前端未引用的 `/sdk/**` API helper，避免后续误接回已退役的 sdk HTTP 服务。
- 验证：`mvn -q -DskipTests compile`、`mvn -q -DskipTests test-compile` 与 `mvn -q -pl data-platform-test/data-platform-test-service -am -Dtest=SdkApiTest -Dsurefire.failIfNoSpecifiedTests=false test` 均通过；扫描确认 `data-platform-sdk` 中无 Spring Boot/Web/Nacos/启动类/Controller 配置残留，gateway/test/web 中无旧 `/sdk/**` 服务路由残留。
- 阶段结论：`sdk` 已从独立服务收敛为纯客户端/代码生成 Jar，符合五域重构附加模块定位。
- 已完成遗留物理目录清理，删除已退出 root reactor 且有效模块无引用的旧目录：
  - `data-platform-api`
  - `data-platform-common`
  - `data-platform-vendor`
  - `data-platform-interface`
  - `data-platform-graylog`
  - `data-platform-caller`
  - `data-platform-call`
  - `data-platform-tenant`
  - `data-platform-iam`
  - `data-platform-security`
  - `data-platform-log`
  - `data-platform-monitor`
  - `data-platform-trace`
  - `data-platform-quality`
- 已同步清理一处旧注释，将 `data-platform-interface` 依赖提示改为主数据域内 `ApiInterfaceService` 本地查询提示。
- 当前 Maven 根层仅保留 13 个聚合点：`common-contract/common-web/common-persistence/common-runtime`、`masterdata/access/billing/identity/governance`、`gateway/test/sdk` 与 root。
- 验证：删除旧物理目录后，`mvn -q validate`、`mvn -q -DskipTests compile`、`mvn -q -DskipTests test-compile` 均通过；旧服务目录与旧 gateway 路由扫描无结果。
- 阶段结论：过细 legacy 服务已从 root reactor 和物理目录双重退役，项目骨架已收敛到五域架构主线。
- 已完成最终架构规则扫描与补齐：
  - 修复 `data-platform-gateway` 启动类全包扫描，移除 `scanBasePackages = "com.dataplatform"`，网关只扫描自身包。
  - 瘦身 `data-platform-common-contract`，移除 Spring Web、OpenFeign、Validation starter、MyBatis-Plus starter、Hutool 等传递依赖，仅保留契约层实际需要的 Jackson annotations。
  - 移除 common-contract 枚举上的 MyBatis `@EnumValue`，避免契约层携带持久化框架注解。
  - 在 `data-platform-common-persistence` 新增 `CodeEnumTypeHandler`，并通过 `MybatisPlusPropertiesCustomizer` 统一注册公共 type handler 包，将枚举 code 入库语义留在持久化层。
  - 为 `masterdata-api` 显式声明 `spring-web`、`openfeign`、`jakarta.validation-api` 轻量契约依赖，不再依赖 common-contract 的隐式传递依赖。
  - 移除 root `dependencyManagement` 中五个 `*-service` artifact 的版本管理，只保留可被跨模块依赖的 `*-api` 与共享模块，降低误依赖 service Jar 的入口。
  - 清理 `billing-api`、`test-api` 中不必要的显式版本/空壳依赖。
- 架构扫描结果：
  - 五个业务 `*-api` 与 `common-contract` 中无 MyBatis/PostgreSQL/Nacos/Redisson/启动类/Lombok 等重依赖残留。
  - 五个业务 service 中未发现跨域直接 import 其他域 `entity/mapper/service/controller`，跨域依赖保持在 api 契约层。
  - 未发现旧小服务目录、旧 gateway 路由、旧全包扫描残留。
  - root `dependencyManagement` 中不再暴露五个业务 `*-service` artifact。
- 验证：最终扫描补齐后，`mvn -q validate`、`mvn -q -DskipTests compile`、`mvn -q -DskipTests test-compile` 均通过。
- 阶段结论：五域重构基线已具备提交条件，后续可进入代码审阅、提交与推送。

### 2026-05-12

- 已按 `codex-five-domain-dev` worktree 继续执行，确认旧 `claude/master` 基线 worktree 不再存在。
- 已复核 Phase 1/Phase 2 当前成果：五域父模块、四类共享模块、业务模块对旧 `data-platform-common` 的直接依赖收口均保留在当前工作区。
- 验证：`mvn -q -DskipTests compile` 通过。
- Phase 3 已开始：`data-platform-masterdata-api` 聚合 `vendor/interface/graylog` 三个 legacy API 模块，`data-platform-masterdata-service` 聚合对应 service 模块，作为主数据域受控迁移入口。
- 已补齐根 `dependencyManagement` 中 `vendor/interface/graylog` 的 API/service 模块版本管理，避免五域父模块直接声明版本。
- 验证：主数据域聚合入口加入后，`mvn -q -DskipTests compile` 通过。
- 下一步继续把 `vendor/interface/graylog` 中的契约 DTO 与服务实现逐步搬入 `masterdata` 域，并在每个可编译小步后记录进度。

### 2026-05-11

- 已清理旧 `claude/master` 基线的 `codex-five-domain-restructure` worktree 和本地旧分支，后续仅以 `codex-five-domain-dev` 为重构执行分支。
- Phase 1 已落地五域父模块骨架：
  - `data-platform-masterdata`
  - `data-platform-access`
  - `data-platform-billing`
  - `data-platform-identity`
  - `data-platform-governance`
- Phase 2 已落地四类共享模块骨架：
  - `data-platform-common-contract`
  - `data-platform-common-web`
  - `data-platform-common-persistence`
  - `data-platform-common-runtime`
- 已将业务模块的直接 `data-platform-common` 依赖收口到新的共享模块组合；当前仅根聚合与 legacy `data-platform-common` 模块自身保留旧模块引用。
- 已将 `OperationLogRecord` 与 `OperationLogService` 从 Web 共享层移到 contract 共享层，供 `log-api` 等契约模块引用。
- 验证：`mvn -q -DskipTests compile` 通过。

剩余遗留项：

- `data-platform-vendor-api` 仍包含带 MyBatis 注解和 `JsonbTypeHandler` 的实体，当前临时依赖 `common-persistence` 保持编译闭合；后续迁入 `masterdata` 时应拆分 API DTO 与持久化实体。
- legacy `data-platform-common` 仍参与 reactor，用于受控迁移窗口；待五域服务迁移完成后再从根 `pom.xml` 与 dependencyManagement 删除。
