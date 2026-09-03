# 真实浏览器全链路验收问题整改方案

> 日期：2026-09-01  
> 适用基线：`dev` / `d4d56bf6954a2827c8d86223ecc211039debf7a9` 及其后续修复分支  
> 状态：截至 2026-09-03，代码、前端、V060 前向迁移、测试和本地 Dev 运行态整改已实施并复审；合并后对抗性审查发现的四类缺陷已按功能点修复，远端交付仍以最终 PR 精确 SHA 的 `CI / required-ci` 为门禁，调用场景产品决策及 staging/production 门禁仍未宣称完成
> 证据入口：[验收结果](2026-08-31-real-browser-end-to-end-acceptance-results.md) / [历史执行计划](2026-08-30-real-browser-end-to-end-acceptance-inventory-plan.md)  
> 全局待办：以 [`PENDING_TASKS.md`](../PENDING_TASKS.md) 为准；本文是本轮问题的唯一详细整改设计

关键脱敏证据保存在本地 `output/playwright/br-full-dev-mvp-20260831030337-33237-admin/` 运行目录中，包括最终数据库汇总、计费发布 500 响应、连接器第一次 400 请求/响应、敏感扫描和清理核验。这些文件只支持 2026-08-31 隔离运行结论，不包含已删除的原始凭据、profile 或 trace；运行证据不随源代码提交。

本轮复审证据由本地 `data-platform-test/test-fixtures/.runtime/dev-mvp-latest-report.json` 和 `output/playwright/dmh-p6-dev_mvp_20260902100949_4309-1788343918/browser-e2e-summary.txt` 提供：报告显示 `status=passed`、`pendingMigrations=0`、六服务健康和 3/2/2 业务事实；浏览器摘要显示四角色真实登录、参数化调用、CallRecord/Billing 增量、UI 登出、console error/warning 为 0，敏感扫描和受保护清理通过。运行态数据库和进程已按 fixture state 清理，证据目录保留脱敏摘要且不随源代码提交。

## 1. 总体判断与整改边界

本轮真实浏览器验收已经证明 `dev` 核心业务链可运行：四角色真实登录、申请、分离审批、授权、真实调用、`CallRecord`、`BillingEvent`、撤销后 403、计费冲正和 P6 自动回放均有运行态证据。已发现的问题不能反向把这一结论改写为“Dev MVP 整体失败”，但也不能把前端菜单隐藏、局部 409 保护或一次成功回放当作完整安全与生产就绪证明。

整改分为四类：

1. **P0 安全闭环**：后端细粒度授权、租户对象归属、数据测试页契约最小权限、服务端登出。
2. **P1 功能正确性**：菜单/路由/按钮权限一致性、权限码漂移、状态开关、连接器草稿、迁移准备、计费发布。
3. **P2 产品与质量**：Vue runtime warning、调用场景生命周期、保护性 409 的前端表达、异步可观测性。
4. **重放与发布门禁**：以安全收尾后的当前脚本重新跑 fresh 隔离浏览器链路，并等待最终 SHA 的远端 `CI / required-ci`。

本文不授权直接修改生产数据、删除历史迁移事实、扩大普通用户权限、弱化连接器 Secret 校验，也不把 staging/production、真实付费厂商、容量/长稳、灾备/回滚或告警通知渠道标记为已验证。

## 2. 事实更正与证据可信度

### 2.1 `companyName` 不是“契约未声明”

验收结果原先把空参数 400 描述为“接口契约未声明参数、连接器实际要求 `companyName`”。复核后该诊断不成立：

- `data-platform-test/test-fixtures/dev-mvp/seed-dev-mvp.sql` 已向 `interface_param` 写入必填请求字段 `companyName`。
- `data-platform-test/test-fixtures/dev-mvp/run-dev-mvp.sh` 已断言契约的 `requestFields` 包含该字段。
- 普通申请人没有 `interface:view` 时，数据测试页调用管理端 `GET /api/v1/interface/{id}/contract` 返回 403，页面随后错误显示“当前接口未配置调用参数”。
- 临时补授 `interface:view` 后，同一页面能显示“1 个参数 / 企业名称 / 必填 / `companyName`”。

因此真正问题是**数据测试能力错误依赖接口管理权限**。空参数调用返回 `companyName不能为空` 是契约校验正常工作，不应通过放宽校验或修改 seed 处理。

### 2.2 证据分层

