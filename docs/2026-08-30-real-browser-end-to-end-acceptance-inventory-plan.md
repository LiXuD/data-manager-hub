# 真实浏览器全链路验收体系：盘点进度与执行计划

> 日期：2026-08-30
> 当前阶段：**历史执行计划；真实浏览器验收、证据脱敏与环境清理已于 2026-08-31 完成**
> 事实基线：`dev` / `d4d56bf6954a2827c8d86223ecc211039debf7a9`
> 本文职责：保留验收前的盘点、边界和执行设计；实际结果见 [验收结果](2026-08-31-real-browser-end-to-end-acceptance-results.md)，后续整改见 [整改方案](2026-09-01-real-browser-acceptance-remediation-plan.md)，全局开发待办仍以 `PENDING_TASKS.md` 为准。

> **状态更新（2026-09-01）：**本文中的“等待确认”“禁止进入浏览器执行”等表述只描述 2026-08-30 当时的停点，不再代表当前执行状态。验收已经按批准范围完成，本文不再作为整改实施依据。

## 1. 目标与本轮边界

最终目标是建立一套由真实浏览器驱动、从真实用户名密码登录开始的全链路业务验收体系：

1. 每个角色按独立确认的预期菜单逐项操作，不以当前页面实际显示结果反向定义“预期菜单”。
2. 第一层覆盖菜单级纵切：列表、查询、新增、修改、删除，以及页面实际存在的启停、验证、发布、回滚、认领、审批、撤销、导出等动作；不适用的页面不强行套用 CRUD。
3. 第二层覆盖跨角色、跨服务的业务旅程，例如管理员创建用户并授权，新用户重新登录，申请并获批接口权限，执行真实调用，随后核验调用记录、计量计费和审计副作用。
4. 每个通过结论都必须同时有浏览器操作、真实 HTTP 链路、服务端状态和持久化/副作用证据；编译、单元测试、静态源码或 GitNexus 图谱都不能单独等价为运行态通过。

本节记录 2026-08-30 当时只批准盘点和本文档写入的边界。当时除本文外，禁止修改业务代码、配置、数据库、测试数据和其他文档，也禁止启动浏览器、服务或会自动迁移/写业务数据的测试。

**当时的强制停点：**角色、预期菜单、能力边界、业务顺序和测试数据策略经用户确认之前，不得直接进入批量浏览器执行。该停点随后已解除并完成执行；新的停点以整改方案中的产品决策与交付门禁为准。

**已确认的已知问题处理规则：**第 4.2 节及同类已发现契约/权限缺口在整套验收完成前不修改，也不得因为已经知道而从测试范围排除。获准进入真实测试后，仍按原业务路径执行并保留失败现场；最终测试结果必须设置独立的“已知问题/契约缺口”章节，对每一项记录复现步骤、预期、实际、影响范围、浏览器/HTTP/服务端/持久化证据和整改建议。待整套测试结束后，再由用户统一确认整改范围和顺序。

## 2. 当前 Git 与工作树基线

| 项目 | 当前事实 | 状态 |
|---|---|---|
| 仓库绝对路径 | `/Users/lixd/IdeaProjects/Git/CodexProject/data-manager-hub` | confirmed |
| 当前分支 | `dev`，与 `origin/dev` 对齐 | confirmed |
| 当前 HEAD | `d4d56bf6954a2827c8d86223ecc211039debf7a9` | confirmed |
| 远端 | `origin = https://github.com/LiXuD/data-manager-hub.git` | confirmed |
| 盘点开始时工作树 | `git status --short --branch` 仅显示 `## dev...origin/dev`，无用户未提交改动 | confirmed |
| 本文写入后的工作树变化 | 只新增本文；已用 `git status`、未跟踪文件清单和 no-index diff 核对 | confirmed |

本轮不会吸收、覆盖或清理任何用户改动。若后续出现本文之外的工作树变化，立即停止并重新确认归属。

## 3. 已完成的盘点进度

### 3.1 项目规则与权威材料

已完整读取并纳入边界：

- `AGENTS.md`、`CLAUDE.md`、`CODE_REVIEW_GATE.md`；
- `README.md`、`PENDING_TASKS.md`、`docs/API.md`、`docs/DEPLOYMENT.md`；
- `docs/2026-08-30-dev-closure-audit.md`、`sql/MIGRATIONS.md`、`nacos-config/README.md`；
- `start-services.sh`、`data-platform-test/test-fixtures/dev-mvp/verify-dev-closure.sh`、`data-platform-test/test-fixtures/dev-mvp/run-dev-mvp.sh` 和 `seed-dev-mvp.sql` 的相关执行与数据段。

现有权威文档证明的是既有 dev/隔离闭环，不是本轮的新鲜运行证据。`docs/2026-08-30-dev-closure-audit.md:41-44` 也把“登录、授权台账、调用记录、计费和监控”的可重复浏览器烟雾列为后续工作，因此本专题不能把历史浏览器记录冒充当前全菜单、全角色验收。

