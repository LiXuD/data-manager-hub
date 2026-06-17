# 2026-06-16 上线前审查报告

## 结论

本轮审查已修复默认生产配置、链路追踪传播、默认测试门禁、本地基础设施闭环、运行态启动阻断、可执行 Jar 打包和部署文档不一致问题。当前代码通过架构扫描、Maven 校验、后端全量测试、前端生产构建、Spring Boot 可执行 Jar 打包和 Docker Compose 配置校验，可进入部署环境联调。

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
| SkyWalking | Agent 解压目录假设错误，可能找不到 `skywalking-agent.jar` | 从临时目录定位真实 agent jar 后复制到 `skywalking/agent` |
| 部署文档 | `DB_USER`/`DB_USERNAME` 不一致，SDK CLI 主类错误，compose 与生产边界不清 | 统一 `DB_USERNAME`，修正 CLI 主类，标注 compose 仅用于本地/测试 |

## 验证证据

| 命令 | 结果 |
|------|------|
| `bash arch-scan.sh` | 通过 |
| `mvn -q validate` | 通过 |
| `mvn -q -DskipTests compile` | 通过 |
| `mvn -q -DskipTests test-compile` | 通过 |
| `mvn -q test` | 通过 |
| `mvn -q -DskipTests clean package` | 通过，五域 service 与 Gateway 主 Jar 均包含 `BOOT-INF` |
| `npm run build` | 通过，仅 Vite chunk size warning |
| `docker compose config --quiet` | 通过 |
| `POSTGRES_PORT=15432 docker compose up -d postgres grafana` | 通过 |
| `psql -h localhost -p 15432 -U postgres -d dataplatform -f sql/init.sql` | 通过 |
| 五域服务 + Gateway 本地启动 | 通过，`8081/8082/8084/8085/8086/8888` 健康检查均为 `UP` |
| Gateway 路由烟雾请求 | `/api/v1/vendor/list` 返回预期 401，`X-Trace-Id` 响应头透传 |
| 生产 profile 默认值扫描 | 无 `localhost`、弱口令或必填环境变量默认值命中 |
| GitNexus `detect_changes(scope=all)` | low risk，无受影响执行流程 |

## 剩余上线注意事项

- `docker-compose.yml` 仅用于本地开发/测试，仍保留本地默认密码；生产环境必须使用独立高可用基础设施和密钥系统。
- 本机已有 PostgreSQL 占用 5432 时，可使用 `POSTGRES_PORT=15432 docker compose up -d postgres` 启动 compose 内 PostgreSQL，并在启动 Java 服务前设置 `DB_PORT=15432`。
- 生产部署必须提供 `NACOS_SERVER_ADDR`、`DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`。
- 真实环境上线前需显式开启外部 API 集成测试：`mvn test -Dintegration.tests=true` 或设置 `INTEGRATION_TESTS=true`。
- SkyWalking 生产环境不要使用 compose 内 H2 存储，应配置持久化后端。
- 前端构建存在 Vite 大 chunk 警告，当前不阻断部署，但后续可通过路由级动态导入优化首屏资源体积。