| 结论类型 | 当前证据 | 使用边界 |
|---|---|---|
| 源码事实 | 当前工作树的 Controller、Service、Vue、TypeScript、fixture 与迁移代码 | 可用于定位候选根因；实现前仍须重新执行 GitNexus impact |
| 运行态事实 | 2026-08-31 与 2026-09-03 隔离 Dev-MVP 的浏览器、HTTP、脱敏数据库和清理证据 | 2026-09-03 当前工作树的 fresh V060/P6 已重新运行并通过；不代表 staging/production 已验证 |
| 高置信推断 | 计费发布 500 与区间冲突的异常映射，现由领域异常、预检、事务锁和回归测试覆盖 | 未保留服务端生产堆栈；仍需在目标环境观察真实错误率和并发行为 |
| 未验证 | 最终精确 SHA 的远端 required CI、调用场景产品决策、生产环境与真实付费厂商 | 只能作为当前残余门禁，不能写成已通过 |

## 3. 优先级与依赖关系

| ID | 优先级 | 问题 | 主要领域 | 依赖/停点 |
|---|---|---|---|---|
| SEC-01 | P0 | 后端细粒度授权、动作权限和租户对象归属不完整 | 五域 | 先形成路由矩阵，再逐域落地 |
| SEC-02 | P0 | 数据测试契约读取依赖 `interface:view` | Access + Masterdata API | 依赖可用 API Key/授权事实校验 |
| AUTH-01 | P0 | 前端退出未注销服务端会话 | Identity + Web | 无产品决策依赖 |
| NAV-01 | P1 | 菜单、路由、按钮与默认落点不一致，管理员依赖角色名 | Web + Identity 权限事实 | 依赖 SEC-01 的权限词汇表 |
| NAV-02 | P1 | `api_process_admin` 有权限码但没有对应产品能力 | Access + Web | 需要产品选择“实现诊断”或“退役权限” |
| PERM-01 | P1 | 配置中心后端使用 `vendor:*`，前端使用 `config:*` | Masterdata + Identity | 先迁移/回填，再切换检查 |
| STATE-01 | P1 | 租户状态枚举漂移且前端二次反转 | Identity + Web | 确认 `suspended` 的产品展示 |
| STATE-02 | P1 | 配置状态开关二次反转导致 no-op | Masterdata + Web | 无产品决策依赖 |
| CONN-01 | P1 | 简单连接器表单生成空可选字段、SecretRef 误判并返回 400 | Masterdata + Web + 插件契约 | 需统一条件 Schema 语义 |
| MIG-01 | P1 | 不可迁移对象可进入 PREPARED，直到观察阶段才失败 | Masterdata + Web | 不得删除已有历史事实 |
| BILL-01 | P1 | 计费方案发布返回未结构化 500，且并发发布需串行化 | Billing + Web | 先复现确认根因 |
| UI-01 | P2 | 运行时模板图标产生 3 条 Vue warning | Web | 无产品决策依赖 |
| SCENE-01 | P2 | 调用场景只有列表/新增，生命周期语义不明确 | Access + Web | 需要产品选择“可维护”或“只增” |
| UX-01 | P2 | 正确的保护性 409 只能在提交后看到 | Web | 保留后端拒绝语义 |
| OBS-01 | P2 | CallRecord 最终一致只由测试轮询兜底 | Access + Governance | 生产阈值需观测数据决定 |
| ROLE-01 | 已闭合/回归 | 历史 fixture 把管理员和审批人耦合 | Test fixture | 保持四角色分离，不重新引入自审 |
| TEST-01 | 验收门禁 | 当前安全收尾版 P6 已在 fresh V060 隔离环境完整回放 | Test fixture | 远端交付仍以最终精确 SHA 的 `CI / required-ci` 为合并门禁 |

### 3.1 2026-09-03 实施与对抗性复审结论

- SEC-01/SEC-02/AUTH-01、NAV-01/NAV-02、PERM-01、STATE-01/02、CONN-01、MIG-01 和 BILL-01 的技术整改已落地；路由机器校验为 286 个 Controller mapping、255 个 policy entry、30 个 public/internal 排除项。
- UI-01、UX-01 和 OBS-01 已落地并通过前端/运行态断言；`api_process_admin` 采用只读流程诊断，流程写管理仍等待产品决策。
- SCENE-01 已采用安全的编码不可变、元数据可维护、停用代替物理删除实现；是否将其作为最终产品生命周期仍等待产品确认，未把该停点伪装成完成。
- V058 以前向迁移修复 API Key 权限目录的历史 ID 父级错误，V059 为调用场景补齐租户所有权并在无法明确归属存量行时原子 HALT，V060 分离精确缓存重放载荷和脱敏审计响应；三者都不改写已执行历史事实，fresh/upgrade/repeat/负向断言均纳入验证。
- 对 V053 的对抗性复审发现 `ON CONFLICT DO UPDATE` 会重写既有权限事实，已改为只补齐缺失词汇；对计费发布复审发现候选行锁早于业务键锁，已调整为“业务键锁 → 候选行锁 → 业务键范围行锁”，并补充并发顺序和并发修改回归测试。
- 对抗性复审重点检查了权限绕过、跨租户对象、Secret 明文/引用、Schema 条件字段、Redis 不可用、计费冲突部分写入、迁移脏状态、前端二次反转、控制台噪声和证据泄露；合并后 PR 评论复审又发现四个缺陷，已在后续提交中闭环，见下一节。

