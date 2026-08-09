# 数据管理平台部署文档

**版本**: 2026-08-10

---

## 环境要求

### 基础软件

| 软件 | 版本要求 | 说明 |
|------|----------|------|
| Java | 21+ | OpenJDK 或 Oracle JDK |
| Maven | 3.9+ | 构建工具 |
| Node.js | 20.19+、22.13+ 或 24+ | 前端构建 |
| Docker | 24+ | 容器化部署 |
| Docker Compose | 2.x | 容器编排 |
| OpenSSL | 3.x | 本地生成服务间认证 RSA 密钥 |

### 基础设施

| 组件 | 版本 | 端口 | 说明 |
|------|------|------|------|
| PostgreSQL | 16 | 5432 | 主数据库 |
| Redis | 7.x | 6379 | 缓存/会话 |
| Nacos | 2.3.x | 8848 | 服务注册与配置中心 |
| SkyWalking OAP | 9.4.0 | 11800/12800 | 链路追踪服务端 (gRPC/HTTP) |
| SkyWalking UI | 9.4.0 | 8088 | 链路追踪可视化 |
| Nexus/S3 兼容 HTTPS 制品库 | 由环境提供 | 443 | 连接器 JAR；坐标不可覆盖，仓库主机和路径必须在白名单 |

---

## 快速部署

### 1. 克隆项目

```bash
git clone https://github.com/LiXuD/data-manager-hub.git
cd data-manager-hub
```

### 2. 启动本地基础设施

```bash
# 使用本机 PostgreSQL
docker compose up -d redis kafka nacos
```

> `docker-compose.yml` 仅用于本地开发/测试，包含 PostgreSQL、Redis、Kafka、Nacos、Prometheus、Grafana、Elasticsearch、Kibana 和 SkyWalking。生产环境应使用独立的高可用基础设施，并通过环境变量或密钥系统提供连接信息和密码。

如需使用 Compose PostgreSQL，可改用备用宿主端口：

```bash
POSTGRES_PORT=15432 docker compose up -d postgres
export DB_PORT=15432
```

### 3. 发布 Nacos 配置

```bash
./publish-nacos-config.sh dev
```

应用采用 Spring Boot `spring.config.import` 标准机制加载 Nacos Config。五个业务域加载共享数据库 Data ID 和各自服务 Data ID，Gateway 只加载自身 Data ID。Data ID 缺失或 Nacos 不可用时应用拒绝启动，避免退回不完整的本地配置。

### 4. 初始化数据库

```bash
DB_PASSWORD=123456 ./migrate-db.sh dry-run
DB_PASSWORD=123456 ./migrate-db.sh update
```

Liquibase 使用 `DATABASECHANGELOG` 和 `DATABASECHANGELOGLOCK` 管理顺序、校验和与并发锁。旧的手工初始化数据库必须先执行 `./migrate-db.sh backup`，再用 `MIGRATION_CONFIRM_BASELINE=<数据库名> ./migrate-db.sh baseline` 接管，不能直接重复执行历史 SQL。`start-services.sh` 默认也会在任何 Java 服务启动前执行 `update`。

接口权限审批由 V026 创建业务表，并使用 Flowable 7.1.0 官方 PostgreSQL 脚本在独立 `workflow` schema 创建引擎表。生产环境保持 `flowable.database-schema-update=false`；应用通过表前缀访问 `workflow`，业务 MyBatis 仍固定使用 `public` schema。引擎表只能经 Liquibase 升级，禁止应用启动时自动建表或手工修改已登记 changeset。

V028 将结果缓存策略纳入申请项和最终授权事实。由于旧服务端未限制缓存天数，存量接口授权兼容回填为“允许缓存、上限 365 天”；新授权默认不允许缓存，必须通过审批显式开通。新建产品缓存作用域默认由 `GLOBAL` 收紧为 `CALLER`。回滚脚本会在发现新缓存申请、审批或非兼容授权事实时拒绝执行，发布前应使用隔离数据库完成 update、rollback、re-update 演练。

