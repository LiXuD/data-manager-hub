# 真实浏览器全链路验收：运行态记录与结果

> 最新运行标识：`dev_mvp_20260831030337_33237`；下文第 1—8 节保留前两轮记录  
> 环境：本机隔离 Dev-MVP；非生产；不使用真实外部付费厂商  
> 状态：第三轮已完成角色分离、当前 UI 暴露菜单的代表性操作、跨角色审批/调用/计费/撤销旅程、P6 回放、证据脱敏和隔离环境清理；仍不等价于生产验收  
> 整改入口：[2026-09-01 真实浏览器验收问题整改方案](2026-09-01-real-browser-acceptance-remediation-plan.md)

## 1. 隔离性与接管基线

- 状态文件：`data-platform-test/test-fixtures/.runtime/dev_mvp_20260830161450_21888/fixture.env`。
- 数据库：`dataplatform_dev_mvp_20260830161450_21888_regression`；默认 `dataplatform` 库仅被枚举确认，未读取或写入业务数据。业务写入只发生在本隔离库，并通过真实浏览器 UI 触发。
- Nacos 命名空间：`dev-mvp-20260830161450-21888`；当前 6 个实例均登记为 `127.0.0.1`、healthy、enabled。
- 共享 `dmh-local-postgres`、`dmh-local-redis`、`dmh-local-kafka`、`dmh-local-nacos` 容器保持运行，未重建或清理。
- 隔离库 `databasechangelog` 共 30 条已执行变更，最新包含 `bind-call-record-interface-identity-2026-08-28`；状态文件声明 schema `V052`。6 个 JDBC 连接均在该隔离库中。
- 重启使用项目 `start-services.sh` 的受限启动路径，显式跳过构建、迁移和 Nacos 配置发布，仅为当前进程设置 `SPRING_CLOUD_NACOS_DISCOVERY_IP=127.0.0.1`；共享基础设施未重建。

## 2. 已完成的正向最小纵切证据

### 2.1 真实用户名密码登录与身份刷新

- 既有 Playwright 会话：`dmh-br-e2e-21888`（headed，未注入 storage state）。
- 浏览器网络记录：`POST /api/v1/auth/login` 为 `200`，随后 `GET /api/v1/auth/userinfo` 为 `200`。
- `userinfo` 实际返回隔离管理员、租户 `Dev MVP Tenant`、角色 `admin, api_interface_approver` 和 62 项权限；其中包括 `call:view`、`dashboard:view`、`system:admin`、`api-permission:*` 与连接器权限。
- 隔离库中该管理员状态为 `active`，`last_login_time` 与浏览器返回时间一致。
- 链路为浏览器 `127.0.0.1:3000` → `/api/v1` Vite 代理 → Gateway `:8888` → Identity `:8086`；未 mock、未直接服务端登录。

### 2.2 代表性只读菜单：调用记录

- 浏览器当前页：`/call`，页面标题为“调用记录”，侧栏按管理员权限渲染 11 个菜单入口。
- 浏览器通过真实请求获得 `GET /api/v1/call-record/dimension-stats`、`stats`、`list` 等 `200` 响应；页面显示 0 条、总调用次数 0，与隔离库 `call_record` 当前为空一致。
- 该轮原始 Playwright profile、snapshot 与 trace 后续敏感扫描发现包含测试凭据/授权材料，已按证据安全门禁整体移除；本节只保留当时已核验的状态结论，不再提供原始可复用会话材料。

### 2.3 申请人菜单与受限路由

- 独立 headed 会话 `dmh-br-e2e-applicant-21888` 使用真实 fixture 账号登录；`POST /api/v1/auth/login`、`GET /api/v1/auth/userinfo` 均为 `200`，身份为 `user`，权限为 `api-permission:view/apply`。
- 登录后侧栏只有“接口权限审批”和“数据查询测试”。从地址栏直接打开 `/tenant` 后仍停留在 `/profile?forbidden=1`，未发起租户页面请求；这证明前端路由守卫有效，但没有替代后端控制器级授权测试。
- `/api-permission` 列表、`/api/v1/api-permission/applications?scope=mine&page=1&pageSize=20` 均为 `200`；接口权限申请页无 console error，仅保留既有 Vue 图标模板 warning。