### 3.2 合并后 PR 评论的逐条整改

PR #45 合并后重新审查其目标评论，发现此前“已通过”的判断仍遗漏以下边界；每条都已独立修复并补充回归证据：

- `3920046732`：缓存候选的 `responseData` 已脱敏，直接重放会向调用方返回不完整数据。`5fcdfe5` 增加 V060 `cache_response_data`，精确成功响应只供授权缓存重放，普通 CallRecord 查询通过 `select=false` 排除该列，旧行不回填；新增精确重放和事件载荷测试。
- `3920046735`：浏览器敏感字段扫描使用了无效的 `rg -E -i` 参数，且错误可能被当作无匹配。`9bb4442` 修正参数并对 ripgrep 执行错误 fail-closed；`2723ea3` 又补齐凭据扫描和禁止文件扫描的错误路径，新增缺失证据目录负向 harness。
- `3920046738`：条件 Schema 的另一分支 `required` 不能推断当前分支禁止字段，否则会误删合法可选值。`23f1b4f` 仅依据显式 `not`/禁止字段处理，新增 `pruneSchemaValue` 可选字段回归测试。
- `3920046742`：`Number.longValue()` 会截断小数和超范围浮点数，可能查询错误 CallRecord。`edfeb82` 使用精确整数解析并拒绝小数、NaN、无穷和越界值，新增 Controller 400/无服务调用测试。

上述提交保持功能边界拆分；对应 targeted backend/frontend 测试、shell harness 和 fresh V060 运行态复验均在最终交付门禁前重新执行。

## 4. 详细修复设计

### 4.1 SEC-01：服务端细粒度授权与租户对象归属

**现状与风险**

多个管理 Controller 主要依赖全局登录态，页面菜单隐藏不能阻止用户绕过前端直接请求接口。现有验收只证明部分路由被前端守卫或后端拒绝，尚未证明全部约 212 个已盘点路由的权限闭合。风险包括具备只读权限的用户调用写接口，以及通过对象 ID 访问其他租户资源。

**实现方案**

1. 建立版本化的“HTTP 方法 + 路径 + 页面权限 + 动作权限 + 数据范围”清单，覆盖 Tenant/User/Role、Vendor/Config/DataType/Interface、Caller/Product/API Key/CallScene/CallRecord、Billing、Alert/Graylog/Audit 和连接器控制面。
2. 每个领域在自己的 service 模块提供领域内授权服务，不把数据库 Mapper 放入公共模块，也不跨域直读权限或业务表。授权服务统一读取 `UserContext` 的用户、租户和权限，并在加载对象后校验对象归属。
3. 查看入口使用 `*:view`；新增、编辑、删除、启停、发布、审批、撤销、导出等动作分别使用精确权限。`system:admin` 只能作为显式能力，不得用角色名 `admin` 形成隐式后门。
4. 对按 ID 操作的资源采用“查不到或不属于当前租户即拒绝”的一致策略；普通用户不得通过错误差异枚举跨租户对象。内部 `/internal/v1/**` 继续使用 Service JWT、audience 和 scope，不复用用户端权限绕过内部边界。
5. 用下一可用的 forward-only Liquibase changeset 补齐缺失权限、动作到查看权限的依赖及内置角色授权。迁移必须幂等、可在 fresh/upgrade/repeat 场景验证；不得改写已执行 changeset。
6. 在前端动作显隐完成前，后端先 fail-closed；任何 UI 兼容问题不能成为延迟后端授权的理由。

**验证矩阵**

- Controller/Service 单测：无登录 401、无权限 403、只有 view 不能写、精确动作权限成功、跨租户 ID 失败、同租户合法路径成功。
- Gateway 集成测试：按路由清单自动生成正负向用例，禁止只测菜单；记录未覆盖路径并使门禁失败。
- 浏览器：管理员、申请人、审批人、安全角色继续使用完全分离账号；按钮、菜单和直接 URL 的结果与后端一致。
- 数据库：被拒绝请求不得产生业务写入、计费、授权或成功审计；失败审计按既定规范保留。

**完成标准**