### 3.2 GitNexus 对齐与可信度

盘点开始时，绝对路径对应的索引停在 `f4cc680`，比当前 HEAD 落后 19 个提交。已在刷新前后分别确认工作树清洁，并只执行不会注入项目文件的：

```text
rtk node .gitnexus/run.cjs analyze --index-only
```

刷新后索引提交与当前 HEAD 均为 `d4d56bf`，状态为 up-to-date；当前图谱约为 17,078 个节点、43,519 条边、692 个社区、300 条执行流，刷新未改变仓库工作树。

已完成的图谱探索包括登录/当前用户、角色/权限、菜单/路由守卫、网关认证、接口权限审批、连接器发布/回滚、OpenAPI 调用、调用记录和计费。已确认的代表性流程有：

- `AuthController.login` → `UserContext.login`；源码回查见 `data-platform-identity/data-platform-identity-service/src/main/java/com/dataplatform/identity/iam/controller/AuthController.java:53-97` 和 `data-platform-common-web/src/main/java/com/dataplatform/common/util/UserContext.java:106-115`。
- `VendorConnectorController.publish` → `VendorConnectorServiceImpl.publish` → Access 内部 release；入口见 `data-platform-masterdata/data-platform-masterdata-service/src/main/java/com/dataplatform/masterdata/connector/controller/VendorConnectorController.java:73-82`。
- `ApiPermissionTaskController.complete` → 审批服务 → Flowable → `GrantApiPermissionDelegate`；最终授权落库见 `data-platform-access/data-platform-access-service/src/main/java/com/dataplatform/access/approval/workflow/GrantApiPermissionDelegate.java:30-78`。
- OpenAPI 查询到 Billing 的跨服务链路只能由源码和 Feign 契约补齐；静态 trace 未能完整跨越运行时服务边界。

图谱局限已经明确：

1. 本机存在多个同名历史索引，裸 `gitnexus://repo/data-manager-hub/...` 曾解析到旧路径；本轮后续查询必须固定使用当前绝对路径。
2. `route_map` 虽发现约 212 个后端路由，但未关联出 Vue 消费者，并遗漏 Spring 类级 `/api/v1` 外层语义；不能据此把接口判成孤儿。
3. `shape_check` 当前没有可比较的 route response shape/consumer 组合，不能据此声称响应形状一致。
4. Vue 动态导入、Axios 封装、数据库权限、Spring 注解组合、Feign 和反射/工作流都可能被图谱遗漏；关键结论必须回查源码、配置、迁移，运行态结论还必须实测。

### 3.3 已核实的源码、配置和迁移范围

已完成直接证据回查的主干包括：

- 前端静态路由与权限守卫：`data-platform-web/src/router/index.ts:9-250`；
- 侧栏菜单与过滤：`data-platform-web/src/views/layout/index.vue:60-234`；
- Token、用户与权限快照：`data-platform-web/src/stores/user.ts:18-80`；
- Axios `/api/v1` 基址、Bearer 注入和 401 处理：`data-platform-web/src/utils/request.ts:5-72`；
- 登录、当前用户和服务端 session 刷新：`AuthController.java:53-150`；
- Gateway 路由：`nacos-config/dev/data-platform-gateway-dev.yml:35-147`；
- OpenAPI API Key 过滤与内部路径封锁：`data-platform-gateway/src/main/java/com/dataplatform/gateway/filter/AuthFilter.java:42-72`、`InternalBoundaryFilter.java:21-31`；
- 下游管理接口 session 验证：`data-platform-common-web/src/main/java/com/dataplatform/common/interceptor/AuthInterceptor.java:27-83`；
- 内部 Feign 服务 JWT 和 scope：`data-platform-common-web/src/main/java/com/dataplatform/common/config/InternalSecurityAutoConfiguration.java:26-66`、`InternalAuthenticationInterceptor.java:35-80`；
- 权限表、基础角色和管理员授权：`sql/migrations/V007__add_permission_tables.sql:5-128`；
- 专项审批角色与权限：`sql/migrations/V027__harden_api_permission_rbac.sql:73-209`；
- 默认审批候选组：`sql/migrations/V037__route_api_permission_to_dedicated_approver.sql:1-76`；
- dev-MVP 夹具中的角色绑定与用户—Caller 绑定：`data-platform-test/test-fixtures/dev-mvp/seed-dev-mvp.sql:14-52,193-200`；
- 接口权限申请/审批/授权：`ApiPermissionApplicationController.java:39-127`、`ApiPermissionTaskController.java:33-79`、`ApiPermissionGrantController.java:36-83`；
- OpenAPI 前置校验、厂商调用、计量计费和调用记录：`OpenApiQueryController.java:96-172`、`OpenApiQueryService.java:65-205,296-353`、`BillingChargeService.java:58-104,222-281`、`CallRecordEventPublisher.java:32-44`；
- 操作日志切面和治理域落库：`data-platform-common-web/src/main/java/com/dataplatform/common/log/OperationLogAspect.java:39-97`、`data-platform-governance/data-platform-governance-api/src/main/java/com/dataplatform/governance/log/api/RemoteOperationLogService.java:25-50`、`data-platform-governance/data-platform-governance-service/src/main/java/com/dataplatform/governance/log/controller/InternalLogController.java:25-46`。