### 2.4 运行时注册修复与复测

- 接管时发现旧浏览器复测的 500 根因是 Nacos 六实例均登记旧地址 `192.168.31.110`，Gateway 到 Identity 的连接超时；该现场不能作为错误密码逻辑结论。
- 按受限启动路径重启后，8081/8082/8084/8085/8086/8888 的 `/actuator/health` 均返回 `status=UP` 且包含 `liveness/readiness`；Nacos 服务清单为 6 个，实例全部为 loopback healthy/enabled。
- 复测错误密码得到浏览器 `POST /api/v1/auth/login` HTTP `200`，响应 envelope `code=401,message=用户名或密码错误`，页面停留登录页；Identity `HttpLoggingFilter` 已记录同一请求，说明请求真正到达认证服务。
- 管理员复登后 `/dashboard` 与 `/call` 正常；调用记录的维度统计、列表等请求均 `200`，页面显示 0 条。

## 3. 运行时负向测试与边界

### 3.1 错误密码

- 步骤：新 headed 浏览器会话打开真实 `/login`，输入隔离管理员用户名和明确错误的测试密码，点击“立即登录”。
- 预期：Identity 的 `AuthController.login` 对错误凭据返回业务 `401`（源码返回“用户名或密码错误”）。
- 初次复测实际为 Gateway HTTP `500`，原因是 Nacos 注册漂移到 `192.168.31.110:8086`，未到达 Identity；该次证据已归类为运行时接管失败，不作为业务结论。
- 修复注册后复测实际为 HTTP `200` + 业务 `code=401`，页面仍在登录页，Identity 已记录 `POST /auth/login`；错误密码路径通过。
- 证据处置：该轮原始 trace/profile 在最终敏感扫描中命中测试密码、授权头和 API Key，已删除；错误密码的 HTTP/业务码和服务端到达结论保留在本报告，不能再从仓库取回原始会话。

### 3.2 有效隔离账号复登

- 为避免凭据误判，使用 fixture 中的隔离申请人和管理员凭据，在两个独立 headed 浏览器会话中复登；两次均完成真实 Gateway→Identity 链路。
- 申请人最终到达 `/profile?forbidden=1`，菜单与权限快照符合 `user` 账号；管理员最终到达 `/dashboard`，随后打开 `/call` 并读取统计/列表。
- 旧地址漂移已修复为 loopback 注册；当前不能据此推断生产网络或生产 Nacos 配置已经验证。

## 4. P4 代表性申请权限最小可回滚纵切

- 建数只使用隔离 Dev-MVP UI：管理员在“内部系统管理 → 产品”下新增产品 `br-e2e-21888`，真实请求 `POST /api/v1/caller/1/products` 返回 `200`；随后新增临时 API Key `br-e2e-21888-key` 并授权该产品，真实请求 `POST /api/v1/caller/apikey` 返回 `200`。API Key 值未写入本报告。
- 申请人选择真实隔离 Caller、API Key 和启用接口 `PROGRAMMER_HISTORY_BY_DATE`，填写业务用途、业务场景和未来截止时间，通过“保存草稿”触发 `POST /api/v1/api-permission/applications`，HTTP `200`，服务端产生 application、item、CREATE action；“详情”页显示三者关联和 `DRAFT` 状态。
- 申请人通过 UI“取消”并确认，触发 `POST /api/v1/api-permission/applications/1/cancel`，HTTP `200`；列表变为“已取消”，响应为 `status=CANCELED,version=1`，服务端产生 CANCEL action。随后管理员通过 UI 删除临时 API Key，`DELETE /api/v1/caller/apikey/1` 返回 `200`，Key 变为软删除。
- 清理前隔离库核验：`caller_product=1`（产品页没有删除动作）、`api_key=1` 且 `deleted=true`、`api_key_product=1`、`api_permission_application=1`/`item=1`/`action=2`；`call_record=0`、`billing_event=0`，无真实外部调用或计费副作用。`operation_log` 从基线 4 增至 8，包含新增/删除 Key、创建草稿、取消申请四项审计记录；随后已按授权脚本删除整个隔离库。
- 该纵切覆盖浏览器表单、Vite `/api/v1` 代理、Gateway、Access、数据库和治理审计，并通过业务取消动作回滚到不可提交状态；未执行提交、Flowable 审批、授权、真实 OpenAPI 调用、计费或撤销旅程。
- 证据处置：Access/管理员日志中的创建、取消、删除结论已写入本报告；原始 trace、snapshot、截图随含敏感会话的历史 profile 一并移除，不能作为长期保留件。