路由矩阵中不存在“仅登录即可执行的受保护管理动作”，所有对象级接口都有租户归属断言，且矩阵覆盖率和未覆盖清单可由 CI 机器读取。

### 4.2 SEC-02：数据测试页契约最小权限入口

**现状与根因**

`data-platform-web/src/views/data-test/index.vue` 复用管理端 `GET /interface/{id}/contract`；`ApiInterfaceController.getContract` 要求 `interface:view`。普通申请人本应只能使用自己的 API Key 和已生效授权进行测试，不应因此获得接口管理页查看权限。

**实现方案**

1. 在 Access 域的 `DataTestQueryController` 或同一功能边界新增登录态契约查询，例如 `GET /api/v1/data-test/contract?apiKeyId={id}&interfaceId={id}`。
2. Access 先复用 `CurrentUserApiKeyOptionService.findUsableKey` 校验 API Key 属于当前用户关联 Caller、当前租户且状态可用，再校验该 Key 对目标接口存在当前生效、未撤销、未过期的授权。
3. 通过 `data-platform-masterdata-api` 的 `ApiInterfaceFeignClient` 读取契约；不得从 Access 直读 Masterdata 表，也不得重新暴露内部接口到 Gateway。
4. 前端必须在 API Key 与接口都选定后调用新入口。403/404/网络失败时显示真实错误并禁止提交，不能再把失败降级为“没有参数”。
5. 管理端接口编辑页继续使用 `GET /interface/{id}/contract` 和 `interface:view`，保持管理与调用测试边界分离。

**验证与完成标准**

- 只有 `api-permission:view/apply` 且有有效 Key/Grant 的申请人能读取已授权接口契约并看到 `companyName`。
- 无 Key、他人 Key、跨租户 Key、已撤销/驳回/过期 Grant、未授权接口分别返回 400/403，不泄露契约。
- 申请人仍不能进入接口管理页或读取任意管理契约；不得通过给申请人补授 `interface:view` 让测试通过。
- 空 `companyName` 返回结构化 400，合法参数完成真实调用并产生 CallRecord/BillingEvent。

### 4.3 AUTH-01：服务端登出与旧 Token 失效

**现状与根因**

Identity 已提供 `POST /auth/logout`，但前端 store 的 `logout()` 只清本地 Token/UserInfo，布局页退出动作没有调用后端。因此页面看似退出，服务端会话是否失效没有被证明。

**实现方案**

1. 在 `data-platform-web/src/api/auth.ts` 暴露 logout API；布局退出动作先调用服务端，再清本地状态并跳转登录页。
2. 本地清理放在 `finally`，保证服务端暂时不可用时用户仍能离开当前会话；服务端失败需要脱敏提示和可观测日志，不能把原 Token 写入 console。
3. Identity 保持 `UserContext.logout()` 为唯一注销入口，确认 Sa-Token 的共享 Redis 会话在多实例中立即失效，并由 `@OperationLog` 记录成功/失败。
4. 处理重复登出为幂等安全结果；路由守卫发现过期/无效会话时复用同一前端清理函数，但不制造重复业务提示。

**验证与完成标准**

- 浏览器网络必须出现 `POST /api/v1/auth/logout`，随后本地 Token/UserInfo 清空并进入 `/login`。
- 保存旧 Token，在同实例和另一 Identity 实例重放 `/auth/userinfo`、受保护管理接口均为 401。
- Redis 中对应会话失效，治理审计出现一次脱敏登出记录；重复点击不恢复会话、不抛 500。

### 4.4 NAV-01：单一导航清单、能力型管理员和授权落点

**现状与根因**

`layout/index.vue` 自己维护菜单并按角色码 `admin` 放开全部菜单，router 又维护另一套权限。连接器菜单使用多个动作权限的 OR，路由只检查 `connector-plugin:view`；登录和根路由固定跳转 `/dashboard`，导致没有 dashboard 权限的用户先进入 forbidden 页。部分页面只靠 view 进入，动作按钮没有精确显隐。

**实现方案**

1. 建立共享、静态、可测试的导航 manifest，至少包含 path、title、icon、pagePermission、children 和默认优先级；菜单和 router 都从它派生或引用同一权限声明。
2. 页面可见性只由 page/view 权限决定；动作权限只控制页内按钮，不得因为拥有 publish/delete 等动作权限却没有 view 就显示入口。权限迁移保证动作权限蕴含对应 view。
3. 管理员判定改为 `system:admin` 能力；通知入口同样使用 `system:admin` 或 `monitor:view`，不读取 `admin` 角色名。
4. 登录成功、访问 `/`、已登录用户打开 `/login` 时，统一调用 `resolveFirstAuthorizedRoute`：优先用户原始目标，其次 dashboard，最后 manifest 中首个可访问页面；没有任何业务页权限时进入 profile/明确空态，而不是先触发 forbidden。
5. 所有新增/编辑/删除/启停/发布/回滚/审批/撤销/导出按钮使用统一 `v-permission` 或组合函数，并与 SEC-01 的服务端权限一致。

