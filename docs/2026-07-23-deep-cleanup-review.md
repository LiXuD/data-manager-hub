# 2026-07-23 深度更新与清理审查

## 结论

本轮清理以“一个概念只有一个权威数据源、一个能力只有一个正式入口、安全失败必须关闭、知识必须能由当前代码和自动化验证”为约束。过渡接口、旧字段和失败回退不再继续叠加维护。

完成后的核心不变量：

- 接口契约只以 `interface_param` 请求/响应字段树为源；`request_schema`、`response_schema` 只能由字段树派生。
- 对外数据调用只走 `/openapi/v1/query` 与 `/openapi/v1/batch-query`。
- API Key 只由 `/caller/apikey` 资源管理。
- 厂商请求/响应安全只走版本化安全流水线；配置缺失、密钥异常或流水线加载失败时拒绝调用。
- 用户密码只接受 BCrypt；标记为加密的厂商扩展配置必须是平台密文格式。
- 数据库结构只由 Liquibase 根变更日志推进，旧库接管必须先备份并通过完整性校验。

## 清理范围

### 契约与调用入口

- 删除 `/interface/{id}/schema`、`/params`、批量参数维护和 `import-schema` 兼容接口。
- 删除 Access 域 `/data/**` 的重复控制器、服务、请求对象和前端调用封装。
- Access 运行时只读取 Masterdata `getContract` Feign 契约。
- 删除独立 Schema 写入、读取回退和旧 `validationRule` 运行时解析。
- API Key 创建、查询、状态、限流、授权和删除统一到 `ApiKeyController`；移除 `CallerController` 中的重复入口。

### 安全与敏感数据

- 删除明文密码比对回退。
- 删除 `signType`、`encryptType`、`SignatureBuilder` 及对应 DTO、实体、前端和测试残留。
- 删除厂商代理在安全流水线失败时回退到简单签名的逻辑。
- 厂商扩展配置不再读取或自动迁移“标记加密、实际明文”的值。
- 保留安全流水线中显式配置的 MD5/SHA1 算法，仅用于确有协议约束的旧厂商，界面持续显示弱算法警告；它们不是默认路径或失败回退。

### 代码与前端

- 删除 SDK 单文件旧快捷方法，保留由 `ApiSpec` 驱动的 Java/Python/Go 多文件生成器。
- 删除未挂载的旧 `SignConfig.vue` 组件及 `SignConfig`/`SignType` 类型。
- 删除告警规则前端兼容字段、后端 JSON 别名和告警记录别名方法。
- 删除未使用的厂商配置公共查询包装、数据查询缓存包装和常量/类型重导出。
- 网关路由 ID 改为五域语义名称，路径与转发行为不变。

### 数据库与迁移

- 新增 `V025__remove_obsolete_compatibility_fields.sql` 与显式回滚。
- V025 在删除列前执行数据守卫：非 BCrypt 密码、伪加密明文、未迁移的厂商签名/加密、无法转换的校验规则、只有 Schema 没有字段树的契约、迁移步骤键冲突都会中止事务。
- 可转换的 `validation_rule` 迁入 `constraint_config`，旧安全步骤键改为中性迁移键，随后删除 `validation_rule`、`sign_type`、`encrypt_type`。
- 旧库 `baseline` 接管会在单事务内临时恢复兼容列、重放历史增量、执行 V025、校验最终结构，再登记两个 changeset。
- 删除未被调用、会直接 `DROP DATABASE` 且结构已漂移的 `sql/create_database.sql`；历史 `sql/init.sql` 仅保留为 Liquibase 基线输入。

### 依赖与配置

- 后端统一为 Java 21、Spring Boot 3.4.13、Spring Cloud 2024.0.3、Nacos 2023.0.3.4、MyBatis-Plus 3.5.8、Redisson 3.27.2、Hutool 5.8.47。
- MyBatis-Plus 未跨到 3.5.9+：新分页插件拆分为独立依赖且会扩大本轮架构改造范围，因此本轮停在当前架构可直接验证的 3.5.8。
- 前端更新到 Vue 3.5.40、TypeScript 5.9.3、Vite 6.4.3、Element Plus 2.13.7、Axios 1.18.1、ECharts 6.1.0。
- 删除未使用且带来审计风险的 `vite-plugin-mock`；启用 Node `>=18.18` 与 `engine-strict`。
- 删除跟踪的本地 `.env`，新增不含秘密的 `.env.example`。

## 知识库

以下文档已按当前代码重写或同步：

- `README.md`：版本、架构入口、数据库流程和合并门禁。
- `CODE_WIKI.md`：五域路由、契约、安全流水线、前端版本和 Liquibase 流程。
- `docs/API.md`：只保留当前可用 API 与安全边界。
- `docs/DEPLOYMENT.md`：Liquibase 初始化、校验、预演和 SDK 文件名。
- `PENDING_TASKS.md`：记录已完成清理和仍需长期运行的工程任务。

已删除失真的前端完成报告和旧生产就绪快照。日志、`.DS_Store` 与未跟踪旧设计稿被移动到系统废纸篓，而不是不可恢复删除。

## 回归证据

| 检查 | 结果 |
|------|------|
| `mvn verify` | 25 个 Maven 模块成功，251 项测试通过，0 失败/错误/跳过 |
| `npm audit` | 0 个漏洞 |
| `npm run lint` | 通过 |
| `npm run build` | 通过；2498 个模块完成转换 |
| `arch-scan.sh` | 8 项五域边界检查通过 |
| Docker Compose 配置 | 校验通过 |
| Shell 语法 | 迁移、回归、启停和架构脚本通过 `bash -n` |
| 数据库隔离回归 | dry-run、update、幂等、V025 回滚/重放、两步全量回滚、备份/恢复、旧库 baseline 接管全部通过 |
| GitNexus 图谱 | 28,752 节点、55,547 边、360 聚类、300 执行流；循环依赖 0 |
| GitNexus 变更影响 | HIGH；151 个变更符号、14 条受影响执行流，集中在密码校验、API Key 和契约更新路径 |
| GitNexus 污染流 | 0 条已建模发现；闭包、字段和隐式流不在模型覆盖内，不能把“0”解释为安全证明 |
| 文本残留与 diff | 旧入口/类型扫描无运行时代码命中，`git diff --check` 通过 |

前端生产构建仍有一个 523.48 kB 的分包警告，不影响正确性；后续可将 Element Plus/图表相关页面进一步按路由拆包。