## 4. 当前确认事实与未确认边界

### 4.1 已确认事实

| 事实 | 直接证据 | 状态 |
|---|---|---|
| 当前菜单不是从后端/数据库动态加载，而是前端静态数组后按 session 权限过滤 | `layout/index.vue:60-168,196-234`；路由同样静态声明于 `router/index.ts:9-191` | confirmed |
| 路由守卫在有本地 Token 时拉取 `/auth/userinfo` 刷新权限，所需权限按“任一满足”放行 | `router/index.ts:200-250`；`api/auth.ts:29-31` | confirmed |
| 登录会校验账号、加载有效角色/权限、建立 Sa-Token session，并更新最后登录时间 | `AuthController.java:53-97` | confirmed |
| Gateway 只对 `/openapi/**` 执行 API Key Redis 校验；管理接口的用户 session 校验在下游服务 | `AuthFilter.java:42-72`；`AuthInterceptor.java:27-83` | confirmed |
| Gateway 不暴露 `/internal/**`，内部 Feign 使用目标服务 JWT、audience/scope 和 actor 上下文 | `InternalBoundaryFilter.java:21-31`；`InternalAuthFeignInterceptor.java:20-39`；`InternalAuthenticationInterceptor.java:35-80` | confirmed |
| 接口权限批准会由 Flowable delegate 写入/更新 `api_key_interface` 有效授权并记录申请动作 | `GrantApiPermissionDelegate.java:30-78`；`ApiKeyInterfaceService.java:69-107` | confirmed |
| 一次符合计费条件的 OpenAPI 调用先同步写 BillingEvent/用量/日汇总，再异步发布 CallRecord，Kafka 失败才回退同步落库 | `OpenApiQueryService.java:134-154,168-205`；`BillingChargeService.java:73-104,271-281`；`CallRecordEventPublisher.java:32-44` | confirmed |
| 带 `@OperationLog` 的管理操作通过内部 Feign 写治理域 `operation_log`，失败为 fail-open，仅记录错误 | `OperationLogAspect.java:88-97`；`RemoteOperationLogService.java:44-50`；`InternalLogController.java:25-46` | confirmed |
| 公共接口文档页是 API Key 门户，不依赖用户登录；它走 `/openapi/v1/docs/**`，Access 明确排除用户 session 拦截后再自行验证 API Key | `router/index.ts:179-184`；`views/openapi-docs/index.vue:20-69`；`CallerOpenApiDocumentController.java:50-124`；Access `WebMvcConfig.java:27-39` | confirmed |

### 4.2 已发现但尚未整改的契约/权限风险

本阶段只记录，不修改。以下路径后续仍必须实测，不得因静态盘点已经发现问题而跳过；测试结果按第 1 节的“已知问题/契约缺口”规则单列证据，整套测试完成并经用户统一确认后才进入整改：

