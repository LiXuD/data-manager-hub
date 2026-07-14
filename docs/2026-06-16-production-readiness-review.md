# 上线就绪审查与修复记录

> **当前参考状态（2026-07-14）**：审查结论和既有验证证据仍有效；跨域调用已进一步收敛为带最小权限的 Internal Feign 契约，并已完成本轮运行态复验。

## 结论

本轮审查已修复默认生产配置、链路追踪传播、默认测试门禁、本地基础设施闭环、运行态启动阻断、可执行 Jar 打包、前端大 chunk 警告和部署文档不一致问题。当前代码通过架构扫描、Maven 校验、后端全量测试、前端生产构建、Spring Boot 可执行 Jar 打包和 Docker Compose 配置校验，可进入部署环境联调。

## 已修复问题

| 类别 | 问题 | 修复 |
|------|------|------|
| 生产配置 | 五域服务/Gateway 的 `application-prod.yml` 带本地地址或弱口令默认值 | 改为生产必填环境变量；补齐 masterdata 的 prod profile |
| 链路追踪 | 请求缺失 `X-Trace-Id` 时日志和下游 Feign 可能无 traceId | 入口过滤器生成 traceId；Feign 从请求头或 MDC/SkyWalking 桥接上下文传播 |
| 测试门禁 | `mvn test` 默认执行外部 API 集成测试，未启动服务时失败 | 集成测试默认跳过，显式设置 `-Dintegration.tests=true` 或 `INTEGRATION_TESTS=true` 才执行 |
| 适配器兼容 | `HttpVendorAdapter` 旧 JSON 字段映射被 `requestMapping` wrapper 逻辑短路 | 恢复兼容 `{"source":"target"}` 简单映射，同时保留结构化映射 |
| 本地基础设施 | compose 缺 PostgreSQL、Grafana 端口映射错误、Prometheus 配置未提交 | 补齐 PostgreSQL、修复 `3100:3000`、新增 `prometheus/prometheus.yml` |
| 本地端口冲突 | 本机已有 PostgreSQL 时 compose 固定绑定 5432 会启动失败 | PostgreSQL 宿主端口支持 `POSTGRES_PORT` 覆盖，服务端 dev profile 支持 `DB_PORT` 覆盖 |
| 开发配置闭环 | dev profile 硬编码 `localhost:5432` 和数据库密码 `123456`，与 compose 默认不一致 | 五域服务与 Gateway dev profile 改为环境变量占位，默认对齐 compose |
| 运行态启动 | masterdata/access 域内重复 `WebMvcConfig` 导致 Bean 冲突，access 缺少熔断管理 Bean | 删除重复 MVC 配置，补齐 common-runtime 熔断自动配置 |
| 可执行 Jar | 部分 service/Gateway 主 Jar 未绑定 Spring Boot `repackage`，或只输出 `*-exec.jar` | 统一绑定 `spring-boot:repackage`，主 Jar 均为可执行部署产物 |
| 前端构建 | Vite 生产构建存在大 chunk 警告，首屏资源体积偏大 | Element Plus 改为按需注入，Dashboard ECharts 改为按需模块，Vue/axios 公共依赖独立分包 |
| SkyWalking | Agent 解压目录假设错误，可能找不到 `skywalking-agent.jar` | 从临时目录定位真实 agent jar 后复制到 `skywalking/agent` |
| 部署文档 | `DB_USER`/`DB_USERNAME` 不一致，SDK CLI 主类错误，compose 与生产边界不清 | 统一 `DB_USERNAME`，修正 CLI 主类，标注 compose 仅用于本地/测试 |
| 服务间认证 | Feign 只传播 Trace，内部接口依赖匿名白名单 | Identity 签发短期 RSA Service JWT；目标服务校验 audience/scope；内部契约统一为 `/internal/v1/**` |
| 用户认证 | Bearer Token 只检查长度，Sa-Token 会话仅存进程内存 | 使用 Sa-Token 真实校验并通过 Redis DAO 共享会话，Identity 登录 Token 可跨域访问管理接口 |
| 调用记录 | `CallRecord` 与表结构不一致且 JSONB 使用字符串绑定，Kafka 消费无法落库 | 移除冗余字段并为 JSONB 字段配置类型处理器，完成真实 Kafka 落库验证 |
| 计费规则 | `BillingRule.ruleName` 与数据库缺失列不一致，OpenAPI 静默使用降级价格 | 新增 V011 迁移并完成 Billing 内部计算验证，演示链路费用为 `0.30` |
| 操作日志 | 治理域实体未填充表中必填的 `operation_type`、`operation_module` | 在 `LogService` 统一规范化必填字段，内部日志接口完成真实落库验证 |
| 跨域数据所有权 | Masterdata/Billing 直接读取 Access 的 `call_record` | 统计 SQL 收归 Access，通过 `CallStatsInternalFeignClient` 暴露只读内部契约 |
| 跨域事件 | Access 通过 Kafka 驱动 Billing 聚合，绕过服务认证 | `call-record` 主题收归 Access 域内；Billing 计算与聚合改为认证 Feign 同步调用 |
| 计费失败语义 | Billing 异常被吞掉并生成 `0.10` 假价格 | 删除降级价格，空响应和调用失败均向上游失败关闭 |
| 最小权限 | 客户端 scope 不按目标 audience 隔离，厂商密钥共用普通读权限 | 改为 audience 级 grants；密钥接口使用 `masterdata:vendor-secret:read` |
| 内部认证韧性 | dev 可默认关闭认证，令牌获取缺少有限超时/重试测试 | dev 默认开启；Identity 优先启动；令牌连接/读取超时、有限重试、缓存和 4xx 不重试均有测试 |
| 操作日志装配 | 公共日志切面不在五域组件扫描范围内 | 使用 Spring Boot 自动配置注册切面和远程实现，并保证日志上下文异常不影响业务 |
| 旧内部契约 | API 模块仍保留公共路径 Feign 和重复适配 Controller | 删除旧契约；内部 Feign 使用 `@InternalFeignContract` 显式标记；架构扫描强制检查内部路径、契约标记、显式注册和 scope |

