# 数据管理平台部署文档

## 环境要求

### 基础软件

| 软件 | 版本要求 | 说明 |
|------|----------|------|
| Java | 21+ | OpenJDK 或 Oracle JDK |
| Maven | 3.9+ | 构建工具 |
| Node.js | 18+ | 前端构建 |
| Docker | 24+ | 容器化部署 |
| Docker Compose | 2.x | 容器编排 |

### 基础设施

| 组件 | 版本 | 端口 | 说明 |
|------|------|------|------|
| PostgreSQL | 16 | 5432 | 主数据库 |
| Redis | 7.x | 6379 | 缓存/会话 |

---

## 快速部署

### 1. 克隆项目

```bash
git clone <repository-url>
cd data-manager-hub
```

### 2. 启动基础设施

```bash
# 使用 Docker Compose
docker-compose up -d

# 或手动启动 PostgreSQL 和 Redis
```

### 3. 初始化数据库

```bash
# 连接数据库
psql -h localhost -U postgres -d dataplatform

# 执行初始化脚本
\i sql/init.sql
```

### 4. 构建项目

```bash
# 编译所有模块
mvn clean install -DskipTests

# 或跳过测试加速构建
mvn clean install -DskipTests -Dmaven.test.skip=true
```

### 5. 启动服务

**方式一: 使用 Maven**

```bash
# 按顺序启动各服务

# 1. 网关服务
cd data-platform-gateway
mvn spring-boot:run &

# 2. 厂商服务
cd ../data-platform-vendor
mvn spring-boot:run &

# 3. 调用方服务
cd ../data-platform-caller
mvn spring-boot:run &

# 4. 调用记录服务
cd ../data-platform-call
mvn spring-boot:run &

# 5. 计费服务
cd ../data-platform-billing
mvn spring-boot:run &

# 6. 监控服务
cd ../data-platform-monitor
mvn spring-boot:run &

# 7. 租户服务
cd ../data-platform-tenant
mvn spring-boot:run &
```

**方式二: 使用 JAR 包**

```bash
# 打包
mvn clean package -DskipTests

# 启动各服务
java -jar data-platform-gateway/target/data-platform-gateway-1.0.0-SNAPSHOT.jar &
java -jar data-platform-vendor/target/data-platform-vendor-1.0.0-SNAPSHOT.jar &
# ... 其他服务
```

### 6. 启动前端

```bash
cd data-platform-web
npm install
npm run dev
```

---

## 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| Gateway | 8888 | API 网关 |
| Vendor | 8081 | 厂商管理（含数据类型、配置中心） |
| Caller | 8082 | 调用方管理 |
| Call | 8083 | 调用记录 |
| Billing | 8084 | 计费管理 |
| Monitor | 8085 | 监控告警 |
| Tenant | 8086 | 租户管理 |
| SDK | 8087 | SDK生成 |
| Log | 8090 | 操作日志 |
| Graylog | 8092 | 灰度发布 |
| IAM | 8093 | 用户权限管理（含用户、角色） |
| Security | 8094 | 数据安全 |
| Trace | 8095 | 数据血缘 |
| Quality | 8096 | 数据质量 |
| Interface | 8097 | 接口管理 |
| Web | 3000 | 前端界面 |

---

## 配置说明

### 数据库配置

各服务的 `application.yml` 中配置:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:dataplatform}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
```

### Redis 配置

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
```

### 环境变量

创建 `.env` 文件:

```bash
# 数据库
DB_HOST=localhost
DB_PORT=5432
DB_NAME=dataplatform
DB_USER=postgres
DB_PASSWORD=your_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Nacos (服务发现)
NACOS_SERVER_ADDR=localhost:8848
NACOS_NAMESPACE=dev
```

### Sa-Token 认证配置

所有需要认证的服务都配置了 Sa-Token，配置示例：

```yaml
sa-token:
  token-name: Authorization
  timeout: 7200
  is-concurrent: true
  is-share: false
  token-style: uuid
  token-prefix: Bearer
```

**关键配置说明**：
- `token-prefix: Bearer` — 支持 `Authorization: Bearer <token>` 格式，前端需在请求头中携带此格式

需要 Sa-Token 的服务：
- Gateway (8888)
- Vendor (8081)
- Caller (8082)
- Call (8083)
- Billing (8084)
- Monitor (8085)
- Tenant (8086)
- Log (8090)
- Graylog (8092)
- IAM (8093)
- Security (8094)
- Trace (8095)
- Quality (8096)
- Interface (8097)

---

## 生产部署

### Docker 部署

```bash
# 构建镜像
docker build -t data-platform:latest .

# 运行容器
docker run -d \
  --name data-platform \
  -p 8888:8888 \
  -e DB_HOST=postgres \
  -e REDIS_HOST=redis \
  data-platform:latest
```

### Kubernetes 部署

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: data-platform
spec:
  replicas: 3
  selector:
    matchLabels:
      app: data-platform
  template:
    spec:
      containers:
      - name: data-platform
        image: data-platform:latest
        ports:
        - containerPort: 8888
        env:
        - name: DB_HOST
          valueFrom:
            configMapKeyRef:
              name: data-platform-config
              key: db-host
```

---

## 健康检查

### 服务健康端点

```bash
# 检查各服务健康状态
curl http://localhost:8888/actuator/health
curl http://localhost:8081/actuator/health
# ... 其他服务
```

### 数据库连接测试

```bash
psql -h localhost -U postgres -d dataplatform -c "SELECT 1"
```

### Redis 连接测试

```bash
redis-cli ping
```

---

## 日志管理

### 日志位置

默认日志输出到控制台，可通过配置输出到文件:

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

## 常见问题

### 1. 端口被占用

```bash
# 查找占用进程
lsof -i :8888

# 终止进程
kill -9 <PID>
```

### 2. 数据库连接失败

检查:
- PostgreSQL 服务是否运行
- 用户名密码是否正确
- 数据库是否已创建

### 3. Redis 连接失败

检查:
- Redis 服务是否运行
- 端口是否正确
- 是否需要密码认证

### 4. 编译失败

```bash
# 清理并重新构建
mvn clean install -U -DskipTests
```

---

## 监控指标

### JVM 监控

```bash
# 获取 JVM 信息
curl http://localhost:8888/actuator/jvm-info
```

### 性能指标

```bash
# 获取性能指标
curl http://localhost:8888/actuator/metrics
```

---

## 备份与恢复

### 数据库备份

```bash
pg_dump -h localhost -U postgres dataplatform > backup_$(date +%Y%m%d).sql
```

### 数据库恢复

```bash
psql -h localhost -U postgres dataplatform < backup_20260426.sql
```

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
    key-store-type: PKCS12
```

### 3. 配置防火墙

```bash
# 只允许特定 IP 访问
iptables -A INPUT -p tcp --dport 8888 -s 10.0.0.0/8 -j ACCEPT
iptables -A INPUT -p tcp --dport 8888 -j DROP
```