V042 增加 Masterdata 所有的插件目录/连接器版本和不可变受控测试事实、Access 所有的逐实例激活事实、
`vendor_config.runtime_mode/active_connector_version_id/connector_version` 以及 `call_record` 插件追踪字段，
并种入内置 `legacy-http:1.0.0`。U042 只允许在不存在真实插件、连接器草稿/发布版本、激活事实、
受控测试事实、PLUGIN 绑定和插件调用事实时回滚；出现任一事实后必须备份并做前向恢复，不能强制执行 U042。

后续连接器迁移必须连续应用到 V047：

- V043：迁移计划和 Access/Billing 观察事实；完成后迁移控制面只读；
- V044：失败关闭地为存量配置建立活动连接器并强制 PLUGIN-only；
- V045：删除旧适配器配置列；
- V046：新增 `V1_DERIVED/V2_EMBEDDED` 完整性事实，不改写旧快照、调用或计费历史；
- V047：升级前核对目录、发布步骤和调用/计费事实，随后冻结插件制品、发布版本和物理删除。

V043—V047 在坏目录/完整性历史上必须原子 HALT，禁止临时关闭 precondition 或原地修历史。发布前
执行 `backup + validate + dry-run`，在隔离 PostgreSQL 同时验证 fresh V001—V047 和 V046→V047。
受保护事实产生后，U043—U047 不作为普通回滚路径；使用升级前备份恢复或新增 forward-recovery
changeset。精确策略见 `sql/MIGRATIONS.md`。

发布新审批节点时，将经过评审的 BPMN 作为 `data-platform-access-service/src/main/resources/processes/` 下的新版本资源发布。新申请使用最新版本，运行中实例继续原定义；禁止在线暴露 Flowable REST、引擎 Actuator 管理端点或 workflow schema。

### 5. 构建项目

```bash
mvn clean install -DskipTests
```

### 6. 启动服务

**使用一键启动脚本 (推荐)**:

```bash
./start-services.sh
```

**使用 Maven 单独启动**:

```bash
# 按顺序启动各服务，Identity 必须先可用
cd data-platform-identity/data-platform-identity-service && mvn spring-boot:run &
cd data-platform-masterdata/data-platform-masterdata-service && mvn spring-boot:run &
cd data-platform-access/data-platform-access-service && mvn spring-boot:run &
cd data-platform-billing/data-platform-billing-service && mvn spring-boot:run &
cd data-platform-governance/data-platform-governance-service && mvn spring-boot:run &
cd data-platform-gateway && mvn spring-boot:run &
```

### 7. 启动前端

```bash
cd data-platform-web
npm install
npm run dev
```

---

## 服务端口

| 域 | 服务 | 端口 | 说明 |
|----|------|------|------|
| - | Gateway | 8888 | API 网关 |
| masterdata | data-platform-masterdata | 8081 | 厂商/数据类型/接口/灰度 |
| access | data-platform-access | 8082 | 调用方/API Key/接口权限审批/调用 |
| billing | data-platform-billing | 8084 | 计费 |
| identity | data-platform-identity | 8086 | 身份/租户/安全 |
| governance | data-platform-governance | 8085 | 监控/日志/质量/血缘 |
| - | Web | 3000 | 前端界面 |

> **注意**: `data-platform-sdk` 是普通 Jar 依赖，不作为独立服务部署。

---

## 链路追踪 (SkyWalking)

### 启动 SkyWalking

本地 `docker-compose up -d` 已包含 SkyWalking OAP 和 UI 容器。UI 访问地址: `http://localhost:8088`。生产环境不要使用 compose 中的 H2 存储，应配置 Elasticsearch 等持久化存储。

### 启用 Agent

服务默认不附加 SkyWalking Agent。如需启用:

```bash
SW_AGENT_ENABLED=true ./start-services.sh
```

首次启用会自动下载 Agent (约 15MB)。Agent 配置位于 `skywalking/agent.config`。

### 追踪传播机制