## 验证证据

| 命令 | 结果 |
|------|------|
| `bash arch-scan.sh` | 通过 |
| `mvn -q validate` | 通过 |
| `mvn -q -DskipTests compile` | 通过 |
| `mvn -q -DskipTests test-compile` | 通过 |
| `mvn -q test` | 通过 |
| `mvn -q -DskipTests clean package` | 通过，五域 service 与 Gateway 主 Jar 均包含 `BOOT-INF` |
| `npm run build` | 通过，无 Vite 大 chunk 警告；最大 chunk 480.75 KiB |
| `docker compose config --quiet` | 通过 |
| `POSTGRES_PORT=15432 docker compose up -d postgres grafana` | 通过 |
| `psql -h localhost -p 15432 -U postgres -d dataplatform -f sql/init.sql` | 通过 |
| 五域服务 + Gateway 本地启动 | 通过，`8081/8082/8084/8085/8086/8888` 健康检查均为 `UP` |
| 服务间认证负向验证 | 错误密钥 401、缺失凭证 401、错误 audience 401、scope 不足 403、Gateway 内部路径 404；敏感厂商密钥仅专用 scope 可访问 |
| 用户认证运行态验证 | Identity 登录成功；Redis 生成 `Authorization:login:*` 会话键；携带 Token 访问 Masterdata 管理接口返回 200，非法 Token 返回 401 |
| OpenAPI 端到端验证 | 返回 200 和 Billing 价格 `0.30`；Masterdata/Billing Feign 成功；`call_record`、`billing_daily_event` 与 `billing_daily` 成功落库；重复计费请求只聚合一次 |
| 跨域统计与日志验证 | Masterdata Service JWT 查询 Access 统计返回 200；Identity Service JWT 写入 Governance 后 `operation_log` 可查询 |
| Gateway 路由烟雾请求 | `/api/v1/vendor/list` 返回预期 401，`X-Trace-Id` 响应头透传 |
| 生产 profile 默认值扫描 | 无 `localhost`、弱口令或必填环境变量默认值命中 |
| GitNexus `detect_changes(scope=all)` | 已执行：88 个文件、241 个符号、54 条流程受影响，风险为 critical；高风险来自跨认证、计费和统计主链路的成组整改，受影响流程与本轮范围一致，需保持全量回归门禁 |

## 剩余上线注意事项

- `docker-compose.yml` 仅用于本地开发/测试，仍保留本地默认密码；生产环境必须使用独立高可用基础设施和密钥系统。
- 本机已有 PostgreSQL 占用 5432 时，可使用 `POSTGRES_PORT=15432 docker compose up -d postgres` 启动 compose 内 PostgreSQL，并在启动 Java 服务前设置 `DB_PORT=15432`。
- 生产部署必须提供基础设施连接变量，以及 `INTERNAL_AUTH_TOKEN_URI`、RSA 密钥路径和各服务独立的 `INTERNAL_AUTH_*_SECRET`。
- 真实环境上线前需显式开启外部 API 集成测试：`mvn test -Dintegration.tests=true` 或设置 `INTEGRATION_TESTS=true`。
- SkyWalking 生产环境不要使用 compose 内 H2 存储，应配置持久化后端。