## 5. P0—P6 进度结论

| 阶段 | 当前结论 | 说明 |
|---|---|---|
| P0 | 已完成 | 角色、菜单、契约缺口和对象依赖以既有盘点文档为边界；不把静态图谱当运行态通过。 |
| P1 | 已按本次授权冻结 | 使用隔离 fixture、真实账号和 headed 浏览器，覆盖当前 UI 暴露菜单与确认的业务旅程；未扩大到生产或真实付费厂商。 |
| P2 | 通过 | 六服务、前端、PostgreSQL、Redis、Kafka、Nacos 可用；迁移为 V052，使用隔离库。 |
| P3 | 通过（修复接管问题后） | 登录、userinfo、角色菜单、调用记录列表和申请列表均有浏览器/HTTP/服务端证据；Nacos 注册漂移被识别并恢复。 |
| P4 | 已完成本轮确认范围 | 第三轮按当前 UI 暴露能力覆盖管理员全部菜单基线及适用的新增、编辑、启停、查看、删除；无编辑/删除入口、运行失败或契约漂移均保留为结果，不伪造通过。 |
| P5 | 已完成本轮确认范围 | 完成分离申请人 → 分离审批人 → 真实查询 → CallRecord/BillingEvent/审计，以及分离安全角色撤销授权 → 原 Key 403 且无新增成功计费；并覆盖计费冲正和连接器保护性负向路径。 |
| P6 | 稳定回放已通过 | 浏览器脚本能按当前 ACTIVE 授权选择风控或信贷基线，真实登录、拉取菜单/契约、执行查询并有界轮询 CallRecord/BillingEvent；最终第三轮回放为 `credit-fallback`，结果通过。 |

## 6. 上一轮 P4 处置与清理状态

- 已执行项目清理脚本 `cleanup-dev-mvp.sh <fixture.env> --keep-output --stop-runtime`：仅删除 `dev_mvp_20260830161450_21888` 隔离数据库，停止本次六服务、前端和 fixture HTTPS 进程，运行目录保留。
- 清理后复核：3000、19888、8081、8082、8084、8085、8086、8888 均无监听；PostgreSQL 仅保留默认 `dataplatform` 库；`dmh-local-postgres`、`dmh-local-redis`、`dmh-local-kafka`、`dmh-local-nacos` 仍为 healthy；四个 Playwright 会话均已关闭。
- 报告、服务日志和 Playwright artifacts 已保留；未修改业务代码、业务配置或迁移文件。
- 已知的第 4.2 节契约/权限缺口未修改、未排除。后端控制器级越权、申请提交/审批、真实 OpenAPI、计量计费、最终一致 CallRecord、监控告警和生产部署边界仍未验证。

## 7. 新鲜隔离运行：P5/P6 结果（2026-08-31）

### 7.1 Fixture 与隔离边界