1. **管理员菜单使用角色名硬编码。** `layout/index.vue:170-204` 只要角色码为 `admin` 就显示全部菜单；而 `V027:78-85` 明确把 `system:admin` 定义为平台能力并写明禁止角色名硬编码。状态：`contradicted`。
2. **连接器菜单与路由权限不一致。** 迁移菜单可由 `migrate/test/publish/rollback` 任一权限显示（`layout/index.vue:98-106`），但路由只接受 `connector-plugin:view`（`router/index.ts:61-67`）。状态：`confirmed mismatch`。
3. **前端退出未调用服务端 `/auth/logout`。** `layout/index.vue:287-293` 只清本地 store；服务端注销入口存在于 `AuthController.java:100-105`，`api/auth.ts` 没有对应调用。状态：`confirmed`，后续必须验证旧 Token 是否仍有效。
4. **普通用户默认落到无权限 dashboard。** `user` 角色只被授予 `api-permission:view/apply`（`V027:148-158`），但登录/已登录跳转默认是 `/dashboard`（`router/index.ts:229-232`），dashboard 要求 `dashboard:view`（`router/index.ts:22-25`）。状态：`confirmed candidate defect`。
5. **`api_process_admin` 有菜单入口但缺少对应页面能力。** 路由接受 `api-permission:process-view`（`router/index.ts:91-103`），而页面只渲染 `view`、`approve`、`grant-view` 三类页签（`views/api-permission/index.vue:17-228`）；默认页签逻辑会落到并不存在的 grants 页签（同文件 `721-724`）。状态：`confirmed candidate defect`。
6. **关键管理接口存在后端细粒度授权缺口。** 全局 `AuthInterceptor` 只验证登录态并写入 actor/tenant 上下文（`data-platform-common-web/src/main/java/com/dataplatform/common/interceptor/AuthInterceptor.java:27-75`），不会按路由权限码授权。`TenantController.java:29-140`、`VendorController.java:39-161`、`DataTypeController.java:36-154`、`ApiInterfaceController.java:48-175,202-219`、`CallerController.java:28-115`、`ApiKeyController.java:53-220`、`CallRecordController.java:34-118`、`AlertController.java:34-159`、`GraylogController.java:31-120` 和 `LogController.java:28-81` 的所列管理入口未调用 `hasPermission/requirePermission`；接口契约子资源是 `ApiInterfaceController.java:178-199` 中的局部例外。与之相对，用户/角色、接口权限、连接器、计费和厂商配置控制器有显式检查。状态：`confirmed authorization gap`；这意味着后续不能把“菜单隐藏”当成接口不可访问，必须先由用户决定是按现状验收还是整改后再验收。
7. **配置中心前后端使用不同权限词汇。** 路由只要求 `config:view`（`router/index.ts:148-151`），迁移也定义 `config:view/config:edit`（`V007__add_permission_tables.sql:106-107`），但后端 `ConfigController` 查看/修改实际检查 `vendor:view/vendor:edit`（`ConfigController.java:35-44,60-65,165-170`）。状态：`confirmed permission mismatch`。
8. **租户状态契约和开关实现同时漂移。** 前端类型/筛选/表单使用 `active/disabled`（`types/index.ts:36-43`、`api/tenant.ts:4-30`、`views/tenant/index.vue:28-30,56-63,111-115`），后端专用状态接口只接受 `active/inactive/suspended`（`TenantController.java:112-140`）。页面在 `v-model` 已更新后又反转状态，并调用通用 `PUT` 而不是已存在的 `PATCH` 客户端（`views/tenant/index.vue:147,209-214`），因此开关会把目标值翻回旧值。状态：`confirmed request/behavior drift`。
9. **配置中心状态开关同样会反转回旧值。** `v-model="row.isActive"` 已先更新状态，但处理器再次执行 `!row.isActive`（`views/config/index.vue:64-67,246-251`）。状态：`confirmed candidate no-op`，待运行态复现但静态因果已闭合。
10. **多数 CRUD 页只在路由层检查 `*:view`，按钮本身没有 `add/edit/delete` 显隐。** 例如租户页按钮见 `views/tenant/index.vue:9-78`，配置页见 `views/config/index.vue:9-77`。用户/角色后端分别按 `user:*`/`role:*` 拦截并实施租户、自操作、会话失效和平台管理员约束（`UserController.java:46-218`、`RoleController.java:39-185`），但第 6 项列出的控制器没有等价保护。状态：`confirmed inconsistent enforcement`。
11. **调用记录是最终一致。** 浏览器收到成功响应时 Kafka 消费落库可能尚未完成；后续断言必须采用有界轮询，不能立即查不到就判失败。状态：`confirmed`。
12. **历史 dev-MVP 的审批账号是 `admin + api_interface_approver` 双角色。** `seed-dev-mvp.sql:30-52` 不能证明“只有 admin 角色”可命中 V037 的默认审批候选组。状态：`confirmed evidence boundary`。

### 4.3 暂未确认

- 当前运行数据库的实际角色、权限、菜单相关数据和 Liquibase 执行状态；本轮未连接数据库。
- 用户业务口径中的“预期菜单”独立清单；当前静态菜单只能作为候选发现，不能作为期望真相。
- 未列入第一版关键纵切的其余 212 个路由是否还存在细粒度权限缺口；本轮按用户要求不追求穷举。
- 所有前端 API 与后端请求/响应 DTO 的字段级一致性；GitNexus `shape_check` 无有效比较结果，第一版只人工核对了主干和已列异常。
- 当前 dev 服务是否可启动、真实账号是否可登录、Nacos/Redis/Kafka/Flowable/PostgreSQL 是否健康；本轮没有运行态操作。
- 历史浏览器/fixture 证据在当前 HEAD 上是否仍可复现；不能将旧结果标成当前通过。

## 5. 角色—菜单—能力矩阵：当前草案

以下是“当前迁移授权 + 当前前端过滤”推导出的候选可见性，不是用户已经确认的预期菜单。