**验证与完成标准**

- 角色名变更但 `system:admin` 不变时能力不漂移；名为 admin 但无该能力时不会越权。
- 每种单一权限组合的菜单可见性、直接 URL、按钮和后端结果一致。
- 普通申请人登录直接进入首个授权页，不再出现 `/profile?forbidden=1` 中转。
- manifest 测试保证每个受保护路由恰有一个导航声明，连接器菜单/路由不再分叉。

### 4.5 NAV-02：`api_process_admin` 产品能力停点

当前迁移/角色中存在 `api-permission:process-view/manage`，但接口权限审批页只有申请、待办、授权等现有页签，没有清晰的流程管理能力。实施前必须二选一：

- **推荐：实现只读优先的流程诊断页签。** 展示流程定义版本、节点、绑定角色、启停状态和实例统计；修改动作单独要求 `process-manage`，所有写操作审计且不允许修改已运行实例历史。
- **备选：明确退役。** 若产品不需要流程管理角色，用 forward-only 迁移移除内置角色绑定和孤立权限，删除路由声明并更新文档；不得保留“看似有权但无页面”的角色。

完成标准是该角色登录后有唯一、可解释、可验收的落点，或该角色/权限被完整退役；不能仅重定向到无对应能力的普通申请列表。

### 4.6 PERM-01：配置中心权限码统一

**现状与风险**

前端菜单使用 `config:view`，但 `ConfigController.canView/canEdit` 检查 `vendor:view/edit`。配置专属角色可能看得到页面却收到 403，厂商角色又可能获得非预期配置能力。

**实现顺序**

1. 盘点所有 `config:*` 与 `vendor:*` 的角色绑定、前端按钮、Controller 和测试。
2. 用 forward-only changeset 新增/确认 `config:view/edit/add/delete`，为确实需要配置能力的内置角色回填；不从自定义角色静默扩权。
3. 迁移验证通过后，把 Controller 切换为 `config:*`；错误消息同步改为“配置中心权限”。缓存清理应使用独立的 `config:cache-clear` 或经确认的 edit 权限。
4. 移除临时双权限兼容；如必须滚动发布，只允许短周期 `config:* OR vendor:*`，并在同一交付计划中设置删除点。

**验证标准**

config-only 角色可按动作访问；vendor-only 角色不能访问配置中心；管理员正常；无权限和跨租户对象为 403；菜单、按钮、Gateway 和审计一致。

### 4.7 STATE-01：租户状态枚举与单次提交

**现状与根因**

前端混用 `enabled/disabled` 与 `active/disabled`，`el-switch` 已经改变 `row.status` 后，处理器又计算相反值并通过整对象 PUT 提交；后端状态端点只接受 `active/inactive/suspended`。

**实现方案**

1. 在共享前端类型中固定 `TenantStatus = 'active' | 'inactive' | 'suspended'`，列表筛选、表单、API 类型和显示文案全部使用同一枚举。
2. 启停开关仅表达 active/inactive；`suspended` 使用独立标签和恢复/解挂动作，避免一次开关抹掉暂停语义。
3. `@change` 接收 Element Plus 已提交的目标值，调用 `PATCH /tenant/{id}/status`，不再发送整行对象、不再次取反。
4. 请求前保存旧值，失败时恢复旧值并重新读取服务端事实；成功后也按响应或 reload 校验最终状态。
5. 后端校验合法状态转换和租户归属；非法转换返回结构化 409/400，不抛 500。

**验证标准**

三种状态筛选与展示正确；active↔inactive 每次点击只发一个 PATCH；失败回滚 UI；suspended 不被误转；刷新页面后状态与数据库一致。

### 4.8 STATE-02：配置状态开关

`config/index.vue` 的 `v-model` 已改变 `row.isActive`，处理器却再次执行 `!row.isActive`。修复时让处理器显式接收目标布尔值，转换为后端 `active/inactive` 一次提交；请求前保留旧值，失败回滚，成功 reload 复核。补充组件测试断言每次操作只有一个请求，并用浏览器验证刷新后状态不回跳。该修复不改变配置内容、密文或缓存策略。

### 4.9 CONN-01：简单连接器 Schema、条件字段与 SecretRef

**现状与根因**