- 新建运行标识：`dev_mvp_20260831013029_8542`；数据库为 `dataplatform_dev_mvp_20260831013029_8542_regression`，状态文件位于 `data-platform-test/test-fixtures/.runtime/dev_mvp_20260831013029_8542/fixture.env`。
- `verify-dev-closure.sh --keep-running` 在 V052、`pendingMigrations=0` 下通过；隔离 seed 为 vendor `3` / caller `2` / data type `2` / interface `2` / connector config `3`，六服务健康接口均为 200，前端为 `http://127.0.0.1:3000`。
- 浏览器专用产品为 `BR_E2E_RISK_20260831013029_8542`、`BR_E2E_CREDIT_20260831013029_8542`；专用 API Key 数据库 ID 为 `3`、`4`，Key 值不写入报告。默认 `dataplatform` 库和共享 `dmh-local-*` 容器不在写入范围内。

### 7.2 P5 申请、审批与真实调用

- 管理员真实登录后，在“内部系统管理”通过 UI 创建两套本次运行的产品/API Key；申请人真实登录后在“接口权限审批”创建并提交风控申请 `APA202608310953282FED275A`，页面从“审批中”进入“已生效”。
- 管理员在“审批待办”真实认领并办理该申请，审批意见为本次运行标识对应的批准意见；“授权台账”显示风控 Key、工商信息接口、审批授权、有效和未来截止时间。
- 申请人“数据查询测试”先执行空参数请求，浏览器捕获 HTTP 400 `companyName不能为空`；随后输入 `{"companyName":"browser-p5-ui-query"}`，捕获 HTTP 200、signed fixture connector 成功和费用 `¥0.25`。后续复核已更正原诊断：fixture 契约本身已声明必填 `companyName`，空参数 400 是正常契约校验；实际缺口是普通申请人读取管理端契约需要 `interface:view`，无该权限时页面把 403 错误降级显示为“未配置调用参数”。
- 申请人浏览器进一步通过前端 Vite 代理执行真实 `POST /openapi/v1/query`，HTTP 200；请求 trace 为 `browser-p5-gateway-20260831013029-8542`，平台 request ID 为 `req_9bee2db893cc4cb5`，返回 fixture connector 成功、响应契约有效、费用 `0.25`。
- 隔离库核验：该浏览器 Key 的 `call_record` 新增行包含同一 request/trace、run-specific API/product/scene、`success=true`、`cost=0.2500`、`e2e-signed-connector:1.1.0`、pipeline `1` 和 `response_contract_valid=true`；对应 `billing_event` 为同一 request ID 的 `USAGE`、`billable=true`、quantity `1`、`0.25000000`、`POSTED`。申请/条目/动作、Flowable grant 和操作日志也已落库。

### 7.3 关键负向路径

- 申请人用本次运行信贷 Key 创建并提交个人信息接口申请 `APA20260831101701627A0578`；管理员真实认领后通过 UI 驳回，申请人刷新后显示“已驳回”。数据库状态为 application `REJECTED`、item `REJECTED`、无 grant，action 为 USER `CREATE` → `SUBMIT` → admin `REJECT`。
- 在驳回后，申请人仍从真实查询页选择该 Key、产品、个人信息厂商/数据类型/接口和场景，执行查询得到浏览器 HTTP 403 `API Key没有访问该接口的权限`；本次没有新增成功调用或计费事实。该路径覆盖了审批拒绝后的权限闭环；授权台账撤销旅程未执行。
- 本节 P5 的脱敏 DB/HTTP 汇总证据保存在本地 `output/playwright/br-e2e-dev-mvp-20260831013029-8542/p5-db-evidence.md`，浏览器快照、trace、network 和 console 保存在 `output/playwright/br-e2e-dev-mvp-20260831013029-8542/`；运行证据不随源代码提交。

### 7.4 P6 稳定回放