| 角色 | 当前代码推导的候选菜单/页签 | 当前能力 | 证据状态与问题 |
|---|---|---|---|
| `admin` | 静态侧栏全部菜单 | V007 时点全部权限，后续迁移继续补齐新权限；接口权限全部能力；侧栏直接全显 | `confirmed`；全显依赖角色名硬编码，不等于接口都可成功 |
| `user` | 接口权限审批（我的申请）、数据查询测试、个人中心；公共 API 文档另行用 API Key | 查看/创建/编辑/提交/取消/复制本人申请 | `partially confirmed`；默认 dashboard 跳转冲突，Caller/API Key 前置数据由谁维护待确认 |
| `tenant_admin` | 接口权限审批、数据查询测试、个人中心 | 除紧急授权外的全部 `api-permission:*` | `contradicted/待确认`；角色描述称负责本租户用户，但迁移未授予 `tenant:view/user:view`（`V027:101,160-170`） |
| `api_interface_approver` | 接口权限审批的申请可见、审批待办、授权台账；数据查询测试 | 认领/释放/批准/驳回、租户内台账 | `partially confirmed`；V037 默认候选组指向该角色，需运行态验证候选与租户隔离 |
| `data_security_approver` | 与接口审批员相同 | `view/approve/grant-view` | `partially confirmed`；默认流程是否真正路由到该角色未找到当前独立规则，可能仅供自定义流程配置 |
| `api_process_admin` | 侧栏会显示接口权限审批 | `process-view/process-manage` 后端权限候选 | `contradicted`；当前页面没有流程管理页签或动作，可能出现空页面 |
| `platform_security_admin` | 接口权限审批的授权台账；数据查询测试 | 查看/撤销/紧急授权、流程查看 | `partially confirmed`；页面有撤销和紧急授权控件，运行态和审计未验证 |
| 自定义角色 | 由静态菜单项的一个或多个权限码过滤 | 取决于角色权限快照 | `unverified`；按钮权限与后端接口权限仍需逐项对齐 |

角色定义和授权依据：`sql/migrations/V007__add_permission_tables.sql:67-128`、`V027__harden_api_permission_rbac.sql:97-209`。当前用户信息由 `AuthController.java:121-150` 从数据库重新加载角色/权限并刷新 session，因此最终运行态仍以实际数据库为准。

### 5.1 菜单级能力盘点完成度

| 菜单/页面 | 已从前端确认的动作 | 当前完成度 |
|---|---|---|
| Dashboard | 聚合调用/厂商/Caller 数据、跳转调用记录 | 已确认前端消费者；后端统计口径待逐项核对 |
| 租户 | 列表/查询、新增、编辑、删除、启停 | 后端无 `tenant:*` 检查；状态枚举与开关实现均已确认漂移，`contradicted` |
| 用户 | 列表/查询、新增、编辑、删除、启停、配置角色、关联 Caller | `user:*`、租户范围、自操作保护和会话失效已确认，运行态待验 |
| 角色 | 列表、新增、编辑、删除、启停、配置权限 | `role:*` + `system:admin`、在用角色保护和会话失效已确认，运行态待验 |
| 厂商/数据类型/接口/配置 | 列表/查询、CRUD、启停；接口另有契约、厂商路由、统计、文档 | 厂商/数据类型/接口主入口缺细粒度检查；接口契约有检查；配置中心使用错误的 `vendor:*` 权限且开关候选 no-op |
| Caller/产品/API Key | Caller CRUD/启停，产品新增/编辑，API Key 创建/删除/限流/产品授权/发起接口申请 | 主入口无 `caller:*` 检查；“申请接口权限”跳转审批页；旧直接授权客户端未使用且写接口固定返回 409 |
| 连接器插件 | 列表/版本、导入、验证、预加载、激活、禁用 | 控制器/权限/前端入口已确认；全动作契约矩阵进行中 |
| 连接器迁移 | inventory、prepare、start-observation、observe、complete、rollback | 控制器/前端入口已确认；菜单/路由权限已发现不一致，运行态禁止执行 |
| 接口权限审批 | 我的申请、审批待办、授权台账、紧急授权、撤销 | 前后端和持久化主链已确认；角色期望与 Flowable 运行态待确认 |
| 调用记录 | 查询、维度统计、导出 | Controller 无 `call:view`/租户范围检查；记录异步可见，`confirmed risk` |
| 计费 | 账单/统计/导出、方案版本/校验/模拟/发布、事件/冲正；后端另有对账 | `billing:view/manage/reverse/reconcile/view-all` 和租户范围已确认；对账后端没有当前页面消费者 |
| 监控告警 | 规则 CRUD/启停、记录查看/处理、服务健康检查 | Alert Controller 无 `monitor:*` 检查；主动健康检查是否允许纳入浏览器验收待确认 |
| 灰度发布 | 规则列表/新增/编辑/删除/启停 | `/graylog` 规则契约对齐但后端无 `graylog:*` 检查；config/stream 客户端无后端映射且无页面消费者 |
| 审计 | 列表/详情/统计/导出 | Log Controller 无 `audit:view` 检查；任意已登录会话可达的静态风险已确认 |
| 数据查询测试 | 选择当前用户 API Key，执行真实查询 | 页面无路由权限；是否应对所有登录用户开放待用户确认 |
| 个人中心/公共 API 文档 | 资料/密码；API Key 查看获授权文档 | 链路已确认；密码修改后的旧会话失效和公开入口安全仍待运行态验证 |

### 5.2 最关键的后端授权覆盖矩阵

