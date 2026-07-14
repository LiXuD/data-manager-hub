# 数据管理平台部署文档

**版本**: 2026-07-14

---

## 环境要求

### 基础软件

| 软件 | 版本要求 | 说明 |
|------|----------|------|
| Java | 21+ | OpenJDK 或 Oracle JDK |
| Maven | 3.9+ | 构建工具 |
| Node.js | 18+ | 前端构建 |
| Docker | 24+ | 容器化部署 |
| Docker Compose | 2.x | 容器编排 |
| OpenSSL | 3.x | 本地生成服务间认证 RSA 密钥 |

### 基础设施

| 组件 | 版本 | 端口 | 说明 |
|------|------|------|------|
| PostgreSQL | 16 | 5432 | 主数据库 |
| Redis | 7.x | 6379 | 缓存/会话 |
| SkyWalking OAP | 9.4.0 | 11800/12800 | 链路追踪服务端 (gRPC/HTTP) |
| SkyWalking UI | 9.4.0 | 8088 | 链路追踪可视化 |

---

## 快速部署

### 1. 克隆项目

```bash
git clone https://github.com/LiXuD/data-manager-hub.git
cd data-manager-hub
```

### 2. 启动本地基础设施

```bash
docker compose up -d
```

> `docker-compose.yml` 仅用于本地开发/测试，包含 PostgreSQL、Redis、Kafka、Nacos、Prometheus、Grafana、Elasticsearch、Kibana 和 SkyWalking。生产环境应使用独立的高可用基础设施，并通过环境变量或密钥系统提供连接信息和密码。

如本机 5432 已被占用，可改用备用宿主端口：

```bash
POSTGRES_PORT=15432 docker compose up -d postgres
export DB_PORT=15432
```

### 3. 初始化数据库

```bash
psql -h localhost -U postgres -d dataplatform -f sql/init.sql
for migration in sql/migrations/*.sql; do
  psql -h localhost -U postgres -d dataplatform -f "$migration"
done
```

### 4. 构建项目

```bash
mvn clean install -DskipTests
```

### 5. 启动服务

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

### 6. 启动前端

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
| access | data-platform-access | 8082 | 调用方/API Key/调用 |
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
| Java | `client-java.ftl` + `model-java.ftl` | Maven 项目结构 |
| Python | `client-python.ftl` + `model-python.ftl` | pip 可安装 |
| Go | `client-go.ftl` + `model-go.ftl` | go module |

---

## 配置说明

### 数据库配置

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

### Redis 配置

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      password: ${REDIS_PASSWORD}
```

### Nacos 配置

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR}
        namespace: ${NACOS_NAMESPACE:prod}
```

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
    ├─→ Masterdata (8081) - 获取厂商配置/接口定义 (Feign)
    ├─→ Billing (8084) - 计算调用费用 (Feign)
    └─→ Governance (8085) - 写入操作日志 (Feign)

Billing (8084)
    │
    ├─→ Access (8082) - 对账统计 (Feign)
    └─→ Governance (8085) - 告警与操作日志 (Feign)

Masterdata
    │
    ├─→ Access (8082) - 接口调用统计 (Feign)
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

# SkyWalking
export SW_AGENT_ENABLED=true
export SW_OAP_ADDRESS=skywalking-oap:11800
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

**文档版本**: 2026-07-10
**最后更新**: 补充全新数据库的迁移执行步骤，并与当前五域部署基线保持一致。
