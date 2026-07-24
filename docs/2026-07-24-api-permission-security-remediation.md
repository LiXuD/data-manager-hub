# 接口权限审批安全整改与验收矩阵

> 目标：封闭 IAM 管理接口，统一角色/候选组编码，修正审批角色矩阵和会话失效，
> 并通过攻击链与真实审批运行态回归证明整改有效。

## 验收矩阵

| 用户动作 | 授权与所有权 | 数据变化 | 可见结果 | 必须拒绝 | 运行时副作用 | 完成证据 |
|---|---|---|---|---|---|---|
| 查询用户/角色/权限 | 对应 `user:view` / `role:view`；普通管理员仅能访问本租户用户 | 无 | 有权限返回数据 | 未登录 401、缺权限 403、跨租户 403 | 无 | Controller 测试、真实 HTTP |
| 管理用户和角色关系 | `user:edit`；禁止修改自己的角色；不能授予操作者未拥有的权限 | `user_role` | 目标用户角色更新 | 自赋权、跨租户、无效/停用角色 | 变更前注销目标用户全部会话 | 攻击链测试、会话测试 |
| 管理角色和权限目录 | 对应 `role:add/edit/delete` 且需 `system:admin` | `role_info`、`role_permission`、`permission` | 管理操作成功 | 普通登录用户、租户管理员、越权权限集合 | 变更前注销所有受影响用户会话 | Controller/Service 测试 |
| 部署安全迁移 | 角色编码统一为小写；重复角色合并；默认组可匹配 | 角色、关系、流程路由、权限矩阵 | 管理员和审批员角色清晰 | 重复关系、大小写漂移、停用角色获权 | 首次启动强制使旧 RBAC 会话失效 | fresh/upgrade/repeat/恢复测试 |
| 审批任务认领 | `api-permission:approve` 且规范化角色命中 candidate group | Flowable assignee | 合法审批人可认领 | 大小写旁路、非候选人、自审、跨租户 | 任务状态和业务投影一致 | Flowable 测试、真实运行态 |
| 权限撤销/角色停用 | 登录权限只来自 active、未删除角色和权限 | 角色/权限状态 | 新请求立即失权 | 旧会话继续使用审批/紧急授权 | 受影响会话立即注销 | 登录权限测试、真实 HTTP |
| 前端进入审批工作台 | 任一审批相关查看/办理权限可进入；路由再次校验 | 无 | 展示允许的标签页 | 仅靠直接 URL 越过前端守卫 | 后端仍为最终授权边界 | lint、typecheck、build、路由测试 |

## 安全不变量

1. 任意普通已登录用户不能给自己分配角色或给自身角色追加权限。
2. 角色、权限和用户状态变化后，不允许旧会话继续使用旧权限。
3. Flowable candidate group 与 identity 角色编码在同一规范化规则下比较。
4. `tenant_admin` 不默认拥有紧急授权；`admin` 才拥有平台级安全管理能力。
5. 旧 `api_key_interface` 授权事实和非审批权限关系不得因整改迁移丢失。
6. 所有拒绝路径必须返回真实 401/403/409，而不是只在前端隐藏入口。

## 运行态回归证据

验收日期：2026-07-24。测试使用从开发基线克隆的一次性 PostgreSQL 数据库、
独立 Redis 15 号库，以及真实启动的 identity、masterdata、access、governance
服务。数据库先通过仓库 `migrate-db.sh update` 执行 Liquibase，共确认 5 个
changeset，测试结束后服务、数据库、Redis 数据和临时目录均已清理。

| 验收项 | 运行态结果 |
|---|---|
| 角色与权限矩阵 | 管理员登录返回小写 `admin` 和 `system:admin`；普通用户仅有 `api-permission:view/apply`；数据库中角色和候选组非小写数量均为 0，审批角色权限数分别为 admin 8、user 2、tenant_admin 7、两类审批员 3、流程管理员 2、平台安全管理员 4 |
| IAM 接口封闭 | 未登录访问返回 HTTP 401；普通用户访问用户、角色、权限管理接口均返回 HTTP 403；普通用户分配角色返回 403；管理员修改自己的角色返回 `SELF_ROLE_MUTATION_FORBIDDEN` |
| 会话失效 | 普通用户旧 Token 在角色重新分配前有效；变更后 identity 校验返回业务码 401，access 服务返回 HTTP 401；重新登录后获得新会话 |
| 旧直配入口 | 管理员调用 `/caller/apikey/{id}/interfaces` 仍返回 HTTP 409，并明确要求走审批申请 |
| 审批提交 | 草稿由 `DRAFT` 进入 `IN_REVIEW/RUNNING`，Flowable 生成真实流程实例和用户任务；相同幂等键重放返回同一申请 |
| 自审批攻击 | 临时赋予申请人候选组角色后，申请人可见任务但认领返回 HTTP 403，证明候选资格不能绕过自审批限制 |
| 审批与授权 | 管理员认领并审批后，申请为 `EFFECTIVE/COMPLETED`，申请项为 `EFFECTIVE`，授权事实为 `APPROVAL/ACTIVE`，审批轨迹为 `CREATE,SUBMIT,APPROVE,GRANT`，Flowable 历史实例有结束时间且运行时任务数为 0 |
| 重复授权 | 已有有效授权后再次提交 OPEN 申请返回 HTTP 409 和 `GRANT_ALREADY_ACTIVE` |
| 治理审计 | governance 服务收到带内部 JWT 的日志请求并返回 200；`operation_log` 持久化申请人、接口权限审批模块、操作名称和 success 状态 |

## 全项目回归门禁

- `mvn verify`：25 个 Maven 模块全部成功，293 项测试零失败、零错误。
- 前端 `vue-tsc --noEmit`、ESLint 和 Vite 生产构建全部通过。
- `npm audit --audit-level=high`：0 个漏洞。
- `bash arch-scan.sh`：五域边界扫描通过，无跨域 service 依赖、重型 API
  依赖、全包扫描、跨域 import、旧模块、Feign 契约或数据所有权违规。
- `verify-db-bootstrap.sh`：dry-run、首次升级、重复执行、V026/V027、
  Flowable、前向恢复拒绝、备份恢复和基线校验通过。
- GitNexus staged 变更检测：33 个文件、低风险、未发现意外执行流程影响。