- `schemaDefault/mergeSchemaDefaults` 会物化所有 properties，可选字符串变为 `""`，随后 `currentSpec()` 原样发送整个对象。
- `secretFieldRepresentation` 使用字段名包含匹配，`tokenEndpoint` 因包含 token 被误判为 Secret；显式 Schema 注解与启发式优先级不清晰。
- 工作区硬编码 `vendor.secretKey`，并在配置读取失败时继续提供这一假选项。
- fixture 插件的三种 flow 共用一组字段但没有完整表达条件可见性/条件必填，后端对“已出现的空字段”按 Schema 正确 fail-closed，于是 UI 生成的草稿返回 400。

**实现方案**

1. 默认值算法只物化三类字段：Schema `required`、显式 `default`、已有持久值。可选且无值的字段保持缺席；数组不因可选自动生成空数组。
2. 保存前递归 prune 隐藏字段、`undefined/null` 和空白可选字符串，同时必须保留有语义的 `false`、`0` 和显式空数组。prune 逻辑需要独立纯函数和矩阵测试。
3. 统一条件 Schema 白名单：用已有的声明式 `x-ui-visible-if` 或标准 JSON Schema `if/then` 表达三种 flow 的可见字段与必填字段；前端显示与后端验证必须读取同一 Manifest 事实，禁止各自硬编码。
4. Secret 判定优先使用显式 `x-secret-ref`/`x-sensitive`；只有 Schema 未声明时才用精确字段名兜底，`tokenEndpoint`、`totalTimeoutMs` 等普通字段不能因子串被误判。
5. Masterdata 提供“当前租户、当前厂商可引用的 Secret 名称”接口，只返回引用名、作用域和可用状态，不返回明文。前端删除 `vendor.secretKey` 硬编码，加载失败时 fail-closed 并禁止保存 Secret 字段。
6. 后端继续验证 SecretRef 所有权、存在性、作用域和插件 Schema，绝不能接受前端传来的明文 Secret；400 返回字段路径、错误码和安全摘要，便于表单定位。

**验证矩阵**

- 三种 flow：`single-http`、`token-business`、`async-polling` 均覆盖最小合法表单、字段切换、保存/刷新/再编辑、校验、受控测试和发布。
- 负向：空 required、非法 URL、错误 flow 字段、未知 SecretRef、其他租户/厂商 SecretRef、明文 Secret、隐藏字段残留均被结构化拒绝。
- 安全：浏览器 network/console/持久证据不出现 Secret 值；敏感扫描为 0。
- 回归：普通配置仍保持“插件 + 固定版本 + 一份 Schema 表单”，不得把 stageKey/capability/order/TRANSPORT 暴露给配置人员。

### 4.10 MIG-01：迁移准备前的资格校验

**现状与根因**

`VendorConnectorMigrationServiceImpl.prepare()` 只校验 Legacy 来源并写入 PREPARED；`startObservation()` 才调用 `requireSimpleTarget()`。页面只要 inventory 有 active 项就显示“准备迁移”，所以 `MUST_REMAIN_LEGACY` 也能制造永远无法推进的任务。

**实现方案**

1. 提取单一 `assessMigrationEligibility`，在 inventory、prepare、startObservation 和 UI 中复用同一分类与原因码；prepare 必须在持有现有 runtime facts 锁时先校验再插入。
2. `LOSSLESS_CONVERTIBLE` 允许自动准备；`REQUIRES_DEDICATED_PLUGIN` 只有在显式选择并验证专用插件、草稿、测试和激活前置条件后进入人工路径；`MUST_REMAIN_LEGACY` 在写入前返回结构化 409。
3. UI 根据 eligibility 禁用按钮并展示安全原因，不允许先创建任务再解释失败。
4. 对已经存在的无效 PREPARED 行先提供 dry-run 报告，再用 forward-only、CAS/recordVersion 保护的修复把它们转为 `BLOCKED`（或现有状态模型中的等价终态），写入 safe error code/digest；不得删除历史行或伪造已迁移。
5. 保持现有 source hash、recordVersion、观察门禁和回滚事实，资格校验不能绕过并发冲突。

**验证标准**

blocked 对象 prepare 返回 409 且数据库不新增任务；可转换对象完整通过 PREPARED→OBSERVING→READY→STABLE；并发 prepare 只产生一个有效事实；source 漂移、recordVersion 冲突和重复请求均可诊断；已有无效行修复后保留审计链。

### 4.11 BILL-01：计费发布预检、业务错误和并发一致性

**现状与根因边界**