- **Gateway**: 为每个请求生成/透传 `X-Trace-Id` 头
- **Feign 调用**: `TraceFeignRequestInterceptor` 自动传播 `X-Trace-Id` 到下游服务
- **日志关联**: `TraceIdMdcFilter` 将 `X-Trace-Id` 写入 SLF4J MDC，日志 pattern 中通过 `%X{traceId}` 引用
- **业务关联**: `call_record.trace_id` 列存储请求级 Trace ID，可与 SkyWalking trace 关联

### 验证链路

```bash
# 启动服务并验证 trace 传播
./skywalking/verify-trace.sh
```

---

## SDK 代码生成

`data-platform-sdk` 是普通 Jar 依赖，不独立部署。使用 Freemarker 模板引擎生成多语言 SDK 客户端代码。

### 生成命令

```bash
# Java SDK
java -cp data-platform-sdk.jar com.dataplatform.sdk.generator.SDKCli --lang java --base-url http://localhost:8888 --output ./sdk-java

# Python SDK
java -cp data-platform-sdk.jar com.dataplatform.sdk.generator.SDKCli --lang python --base-url http://localhost:8888 --output ./sdk-python

# Go SDK
java -cp data-platform-sdk.jar com.dataplatform.sdk.generator.SDKCli --lang go --base-url http://localhost:8888 --output ./sdk-go
```

### 支持语言

| 语言 | 模板 | 说明 |
|------|------|------|
| Java | `java-client.ftl` + `java-model.ftl` | Java 客户端与模型 |
| Python | `python-client.ftl` + `python-model.ftl` | Python 客户端与模型 |
| Go | `go-client.ftl` + `go-model.ftl` | Go 客户端与模型 |

---

## 配置说明

### Nacos Config

```yaml
spring:
  config:
    import:
      - nacos:data-platform-database-${spring.profiles.active}.properties?group=DEFAULT_GROUP&refreshEnabled=true
      - nacos:${spring.application.name}-${spring.profiles.active}.yml?group=DEFAULT_GROUP&refreshEnabled=true
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_SERVER_ADDR}
        namespace: ${NACOS_NAMESPACE:prod}
      discovery:
        server-addr: ${NACOS_SERVER_ADDR}
        namespace: ${NACOS_NAMESPACE:prod}
```

版本化配置模板位于 `nacos-config/`，通过 `publish-nacos-config.sh` 发布。应用本地不再保存数据库、Redis、Kafka、Gateway 路由等业务运行配置。生产模板中的密码和密钥占位符必须由部署环境或密钥系统提供。

### Sa-Token 认证配置

```yaml
sa-token:
  token-name: Authorization
  timeout: 7200
  token-style: uuid
  token-prefix: Bearer
```

用户会话通过 `sa-token-redis-jackson` 存入共享 Redis。Identity 负责登录和签发用户 Token，其他业务域在本地读取同一会话并校验；生产环境必须保证五域服务使用相同 Redis 实例和 `Authorization` token-name。

### 服务间认证配置

跨域 Feign 调用使用 Identity 签发的短期 RSA JWT，内部端点统一为 `/internal/v1/**`。目标服务本地校验签名、issuer、audience、有效期和 scope；Identity 按 `clients.<service>.grants.<audience>` 只签发该目标允许的最小 scope。用户 Token 与 API Key 不作为服务身份传播。

生产环境必须由密钥管理系统挂载 RSA 密钥，并为每个服务提供独立客户端密钥：

```bash
export INTERNAL_AUTH_ENABLED=true
export INTERNAL_AUTH_TOKEN_URI=http://data-platform-identity:8086/internal-auth/v1/token
export INTERNAL_AUTH_PRIVATE_KEY_PATH=/run/secrets/internal-auth-private.pem  # 仅 Identity
export INTERNAL_AUTH_PUBLIC_KEY_PATH=/run/secrets/internal-auth-public.pem
export INTERNAL_AUTH_ACCESS_SECRET=...
export INTERNAL_AUTH_BILLING_SECRET=...
export INTERNAL_AUTH_MASTERDATA_SECRET=...
export INTERNAL_AUTH_IDENTITY_SECRET=...
```