- 按计划先检查了现有 `verify-dev-closure.sh`、`run-dev-mvp.sh`、Web Vitest 与 data-test/API 权限测试；对两个既有 Dev-MVP 脚本执行 GitNexus upstream impact，结果均为 `LOW`、直接调用方 `0`，因此新增测试夹具脚本，不改业务代码和既有业务测试。
- 新增 [run-browser-e2e.sh](../data-platform-test/test-fixtures/dev-mvp/run-browser-e2e.sh)。它只接受经过 V052/隔离库校验的 fixture 状态文件，使用 Node `v22.19.0`/npm `10.9.3` 和 Playwright CLI，执行真实申请人/管理员登录、角色菜单、授权台账、已知空参数失败、参数化成功查询，并对 `CallRecord/BillingEvent` 做最多 30 秒有界轮询；任何断言失败都会保留脱敏 failure snapshot/network/console 并返回非零。原始 trace 因可能包含凭据和 session，不作为持久化证据。
- P6 回放通过，报告为本地 `output/playwright/dmh-p6-dev_mvp_20260831013029_8542-1788144628/browser-e2e-summary.txt`：baseline 风控 Key 的成功 `call_record`/`USAGE billing_event` 从 `5/5` 增至 `6/6`，费用为 `0.25`。对应 `output/playwright/dmh-p6-dev_mvp_20260831013029_8542-1788144628/` 保留脱敏 snapshot、network、console 和失败现场机制验证；运行证据不随源代码提交。

### 7.5 未验证边界

- 本轮只完成已授权的申请→审批→调用主链和拒绝负向路径；没有执行授权撤销、全量菜单 CRUD、租户/用户交付旅程、生产环境、真实第三方付费厂商或生产部署/回滚门禁。
- P6 固化的是稳定的只读浏览器回放和查询闭环，不把管理员写操作、所有五条候选旅程或生产验收冒充为已自动化；审批写操作已有本轮人工浏览器证据。

## 8. 本轮新运行清理状态

- P5/P6 测试和证据收集完成后，已关闭本轮 Playwright 会话，并执行 `cleanup-dev-mvp.sh <fixture.env> --keep-output --stop-runtime`。
- 清理仅删除 `dev_mvp_20260831013029_8542` 隔离数据库，停止本轮六服务、前端和 fixture HTTPS 进程；运行目录、报告与证据均保留。
- 清理后复核：隔离数据库已不存在，默认 `dataplatform` 库仍保留；3000、19888、8081、8082、8084、8085、8086、8888 均无监听；`dmh-local-postgres`、`dmh-local-redis`、`dmh-local-kafka`、`dmh-local-nacos` 仍为 healthy。

## 9. 第三轮全菜单代表性验收（2026-08-31）

### 9.1 Fixture、角色与证据边界

- 运行标识：`dev_mvp_20260831030337_33237`；隔离数据库为 `dataplatform_dev_mvp_20260831030337_33237_regression`，模式为 V052，`databasechangelog` 30 条且无待执行迁移。
- 六个后端服务、Gateway、前端和 fixture HTTPS 端点均在本轮隔离运行中真实启动；所有账号均通过页面填写用户名/密码登录，不注入 Token、storage state 或 mock。
- 角色分离为管理员、普通申请人、审批人、安全角色四个浏览器会话；管理员退出前已通过 UI 点击“退出登录”。
- 页面快照、请求状态、网络、console 和脱敏数据库汇总位于 `output/playwright/br-full-dev-mvp-20260831030337-33237-*/`；最终 P6 证据位于 `output/playwright/dmh-p6-dev_mvp_20260831030337_33237-1788164628/`。原始 trace/profile 不作长期保留。
- 脱敏数据库汇总见本地 `output/playwright/br-full-dev-mvp-20260831030337-33237-admin/p4-p6-final-db-evidence.md`；运行证据不随源代码提交。

### 9.2 当前 UI 暴露能力的页面覆盖