| 菜单域 | 前端入口权限 | 后端实际检查 | 第一版结论 |
|---|---|---|---|
| 用户/角色 | `user:view` / `role:view`，按钮未细分隐藏 | `UserController` 按 `user:view/add/edit/delete`；`RoleController` 按 `role:view/add/edit/delete` 且写操作要求 `system:admin` | confirmed，一致性较完整 |
| 接口权限审批 | 任一 `api-permission:*` | application/task/grant 控制器分别检查 view/apply、approve/process-view、grant-view/revoke/emergency | confirmed，页面对 `process-view/manage` 不完整 |
| 连接器 | `connector-plugin:*` | 插件绑定/测试/发布/回滚和迁移均显式检查对应权限 | confirmed；迁移菜单、路由和动作需要的权限集合不一致 |
| 计费 | `billing:view` | view/manage/reverse/reconcile/view-all + tenant scope | confirmed；对账没有当前 Web 入口 |
| 配置中心 | `config:view` / 迁移中的 `config:edit` | 实际检查 `vendor:view/vendor:edit` | contradicted |
| 租户、厂商、数据类型、接口主资源 | 对应 `*:view`；页面写按钮多不隐藏 | Controller 主 CRUD 无对应权限检查 | confirmed gap |
| Caller/API Key/调用场景/调用记录 | `caller:view` / `call:view` | Controller 主入口无对应权限检查，部分仅注入当前 tenant/user | confirmed gap；对象级租户隔离也需整改或运行态负测 |
| 监控、灰度、审计 | `monitor:view` / `graylog:view` / `audit:view` | 所列 Controller 无对应权限检查 | confirmed gap |
| 数据查询测试 | 无路由权限 | 仅要求管理 session，并从当前用户关联 Caller/API Key 取选项 | partially confirmed；是否应对所有登录用户开放待用户决定 |

## 6. 契约映射：已闭合主干与进行中范围

### 6.1 已闭合到源码的主干

| 页面动作 | HTTP/网关 | 后端处理与数据归属 | 副作用 | 状态 |
|---|---|---|---|---|
| 用户名密码登录 | Web `POST /api/v1/auth/login` → Identity route | `AuthController.login` → `user_info/user_role/role_permission/permission` → Sa-Token session | 更新最后登录时间 | confirmed |
| 当前用户/菜单刷新 | Web `GET /api/v1/auth/userinfo` → Identity | 重查用户、租户、角色、权限并刷新 session；前端再过滤静态菜单 | 本地 store 权限快照更新 | confirmed |
| 接口权限申请 | Web `/api/v1/api-permission/applications*` → Access | application/item/action 表 + Flowable process/task | `@OperationLog` 远程写治理域 | confirmed |
| 审批批准 | Web `POST .../tasks/{id}/complete` → Access | Flowable 推进 → `GrantApiPermissionDelegate` → `api_key_interface` ACTIVE | action history + 操作日志 | confirmed |
| 公共接口文档 | 公共页 `/openapi/v1/docs/**` → Gateway API Key Redis 校验 → Access | API Key 有效授权 → Masterdata 内部接口契约 | 无管理 session；只读 | confirmed |
| 真实 OpenAPI 查询 | `/openapi/v1/query` → Gateway API Key Redis 校验 → Access | Caller/产品/场景/接口授权/限流/配额 → Masterdata 路由与连接器 → 厂商 | BillingEvent/usage/daily 同步；CallRecord Kafka 异步；指标与缓存按配置 | confirmed at source, runtime unverified |
| 管理操作审计 | 带 `@OperationLog` 的 controller 方法 | common aspect → Governance internal Feign → `operation_log` | 失败不回滚业务 | confirmed at source, runtime unverified |

### 6.2 第一版异常与未穷举边界

| 类型 | 直接证据 | 状态 |
|---|---|---|
| 前端存在、后端不存在 | `data-platform-web/src/api/graylog.ts:26-65` 定义 `/graylog/config*`、`/graylog/stream*`；当前 `GraylogController.java:31-120` 只有规则资源，全仓也没有这些客户端函数的页面引用 | confirmed orphan client contract |
| 后端存在、当前 Web 不消费 | `BillingController.java:114-157` 提供导入、运行、列表和差异对账；`data-platform-web/src/api/billing.ts:1-199` 与 billing 页面没有 reconciliation 调用 | confirmed backend-only capability |
| 客户端模块存在、无页面消费者 | `data-platform-web/src/api/quality.ts`、`trace.ts`、`security.ts` 没有被当前 view/router 引用；相关后端入口存在 | confirmed unwired UI，不能据此判后端废弃 |
| 旧直接接口授权契约 | `api/caller.ts:81-86` 的 API Key—接口客户端无调用方；`ApiKeyController.java:151-163` 的写入口固定返回 409 并要求走审批；Caller 页面实际跳 `/api-permission`（`views/caller/index.vue:448-456`） | confirmed retired write path |
| 请求/行为漂移 | 租户 `disabled` 对后端 `inactive/suspended`，且租户与配置开关均在 `v-model` 后二次反转 | confirmed，见 4.2 第 8、9 项 |
| 菜单/接口权限不一致 | 配置中心 `config:*` 对后端 `vendor:*`；多类 Controller 只有登录校验没有细粒度权限；连接器迁移菜单/路由/动作权限集合不同 | confirmed，见 4.2 和 5.2 |
| 绕过 Gateway | 当前前端请求基址统一为 `/api/v1`，公共文档明确走 `/openapi/v1/docs/**`；本轮未发现前端直连服务端口 | no confirmed frontend bypass；部署网络是否允许直达服务仍 unverified |
| 响应形状 | GitNexus `shape_check` 没有形成可比较 shape；主干人工检查未发现足以宣称全局一致的证据 | partially confirmed；不得把空结果写成通过 |