五域 dev profile 默认开启内部认证。本地 `start-services.sh` 会在被 Git 忽略的 `.runtime/` 中生成临时 RSA 密钥，并等待 Identity 健康后再启动依赖服务。令牌客户端默认连接超时 2 秒、读取超时 5 秒、最多尝试 3 次；4xx 凭证错误不重试。Gateway 不路由 `/internal/**`，并清理外部请求中的 `X-Actor-*`、`X-Internal-*` 可信头。

最小授权关系：Access 可读 Masterdata、读取厂商密钥、调用 Billing 和写 Governance 日志；Billing 可读 Access 统计并写 Governance 告警/日志；Masterdata 可读 Access 统计并写 Governance 日志；Identity 仅写 Governance 日志。

### 连接器制品、签名和运行时配置

连接器有两个独立信任检查：Masterdata 在导入时验证目录数据，Access 在实际加载前再次验证本地
缓存。同一 `keyId` 必须使用同一公钥，但配置格式不同：

- `CONNECTOR_SIGNING_PUBLIC_KEY_BASE64`：X.509 DER 公钥的 Base64，供 Masterdata V1 Ed25519 验证；
- `CONNECTOR_SIGNING_PUBLIC_KEY_RESOURCE`：只读 `file:` 或 `classpath:` PEM，供 Access 加载时验证；
- JVM TLS TrustStore：信任制品库和厂商 HTTPS 证书，和插件签名公钥不是同一套密钥。

生产环境必须提供以下变量，不能使用 dev 的 `*.invalid` 失败关闭占位值：

```bash
export CONNECTOR_ARTIFACT_REPOSITORY_HOST=plugins.example.com
export CONNECTOR_ARTIFACT_REPOSITORY_PATH=/repository/data-platform
export CONNECTOR_ARTIFACT_REPOSITORY_PREFIX=https://plugins.example.com/repository/data-platform
export CONNECTOR_SIGNING_PUBLIC_KEY_BASE64='<X.509 DER Base64>'
export CONNECTOR_SIGNING_PUBLIC_KEY_RESOURCE=file:/run/secrets/connector-signing-public.pem
export CONNECTOR_PLUGIN_CACHE_DIR=/var/lib/data-platform/plugins
export CONNECTOR_INSTANCE_ID="${HOSTNAME}:8082"
export CONNECTOR_HOST_VERSION=1.0.0
export CONNECTOR_VENDOR_ALLOWED_HOST=api.vendor.example.com
export JAVA_TOOL_OPTIONS='-Djavax.net.ssl.trustStore=/run/secrets/connector-ca.p12 -Djavax.net.ssl.trustStoreType=PKCS12 -Djavax.net.ssl.trustStorePassword=***'
```

当前 Nacos 键由以下两个前缀绑定：

```yaml
masterdata.connector-plugin:
  artifact-allowed-hosts: [${CONNECTOR_ARTIFACT_REPOSITORY_HOST}]
  artifact-allowed-path-prefixes: [${CONNECTOR_ARTIFACT_REPOSITORY_PATH}]
  trusted-signing-keys:
    platform-default: ${CONNECTOR_SIGNING_PUBLIC_KEY_BASE64}
  max-artifact-bytes: ${CONNECTOR_MAX_ARTIFACT_BYTES:52428800}
  max-manifest-bytes: ${CONNECTOR_MAX_MANIFEST_BYTES:262144}
  max-schema-bytes: ${CONNECTOR_MAX_SCHEMA_BYTES:131072}

connector.runtime:
  instance-id: ${CONNECTOR_INSTANCE_ID}
  host-version: ${CONNECTOR_HOST_VERSION}
  cache-directory: ${CONNECTOR_PLUGIN_CACHE_DIR}
  repository-allowed-prefixes: [${CONNECTOR_ARTIFACT_REPOSITORY_PREFIX}]
  network-allowed-protocols: [https]
  network-allowed-hosts: [${CONNECTOR_VENDOR_ALLOWED_HOST}]
  allow-private-networks: false
  max-connect-timeout-ms: ${CONNECTOR_MAX_CONNECT_TIMEOUT_MS:5000}
  max-read-timeout-ms: ${CONNECTOR_MAX_READ_TIMEOUT_MS:30000}
  max-total-timeout-ms: ${CONNECTOR_MAX_TOTAL_TIMEOUT_MS:60000}
  test-timeout-ms: ${CONNECTOR_TEST_TIMEOUT_MS:30000}
  max-response-bytes: ${CONNECTOR_MAX_RESPONSE_BYTES:10485760}
  signing-keys:
    platform-default:
      resource: ${CONNECTOR_SIGNING_PUBLIC_KEY_RESOURCE}
      algorithm: Ed25519
```