浏览器保存了发布 500 响应，但敏感清理后没有可用的服务端堆栈。源码显示 `validate()` 只运行契约校验；`publish()` 还会关闭旧版本并执行“同厂商 + 接口 + 会计方向”的区间冲突检查，冲突通过 `IllegalArgumentException` 抛出。该路径是 500 的高置信候选，不是已由堆栈证实的唯一根因。

**实现方案**

1. 先在 fresh 隔离库复现同一表单和已有方案组合，保存脱敏的请求摘要、计划 ID/业务键、错误码和服务端异常类型，禁止保存价格外的敏感数据。
2. 建立计费领域异常：请求/契约校验为 400、资源不存在为 404、状态或生效区间冲突为 409；全局异常映射不得把可预期业务冲突转为 500。
3. 提取 publish preflight，让“校验”和“发布”共用状态、契约、版本顺序、区间重叠和业务键检查；校验接口返回结构化错误列表，UI 在提交前即可显示。
4. 对同一 `(vendorId, interfaceId, accountingPurpose)` 在事务内加数据库可证明的串行化策略（行锁、专用锁事实或等价机制），锁后重新读取并校验；不能只依赖前端禁用按钮。
5. 同一 planCode 的升级使用“新版本”流程并保证生效时间严格递增；不同 planCode 的重叠返回 409。冲突失败不得提前关闭现有 ACTIVE 版本或留下部分更新。
6. 实施前必须重新执行 GitNexus upstream impact。此前规划查询将 `BillingPlanService.publish` 标为高影响候选；需按实际直接调用者和跨模块契约拆成窄改动，图谱结果只作为影响提醒。

**验证标准**

非法方案 400、缺失 404、重叠 409，均无通用 500；同方案下一版本、未来生效版本和合法发布成功；两个并发发布只有一个成功；失败事务前后 ACTIVE/PUBLISHED/有效区间不被部分修改；UI 能引导用户选择“新建版本”而不是重复方案。

### 4.12 UI-01：Vue runtime compiler warning

把 `layout/index.vue` 中带 `template` 字符串的运行时组件替换为静态 SFC 图标、Element Plus 图标或显式 render function，并保持图标映射类型安全。补 Vitest mount 测试与 P6 console 门禁：error 为 0，目标 warning 为 0；不能用过滤 console 文本的方式假装修复。

### 4.13 SCENE-01：调用场景生命周期产品决策

推荐采用“编码不可变、名称/描述/状态可维护、停用代替物理删除”的模型：新增 `call-scene:add/edit/disable`（只有明确要求且无引用时才考虑 delete），后端检查租户归属和 sceneCode 唯一性，已有 CallRecord 永远保留原场景事实。若产品选择只增模型，则明确页面和 API 文档，移除 CRUD 暗示并隐藏无意义权限。两种方案都必须通过产品确认后实施，不能自行补一个会破坏历史引用的物理删除。

### 4.14 UX-01：正确保护性 409 的前端表达

已发布连接器重复发布、活动绑定插件禁用、相同版本重复导入返回 409，且没有破坏活动事实，这是正确保护，不应改成 200 或弱化后端检查。前端根据当前状态提前禁用动作、展示不可操作原因，并在竞争条件下仍正确处理服务端 409。浏览器测试同时断言错误提示与数据库/活动版本无变化。

### 4.15 OBS-01：CallRecord 最终一致性与观测

Kafka 异步落库导致成功响应后短暂查不到 CallRecord 是设计行为，不改为同步写入。保留按 requestId/traceId 的有界轮询，超时保存调用结果、Kafka lag、consumer/DLT 和数据库查询摘要；Dev fixture 暂保持 30 秒门禁。生产阈值只能根据 staging/production 观察数据和 SLO 决定，不能直接照搬 30 秒。补充 lag、消费失败、DLT、端到端落库延迟指标和告警。

### 4.16 TEST-01：安全收尾脚本的 fresh 完整回放

最终 P6 通过后，脚本移除了 tracing start/stop 以避免保留凭据。整改前该安全收尾版本只执行过 `bash -n` 和 `--help`；2026-09-03 已在 fresh V060 隔离环境完成以下回放：

1. 从 fresh 隔离数据库、V060 最新迁移且 `pendingMigrations=0` 启动六服务、Gateway、Web 和 HTTPS fixture。
2. 使用四个分离角色和真实用户名密码，不注入 Token/storage state/mock。
3. 执行登录、菜单/直接 URL 权限、申请、分离审批、契约加载、真实查询、CallRecord/Billing、撤销后旧 Key 403、服务端登出旧 Token 401，以及本方案涉及的代表性 CRUD/负向路径。
4. 对当前 `run-browser-e2e.sh` 运行完整 P6，确认成功证据目录不产生 trace/profile，敏感扫描为 0。
5. 只通过授权的 fixture state 清理隔离数据库和端口，重新生成 cleanup verification；共享 PostgreSQL/Redis/Kafka/Nacos 保持健康。