| 业务域/菜单 | 本轮真实页面操作 | 结果 |
|---|---|---|
| 登录、首页、个人中心 | 正确凭据登录、错误凭据、userinfo、角色菜单、退出登录 | 登录/身份刷新通过；普通用户默认跳转和服务端登出存在缺口，见第 10 节 |
| 租户 | 列表、新增、编辑、状态开关、删除 | CRUD 请求与软删除通过；状态开关复现前端反转/枚举漂移 |
| 用户、角色 | 用户新增、角色分配、调用方关联、用户登录、角色新增/编辑/权限分配/启停、删除 | 页面与数据库结果闭合；隔离对象已通过 UI 删除 |
| 厂商、数据类型、接口 | 新增、编辑、启停、详情、契约、统计、文档、路由、连接器工作区、删除 | 主体 CRUD 通过；接口契约与连接器表单缺口单列 |
| 调用方、产品、API Key | 调用方新增/编辑/启停，产品新增/编辑，Key 新增、产品授权、限流、接口权限 | 页面请求、授权关系和后续真实调用闭合；密钥值未保留 |
| 调用记录 | 列表、统计、详情、导出 | 与 `call_record` 成功/失败事实一致 |
| 调用场景 | 列表、新增 | 页面没有编辑/删除入口，未冒充 CRUD 完成 |
| 配置中心 | 新增、编辑、状态开关、删除 | 新增/编辑/删除通过；状态开关复现反转/no-op |
| 灰度发布 | 规则新增、编辑、启停、删除 | 通过；清理前表内 0 条 |
| 监控告警 | 健康列表、立即检查、规则新增/编辑/启停/删除、告警记录与图表 | 页面链路通过；本轮没有生成告警记录 |
| 审计日志 | 列表、统计、详情 | 清理前 162 条，153 success / 9 fail；失败路径同样保留 |
| 计费 | 计费事件、详情、导出、报表、计提、冲正；方案新增/编辑/模拟/发布/删除 | 计费事实和冲正闭合；方案发布返回 500，草稿随后由 UI 删除 |
| 连接器、插件、迁移、诊断 | 校验、测试、发布、版本历史、插件详情/实例/禁用、重复导入、迁移盘点/准备/观察、当前/历史执行计划 | 正向读写及保护性 409 有证据；简单草稿保存与迁移观察存在运行态缺口 |

### 9.3 跨角色业务旅程

1. 管理员建立租户/用户/角色/调用方/产品/API Key/接口关系，普通用户用真实账号登录并获得与角色一致的菜单与页面能力。
2. 普通申请人创建并提交接口权限申请，分离审批人真实认领并批准；授权台账产生 ACTIVE grant，随后真实页面查询经过前端代理、Gateway、服务、签名 fixture 连接器，形成成功响应、`CallRecord`、`USAGE BillingEvent` 和审计记录。
3. 分离安全角色撤销已生效的风控授权；同一旧 Key 再查询返回 403，成功调用和计费数量不增加。申请审批事实保持 EFFECTIVE，实际授权事实变为 REVOKED。
4. 计费页面对已计费事件执行冲正，数据库新增独立 `REVERSAL / POSTED / -0.25` 事实，原 USAGE 不被篡改。
5. 连接器发布版本可用于真实查询；已发布版本重复发布、活动绑定插件禁用和相同版本重复导入均被 409 保护。MUST_REMAIN_LEGACY 配置仍可创建 PREPARED 任务，但启动观察返回 `CONNECTOR_MIGRATION_TARGET_NOT_SIMPLE`。

### 9.4 P6 最终回放