仓库 URI 只允许无 user-info、query 和 fragment 的 HTTPS 地址；Masterdata 同时校验主机和路径，
Access 校验完整 URI 前缀且禁止重定向。缓存路径固定为
`<cache-directory>/<pluginId>/<version>/<sha256>/connector-plugin.jar`，下载先写临时文件，哈希通过后
原子移动。缓存目录应挂载为仅 Access 服务账号可写，不可与插件构建或上传目录共享。

Access 启动后会从 Masterdata 拉取所有活动连接器所需版本。健康组件名为
`connectorRuntimeReadiness`：所需版本未完成本地加载、哈希不一致或仓库不可用且无已验证缓存时，
Access `/actuator/health` 保持 `DOWN`；已有匹配缓存可在仓库故障时恢复。预加载会按 Nacos 服务发现
中的活动 Access 实例创建 `connector_plugin_activation` 事实，只有聚合 `ready=true` 才允许激活。
发布或切换期间旧版本继续服务在途租约，引用归零后才关闭插件和 ClassLoader。

生产至少部署两个具有唯一 `CONNECTOR_INSTANCE_ID` 的 Access 实例。新实例在当前活动绑定全部预加载
完成前 readiness 必须保持 DOWN；候选版本只有所有服务发现中的活动实例 READY 后才能激活。任一
实例失败时旧 ACTIVE 继续服务。实例下线后由服务发现更新活动集合，不永久阻塞；发布/回滚/禁用/
解绑在事务提交后触发 release，定时 required-artifact 对账重试部分失败并释放无绑定版本。

内部 JWT 最小 scope 还包括：Access 读取制品 `masterdata:connector-artifact:read`、读取运行快照
`masterdata:connector-runtime:read`；Masterdata 查询/管理 Access 激活状态和受控测试分别使用
`access:connector-runtime:read`、`access:connector-runtime:manage`、`access:connector-runtime:test`。

### 连接器供应链 CI

插件源码必须在独立 CI 构建、测试、扫描、计算 SHA-256，并由受信离线私钥对规范化 Manifest 与
JAR 哈希进行 Ed25519 脱离签名；平台只导入 HTTPS 制品坐标，不提供本地 JAR 执行入口。同坐标不得
覆盖。宿主仓库的可复现扫描命令为：

```bash
mvn -B -ntp -Pconnector-supply-chain-scan -DskipTests -DskipTests=true verify
```

`.github/workflows/connector-plugin-supply-chain.yml` 在相关变更上执行 OWASP Dependency-Check、CycloneDX
SBOM 和许可证报告；`NVD_API_KEY` 只配置在 CI Secret。危险字节码门禁属于日常离线测试与导入/加载
路径，不依赖在线服务，并拒绝直接 Socket/URL/HttpClient、文件系统、宿主反射、System/ClassLoader、
Thread/Executors 和 native load。扫描失败或签名/哈希/白名单不匹配时不得导入或激活。

### 连接器发布与回滚 Runbook

1. 通过管理 API import/verify，确认制品哈希、签名 keyId、SPI/Host 版本和权限清单。
2. stage 后查看逐实例 activation；任何实例不是 READY 都停止，不能调用 activate。
3. 使用目标 vendor 草稿完成 Schema/secretRef 校验和有界受控测试，再以 CAS 发布。
4. 观察错误率、P95、ClassLoader gauge、缓存/计费和实际完整性事实，按批次放量。
5. 运行错误时对厂商连接器执行历史版本 rollback；该动作创建新发布版本，不修改旧历史。
6. 制品加载错误时保持旧 ACTIVE，修复制品/信任配置后重新 stage；不要删除目录事实。
7. 数据库 changeset 错误使用升级前备份恢复或 forward recovery；不要强制执行受保护 U 脚本。