按用户要求，第一版不穷举全部约 212 个后端路由，也不把 route_map 未识别消费者直接判为孤儿。下一轮只有在用户确认业务地图后，才按所选最小纵切继续补齐字段级 DTO/分页/错误契约。

## 7. 业务对象依赖图与建议顺序

当前源码支持的依赖顺序应修正为：

```text
bootstrap 登录
  → 租户
  → 角色/权限 → 用户 → user_role / user_caller
  → 厂商 → 数据类型 → 接口 + 接口契约
  → 连接器插件固定版本 → 厂商配置/接口路由 → 校验/测试/发布
  → Billing Plan 发布
  → Caller → 产品 → API Key + 产品授权
  → 调用场景
  → 接口权限申请 → Flowable 审批 → api_key_interface 有效授权
  → 公共文档/真实 OpenAPI 调用
  → BillingEvent/Usage/Daily + CallRecord（最终一致）
  → 授权台账/调用记录/计费/对账/监控/操作审计
  → 按反向依赖清理
```

与原候选顺序相比，租户必须先于新用户和 Caller；Billing Plan、Caller 产品、API Key 产品授权、调用场景、接口契约和已发布连接器都必须在真实调用前就绪。审批只解决 API Key—接口授权，不替代产品授权、配额、限流、接口启用、连接器发布或计费方案。

直接证据：`seed-dev-mvp.sql:6-79,180-215` 的建数顺序；`OpenApiQueryController.java:104-165` 的产品/场景/接口授权/限流/配额前置校验；`OpenApiQueryService.java:105-154` 的连接器与计费调用。

## 8. 黄金业务旅程：当前候选

以下仅是待用户确认的第一版，不是已执行结果。

### 旅程 1：平台管理员创建租户用户并交付权限

- 角色：bootstrap 平台管理员 → 新建普通用户。
- 页面顺序：登录 → 租户 → 角色/权限 → 用户创建/配置角色/关联 Caller → 退出 → 新用户重新登录。
- 最终状态：新用户只能看到确认后的菜单，`/auth/userinfo` 返回正确租户、角色和权限。
- 必验副作用：`user_info/user_role/user_caller`、管理操作审计、旧/新 session 行为。
- 当前风险：前端退出不注销服务端 session；普通用户默认 dashboard 无权限。

### 旅程 2：业务管理员配置并发布可调用接口

- 角色：拥有 vendor/datatype/interface/connector/billing 管理权限的业务管理员。
- 页面顺序：厂商 → 数据类型 → 接口/契约 → 连接器插件固定版本 → 厂商配置与主备路由 → validate/test/publish → Billing Plan 发布 → 诊断页。
- 最终状态：接口、契约、发布版本、运行时激活和计费方案互相一致。
- 必验副作用：Masterdata 配置/版本，Access activation/runtime，操作审计，必要的诊断/观察事实。
- 当前风险：连接器菜单与路由权限不一致；生产厂商与隔离 fixture 语义必须分开。

### 旅程 3：申请人申请，专职审批员批准并开通授权

- 角色：普通申请人 → `api_interface_approver`，禁止自审。
- 页面顺序：申请人登录 → 选择自己可管理的 Caller/API Key → 创建草稿/提交 → 退出 → 审批员登录 → 认领/批准 → 申请人重新登录查看授权台账或公共文档。
- 最终状态：申请/条目 EFFECTIVE，Flowable 流程完成，`api_key_interface` ACTIVE。
- 必验副作用：application/item/action、Flowable runtime/history、授权台账、操作日志。
- 当前风险：旧 dev fixture 的管理员是双角色，下一阶段必须使用角色分离账号。

### 旅程 4：获授权 API Key 完成真实调用并形成账务闭环

- 角色：调用方用户/API Key → 运营观察角色。
- 页面顺序：公共文档验证授权 → 数据查询测试或真实 OpenAPI 请求 → 调用记录 → 计费事件/日汇总 → 账单/对账 → 监控。
- 最终状态：响应契约有效，实际厂商和连接器版本可追溯，BillingEvent 与 CallRecord 用同一 requestId 关联，金额/数量符合发布方案。
- 必验副作用：配额与限流、缓存命中语义、BillingEvent/usage/daily、Kafka CallRecord、指标/告警。
- 当前风险：CallRecord 最终一致，需要有界轮询；查询页面使用管理 session 代发 API Key，需区分真实外部调用入口。