- 最终执行目录中的摘要为本地 `output/playwright/dmh-p6-dev_mvp_20260831030337_33237-1788164628/browser-e2e-summary.txt`；运行证据不随源代码提交。
- 因风控基线授权已按 P5 旅程撤销，脚本自动选择仍为 ACTIVE 的信贷 Key，`target_profile=credit-fallback`。
- 成功 `call_record` 和 `USAGE billing_event` 均由 1 增至 2；最新平台 request ID 为 `req_2609c7962ef84bdb`，费用 0.50，Billing 状态 POSTED。
- 浏览器网络中 userinfo、列表、选项、契约请求和 `POST /api/v1/data-test/query` 均为 200；页面显示“查询成功”、费用 0.50 和 `e2e-signed-connector`。
- 其中契约请求 200 依赖本轮为申请人临时补授 `interface:view` 后的复测，只证明契约存在且页面可渲染字段，不代表普通申请人的最小权限模型已经闭合；整改不得把该管理权限固化给申请人。
- console error 为 0；仍有 3 条既有 Vue runtime compiler warning，作为缺口保留。
- P6 通过后，最终敏感扫描证明原始 trace 会记录凭据/授权材料，因此仅删除脚本中的 tracing start/stop，并新增“不保留 trace”的说明；成功查询、断言、轮询和退出逻辑未变。这个安全收尾版本已通过 `bash -n` 与 `--help`，因隔离环境随后已清理，没有再执行一次完整浏览器回放；本节的运行态通过结论对应安全收尾前、业务逻辑相同的版本。

## 10. 已知问题、契约缺口与保护性失败

本节按既定规则只记录，本轮未修改业务代码。问题范围、优先级、实施顺序、验证门禁和产品停点已集中到 [整改方案](2026-09-01-real-browser-acceptance-remediation-plan.md)；实际修复仍应在独立小提交中实施。

| 项目 | 预期 | 实际与影响 | 证据/结论 | 建议 |
|---|---|---|---|---|
| 管理员菜单依赖角色名 | 按能力权限渲染 | `admin` 角色码直接放开全部菜单，角色重命名或等价管理员角色会漂移 | 源码确认；管理员菜单运行态基线已采集 | 改为 `system:admin` 或统一能力模型 |
| 连接器菜单/路由权限集合 | 菜单可见即可进入路由 | 菜单和路由使用不同权限集合；特定组合可能看到菜单却被路由拒绝 | 源码确认；未构造该单一权限组合 | 共享一份权限声明并补组合测试 |
| 前端退出登录 | 同时注销服务端 session | 只清本地状态并返回 `/login`；没有 `/auth/logout` 请求或审计，旧 Token 服务端有效性未重放 | UI 实测；logout 审计命中 0 | 调用服务端 logout，并补旧 Token 失效断言 |
| 普通用户默认落点 | 登录后进入首个有权页面 | 先落到无权 dashboard，再转 `/profile?forbidden=1` | 分离普通用户实测 | 按权限计算默认首页 |
| `api_process_admin` 页面能力 | 角色对应明确页签 | 路由允许 `process-view`，页面没有同名能力分支 | 源码确认；未单独构造该角色 | 明确产品语义并增加专属页签/重定向 |
| 后端细粒度授权 | 隐藏菜单对应接口也拒绝越权 | 多个管理 Controller 只有登录校验；菜单隐藏不能证明后端安全 | 源码确认；本轮仅覆盖部分受限路由，不宣称全接口负向完成 | 在服务端统一 `requirePermission`，补直连接口矩阵 |
| 数据测试页契约权限耦合 | 申请人只能读取其可用 Key 已获授权接口的调用契约 | 页面复用管理端 `/interface/{id}/contract`；无 `interface:view` 时返回 403 并被页面误显示为无参数，补授后才显示既有 `companyName` | seed、fixture 断言、403/补授后快照和源码共同确认；原“契约未声明”诊断已更正 | 在 Access 提供校验用户、租户、Key 和 ACTIVE grant 的 data-test 契约入口，经 Masterdata API Feign 读取；前端失败关闭 |
| 配置中心权限词汇 | 前后端使用 `config:*` | 后端实际检查 `vendor:*` | 源码确认；管理员路径可用，单一配置角色未隔离验证 | 统一权限码并迁移角色授权 |
| 租户状态开关 | 一次点击持久化目标状态 | `v-model` 后再次反转，且 `disabled` 与后端 `inactive/suspended` 漂移 | 页面实测复现 | 统一枚举并只提交一次目标值 |
| 配置状态开关 | 一次点击切换并落库 | 处理器二次取反，表现为 no-op/回旧值 | 页面实测复现 | 移除二次反转，增加状态断言 |
| CRUD 按钮权限 | 按 add/edit/delete 能力显隐并由后端兜底 | 多页只按 view 进路由，按钮无动作级显隐；部分后端也无细粒度校验 | 源码和角色菜单证据 | 前后端同时实施动作级授权 |
| CallRecord 最终一致性 | 成功响应后最终可查 | Kafka 异步落库，立即读取可能短暂为空 | P5/P6 已用 30 秒有界轮询稳定通过 | 保留有界轮询，超时保存链路证据 |
| 历史审批账号角色耦合 | 独立审批角色可完成审批 | 旧 fixture 为 admin+approver 双角色，历史证据不独立 | 第三轮分离审批人已完成认领/批准，证据边界已闭合 | 持续保留分离角色 fixture |
| Vue 图标模板 | 浏览器无 runtime compiler warning | 关键页面持续出现 3 条 warning，虽未阻断请求但污染 console 门禁 | P6 console error 0、warning 3 | 改用组件渲染并将 warning 纳入断言 |
| 简单连接器草稿保存 | 合法 Schema 表单可保存 | 两次返回 400；tokenEndpoint 被当成 secret/空选项默认值不合法 | UI 请求与响应快照 | 修正 Schema/secretRef 语义和空值归一化 |
| 已发布连接器重复发布 | 明确幂等或受控拒绝 | 返回 409，未破坏活动版本 | 保护性失败，行为合理 | UI 提前禁用并解释状态 |
| 活动插件禁用/重复导入 | 不破坏活动绑定且版本不可覆盖 | 两条路径均返回 409 | 保护性失败，行为合理 | 保留后端保护，优化前端提示 |
| 迁移准备与可迁移性 | 不可迁移对象在准备前被阻止 | MUST_REMAIN_LEGACY 仍能创建 PREPARED；开始观察才失败 | UI/DB 实测，任务停在 PREPARED | 在 prepare 前执行相同可迁移性校验 |
| 计费方案发布 | 合法草稿可发布或返回可诊断业务错误 | 发布返回 500；草稿可删除，未污染 ACTIVE 方案 | 页面、响应、数据库实测 | 定位服务异常并改为结构化业务错误 |
| 调用场景 CRUD | 基础字典具备新增、编辑、删除 | 当前页面仅有列表和新增 | UI 实测 | 确认产品口径后补编辑/删除或明确只增模型 |