### 隔离连接器 E2E 夹具

`data-platform-test/test-fixtures/connector-e2e` 提供最小外部插件、Ed25519 签名、localhost HTTPS
制品库/厂商端点、PKCS12 TrustStore 和唯一 PostgreSQL 测试库。它只用于测试，不改变生产默认值。

```bash
E2E_DB_HOST=localhost \
E2E_DB_PORT=5432 \
E2E_DB_USERNAME=postgres \
E2E_DB_PASSWORD=postgres \
  ./data-platform-test/test-fixtures/connector-e2e/prepare-e2e.sh
```

脚本输出 `E2E_STATE_FILE`、制品 URI/哈希/签名/`keyId`、两种公钥格式、TLS TrustStore、导入请求 JSON
和 `FIXTURE_VENDOR_CONFIG_ID`。把输出值注入隔离服务进程后，按 `docs/API.md` 完成导入、stage、
activate、草稿、validate、test、publish 和 OpenAPI 调用。`prepare-e2e.sh` 自身验证的是制品/TLS/迁移
夹具，不代表六服务链路已经通过。

验收结束必须使用脚本输出的精确状态文件清理；脚本会校验 PID、数据库名和目录归属后才删除：

```bash
./data-platform-test/test-fixtures/connector-e2e/cleanup-e2e.sh "$E2E_STATE_FILE"
```