### 旅程 5：安全管理员撤销授权并验证负向闭环

- 角色：`platform_security_admin` → 原调用方。
- 页面顺序：授权台账撤销并填写原因 → 原 API Key 再次调用 → 查看授权台账/审计/调用与计费变化。
- 最终状态：授权 REVOKED，后续请求在厂商调用前被 403 拒绝。
- 必验副作用：撤销 actor/reason/action/audit；被拒请求不应新增成功调用或计费事件。
- 当前风险：拒绝请求是否保留独立安全审计/指标尚未确认。

## 9. 后续分阶段计划

| 阶段 | 输入 | 工作与输出 | 验收条件 | 强制停点 |
|---|---|---|---|---|
| P0 第一版盘点收口 | 当前源码、配置、迁移、图谱 | 已形成关键角色/菜单动作矩阵、主干契约、异常清单、对象依赖图和 5 条黄金旅程草案 | 重要结论有文件行号和 confirmed/partial/contradicted/unverified 边界 | **本轮已到停点，停止并等待用户确认** |
| P1 业务语义确认 | P0 草案 + 用户决策 | 冻结角色、预期菜单、动作适用性、账号分离、建数/清理策略、哪些动作允许产生外部副作用 | 用户明确确认第一批角色和最小纵切 | 未确认不得启动浏览器 |
| P2 运行态只读预检 | 用户批准的 dev 环境 | 检查服务/依赖健康、版本、迁移状态、账号可用性；不先写业务数据 | Gateway/六服务/前端/DB/Redis/Kafka/Flowable 健康，测试范围与隔离边界明确 | 任一依赖或凭证异常即停 |
| P3 最小真实浏览器纵切 | P1/P2 通过 | 只执行“登录 → 当前用户 → 预期菜单 → 一个只读列表/详情”并抓取网络/console/服务端证据 | 浏览器、HTTP、鉴权、后端、数据库读证据一致；已知和新发现异常均有复现步骤、预期/实际、影响和证据，不以“已知”为跳过理由 | 用户审阅结果后再扩展写操作 |
| P4 单菜单纵切 | 用户批准的页面清单 | 按依赖顺序逐菜单执行适用动作，逐项验证 DB/缓存/消息/审计；继续覆盖第 4.2 节路径 | 每个动作有可重复步骤、清理方案和副作用断言；已知问题单列但不在本阶段整改 | 每个业务域完成后停点复核 |
| P5 跨角色黄金旅程 | 已验证的单菜单能力 | 执行 3—5 条确认后的跨角色旅程，验证最终一致性和负向路径 | 最终状态、账务、调用记录、审计、监控均闭合 | 未经用户确认不扩大到批量/并发 |
| P6 自动化与治理 | 稳定的人工证据 | 把选择器、断言、等待策略、脱敏报告和失败现场固化为自动化，并汇总“已知问题/契约缺口” | 同一隔离基线可重复；失败不会降级成“无测试成功”；每项缺口具备复现、预期/实际、影响、证据和整改建议 | 先由用户统一确认整改清单；生产环境仍需独立授权与门禁 |

## 10. 风险、阻塞与需要用户确认的语义

进入 P2/P3 前至少需要用户确认：

1. 哪些角色是本轮业务真相：`admin` 是否仍可兼任审批员，还是必须严格分离；`data_security_approver` 和 `api_process_admin` 是否有预期页面。
2. 每个角色的**预期菜单清单**由哪份独立事实源给出；是否允许暂以迁移权限为候选基线。
3. 普通用户登录后的默认落地页应是 dashboard、接口权限申请还是个人中心。
4. `data-test` 是否真的对所有已登录用户开放，公共 API 文档是否属于正式验收入口。
5. Caller 页当前“申请接口权限”已经跳审批页；旧直接接口授权客户端无人使用且后端固定返回 409。是否确认审批流程是唯一写入入口，并把旧客户端列为后续清理项。
6. 主动健康检查、连接器 test/publish/rollback、Billing accrue/reverse/reconcile、紧急授权等高副作用动作，哪些可以进入第一批浏览器验收。
7. 测试数据是复用 dev-MVP 隔离夹具、创建新的浏览器专用隔离数据，还是使用用户指定的现有 dev 数据；清理责任和保留时长如何定义。
8. 是否允许访问真实第三方厂商；若不允许，第一阶段必须显式使用受控 fixture，且结论只能写“dev 隔离链路通过”。

第一版 P0 材料已足以进入用户审阅。已知契约/权限缺口的处理规则已经确认；当前待确认的是预期菜单、角色职责、高副作用动作范围和测试数据策略。下一步是 P1 交互确认；**本轮在此停止，不得自行进入浏览器或运行态写数据阶段**。