## 11. 第三轮清理与未验证边界

- 当前状态：证据采集和脱敏完成后，已关闭全部 Playwright 会话，并执行 `cleanup-dev-mvp.sh <fixture.env> --keep-output --stop-runtime`。该脚本只删除 `dataplatform_dev_mvp_20260831030337_33237_regression`、停止本次服务/前端/fixture HTTPS，保留运行目录用于诊断。
- 清理复核：3000、19237、8081、8082、8084、8085、8086、8888 均无监听；没有本项目 Java/Vite/fixture HTTPS 进程；隔离数据库不存在，默认 `dataplatform` 仍存在；`dmh-local-postgres`、`dmh-local-redis`、`dmh-local-kafka`、`dmh-local-nacos` 均保持 healthy。
- 证据安全处置：删除 23 个含完整 API Key、签名正文、密码输入或潜在 secret 值的临时快照/命令日志；删除三处 Playwright 完整 profile/cache，共 295,248 个文件、约 7.5 GB，其中包括 11 个命中测试密码、授权头或 API Key 的原始 trace。删除内容只能通过重新执行隔离测试生成，不能从当前工作树恢复。
- 不包含：生产/预生产部署门禁、真实第三方付费厂商、并发/容量/长稳、灾备/回滚演练、告警通知渠道、所有 212 个路由的逐接口越权矩阵，以及登出后旧 Token 的直接重放。
- 本轮没有修改业务源码、数据库迁移或业务配置；新增内容仅为测试夹具脚本、计划/结果文档和 `output/playwright` 证据。