### 4.17 ROLE-01：保持审批角色分离

历史 fixture 的管理员同时具备审批角色，不能证明独立审批边界。第三轮已经用管理员、普通申请人、独立审批人和独立安全角色闭合该证据，因此这不是待修业务缺陷。后续修复只需把四角色完全分离固化为 fixture/浏览器回归：申请人不得审批自己的申请，管理员不应被测试脚本自动当作审批人，安全角色只执行撤销；任何角色缺失都应让验收 fail-closed。

## 5. 建议实施批次与提交边界

每个提交只覆盖一个可验证功能边界，禁止把五域授权、连接器、计费和 UI 清理压成一个大提交。建议顺序如下：

1. 路由/动作/租户范围清单与测试骨架（不改变业务行为）。
2. Identity 授权与服务端登出；对应前端 logout。
3. Masterdata 授权与配置权限码 forward-only 迁移。
4. Access 授权与数据测试契约最小权限入口。
5. Billing、Governance 的领域授权及直接 Gateway 负向矩阵。
6. 共享导航 manifest、能力型管理员、默认授权落点和动作按钮。
7. 租户状态修复；配置状态修复应单独提交。
8. 连接器 Schema/SecretRef 修复（前后端契约与测试作为一个纵切，但不混入迁移控制面）。
9. 迁移资格前置与历史 PREPARED 行的独立 forward-only 修复。
10. 计费发布预检、异常映射和并发串行化。
11. Vue warning；调用场景仅在产品决策完成后另立提交。
12. P6/fresh 证据与知识库最终同步。

任何代码提交前必须对拟修改符号执行 GitNexus upstream impact；HIGH/CRITICAL 先报告影响再改。提交前执行 `detect_changes()` 核对实际受影响符号和流程，并按 `CODE_REVIEW_GATE.md` 自审。`dev`/`master` 禁止直推。

## 6. 分层验证门禁

| 层级 | 必做验证 | 通过条件 |
|---|---|---|
| 单元/组件 | 权限 resolver、状态转换、Schema default/prune、Secret 判定、异常映射、并发规则、Vue 组件 | 正负向断言齐全，不靠快照掩盖错误 |
| 模块集成 | 五域 Controller/Service、Feign 契约、Liquibase fresh/upgrade/repeat、数据库并发 | 401/403/409 语义稳定，无跨域直读和部分写入 |
| 基础 CI | 后端 `verify`，前端 `npm ci/lint/test/build`，仓库合同与 `git diff --check` | 本地通过，但不替代远端检查 |
| 隔离运行态 | fresh V060、四角色真实浏览器、Gateway、CallRecord/Billing/审计、敏感扫描与清理 | 当前修复问题逐项闭合，未验证项明确保留 |
| 远端交付 | 最终精确 SHA 的 `CI / required-ci` | backend/frontend/contracts/required-ci 均明确成功后才可交付 |
| 生产门禁 | staging/production、真实厂商、容量/长稳、DR/回滚、通知渠道 | 仍按既有发布 Runbook 单独执行，不属于本轮 Dev 修复通过条件 |

## 7. 完成定义与残余边界

本整改专题的本地整改与验证条件已满足；远端交付仍须以最终 PR 精确 SHA 的 required CI 记录为准，产品停点和生产门禁仍不得被本专题代替：

- P0/P1 项有代码、测试、迁移和运行态证据，产品停点已得到明确决策；
- 约 212 个已盘点路由的机器可读授权矩阵无未知受保护路径，跨租户负向通过；
- 普通申请人无需 `interface:view` 即可安全加载已授权契约，仍不能读取任意管理契约；
- 登出后旧 Token 跨实例重放为 401；
- 三类连接器表单、迁移资格和计费发布均无通用 500/不可推进脏状态；
- 缓存重放使用未脱敏的精确载荷而普通 CallRecord/审计查询不暴露该载荷，证据扫描遇到读取错误时 fail-closed，数值过滤不发生有损转换，条件 Schema 不误删可选字段；
- 当前安全版 P6 在 fresh 环境完整通过，warning/error、敏感扫描和清理断言通过；
- 最终 SHA 的远端 required CI 成功，文档与 `PENDING_TASKS.md` 同步到相同事实。

即使上述全部完成，生产/预生产部署、真实第三方付费厂商、并发容量/长稳、灾备回滚、告警通知渠道和生产观察窗口仍是独立发布门禁，不得在 Dev 整改报告中冒充完成。