2026-08-10 已按此隔离方式启动五域、Gateway、Web 和双 Access，完成签名插件、控制面、单条/批量
OpenAPI、权限/限流/配额、缓存/契约、delivery/主备/计费、离线缓存/readiness、并发切换、卸载和浏览器
验收。清理后数据库、Nacos、Redis、缓存、进程、端口和凭据残留为 0。精确验收结论见
[外部请求连接器插件化升级设计第 0.1 节](2026-08-03-external-request-connector-plugin-upgrade-design.md#01-隔离运行环境与浏览器验收记录)；
该隔离证据不替代生产容量和放量验收。

---

## 服务依赖关系

```
Gateway (8888)
    │
    ├─→ Masterdata (8081) - 厂商/接口/灰度
    ├─→ Access (8082) - 调用方/调用
    ├─→ Billing (8084) - 计费
    ├─→ Identity (8086) - 身份/租户
    └─→ Governance (8085) - 监控/日志/质量

Access (8082)
    │
    ├─→ Masterdata (8081) - 获取厂商配置/接口定义/连接器制品与快照 (Feign)
    ├─→ Billing (8084) - 计算调用费用 (Feign)
    └─→ Governance (8085) - 写入操作日志 (Feign)

Billing (8084)
    │
    ├─→ Access (8082) - 对账统计 (Feign)
    └─→ Governance (8085) - 告警与操作日志 (Feign)

Masterdata
    │
    ├─→ Access (8082) - 接口调用统计/插件激活/受控测试 (Feign)
    └─→ Governance (8085) - 写入操作日志 (Feign)

Identity
    │
    └─→ Governance (8085) - 写入操作日志 (Feign)
```

`call-record` Kafka 仅在 Access 域内用于调用记录异步落库。Billing 不消费该主题，费用与日聚合通过认证 Feign 同步完成。

Access 的生产 profile 默认使用 `SASL_SSL` 与 `SCRAM-SHA-512`。生产环境必须提供 `KAFKA_BOOTSTRAP_SERVERS`、`KAFKA_USERNAME`、`KAFKA_PASSWORD`、`KAFKA_SSL_TRUSTSTORE_LOCATION` 和 `KAFKA_SSL_TRUSTSTORE_PASSWORD`；如基础设施采用其他安全机制，应显式覆盖 `KAFKA_SECURITY_PROTOCOL` 与 `KAFKA_SASL_MECHANISM`，不得退回明文匿名连接。

---

## 启动与停止

### 一键启动

```bash
./start-services.sh
```

### 一键停止

```bash
./stop-services.sh
```

### 启动顺序

```
1. 基础设施 (PostgreSQL, Redis, Kafka, Nacos)
2. Identity（服务 Token 签发）
3. Masterdata / Billing / Governance
4. Access
5. Gateway
6. Web
```

一键脚本简化为: `./start-services.sh`（已包含步骤 2-5）。

---

## 健康检查

```bash
curl http://localhost:8888/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8084/actuator/health
curl http://localhost:8085/actuator/health
curl http://localhost:8086/actuator/health
```

---

## 常见问题

### 端口被占用

```bash
lsof -i :8081
kill -9 <PID>
```

### 数据库连接失败

- 检查 PostgreSQL 服务是否运行
- 检查用户名密码是否正确
- 检查数据库是否已创建

### Redis 连接失败

- 检查 Redis 服务是否运行
- 检查密码是否正确

### 编译失败

```bash
mvn clean install -U -DskipTests
```

---

## 生产部署

### 环境变量

```bash
# 数据库
export DB_HOST=postgres-server
export DB_PORT=5432
export DB_NAME=dataplatform
export DB_USERNAME=dataplatform_app
export DB_PASSWORD=your_password

# Redis
export REDIS_HOST=redis-server
export REDIS_PORT=6379
export REDIS_PASSWORD=your_redis_password

# Nacos
export NACOS_SERVER_ADDR=nacos-server:8848
export NACOS_NAMESPACE=prod

# SkyWalking
export SW_AGENT_ENABLED=true
export SW_OAP_ADDRESS=skywalking-oap:11800

# 连接器（示例；真实值来自部署 Secret/只读挂载）
export CONNECTOR_ARTIFACT_REPOSITORY_HOST=plugins.example.com
export CONNECTOR_ARTIFACT_REPOSITORY_PATH=/repository/data-platform
export CONNECTOR_ARTIFACT_REPOSITORY_PREFIX=https://plugins.example.com/repository/data-platform
export CONNECTOR_SIGNING_PUBLIC_KEY_BASE64='<X.509 DER Base64>'
export CONNECTOR_SIGNING_PUBLIC_KEY_RESOURCE=file:/run/secrets/connector-signing-public.pem
export CONNECTOR_PLUGIN_CACHE_DIR=/var/lib/data-platform/plugins
export CONNECTOR_INSTANCE_ID="${HOSTNAME}:8082"
```

### Docker 部署

```bash
# 构建镜像
docker build -t data-platform:latest .

# 运行容器
docker run -d \
  --name data-platform-gateway \
  -p 8888:8888 \
  -e DB_HOST=postgres \
  -e REDIS_HOST=redis \
  data-platform:latest
```

---

## 日志管理

### 日志位置

```yaml
logging:
  file:
    name: logs/data-platform.log
  level:
    root: INFO
    com.dataplatform: DEBUG
```

### 日志级别

| 级别 | 说明 |
|------|------|
| DEBUG | 详细调试信息 |
| INFO | 运行状态信息 |
| WARN | 警告信息 |
| ERROR | 错误信息 |

---

## 安全配置

### 1. 修改默认密码

- 数据库密码
- Redis 密码
- 管理员账户密码

### 2. 启用 HTTPS

```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_PASSWORD}
```

### 3. 配置防火墙

```bash
iptables -A INPUT -p tcp --dport 8888 -s 10.0.0.0/8 -j ACCEPT
iptables -A INPUT -p tcp --dport 8888 -j DROP
```

---

## 备份与恢复

### 数据库备份

```bash
pg_dump -h localhost -U postgres dataplatform > backup_$(date +%Y%m%d).sql
```

### 数据库恢复

```bash
psql -h localhost -U postgres dataplatform < backup_20260516.sql
```

---

**文档版本**: 2026-08-10
**最后更新**: 同步 V042—V047、PLUGIN-only、供应链 CI、双 Access 激活/readiness、release/回滚和隔离 E2E。
