# 🏦 数据管理平台 (Data Management Platform)

> 银行数据管理平台 - 厂商对接、数据调用、计费管理、监控告警一体化解决方案

## 项目简介

基于设计文档构建的银行数据管理平台，为银行提供统一的数据厂商对接、数据调用、计费管理、监控告警的一体化解决方案。

### MVP 范围

- **3家厂商**: 工商信息×2家、手机验证×1家
- **2个系统**: 风控系统、信贷系统
- **2类数据**: 工商信息、个人信息

---

## 🏗️ 技术栈

### 后端

| 技术 | 版本 |
|------|------|
| Java | 21 |
| Spring Boot | 3.4 |
| Spring Cloud | 2024.0.0 |
| MyBatis-Plus | 3.5 |
| PostgreSQL | 16 |
| Redis | 7.4 |
| Nacos | 2.3 (本地配置模式) |

### 前端

| 技术 | 版本 |
|------|------|
| Vue | 3.5 |
| TypeScript | 5.x |
| Element Plus | 2.x |
| Vite | 5.x |
| Pinia | 2.x |

---

## 📦 模块架构

```
data-platform/
├── data-platform-common-contract/    # 通用契约：Result/PageResult、错误码、基础枚举/常量
├── data-platform-common-web/         # Web公共能力：异常、拦截器、MVC配置
├── data-platform-common-persistence/ # 持久化公共能力：MyBatis配置、审计字段
├── data-platform-common-runtime/     # 运行时公共能力
├── data-platform-gateway/       # API网关 (端口 8888)
├── data-platform-masterdata/    # 主数据服务 (端口 8081)：vendor/datatype/interface/config/graylog
│   ├── data-platform-masterdata-api/
│   └── data-platform-masterdata-service/
├── data-platform-access/        # 访问服务 (端口 8082)：caller/call/API Key/调用记录
│   ├── data-platform-access-api/
│   └── data-platform-access-service/
├── data-platform-billing/       # 计费服务 (端口 8084)
│   ├── data-platform-billing-api/
│   └── data-platform-billing-service/
├── data-platform-governance/    # 观测治理服务 (端口 8085)：monitor/log/quality/trace
│   ├── data-platform-governance-api/
│   └── data-platform-governance-service/
├── data-platform-identity/      # 身份租户服务 (端口 8086)：tenant/user/role/security
│   ├── data-platform-identity-api/
│   └── data-platform-identity-service/
├── data-platform-sdk/           # SDK客户端/代码生成 Jar，不独立部署
├── data-platform-test/          # 测试模块
└── data-platform-web/           # 前端 Vue3 项目
```

> **当前服务边界**: 项目已收敛为 masterdata / access / billing / identity / governance 五个业务域；旧 vendor/caller/call/tenant/iam/log/monitor/trace/quality/interface/graylog/security 小服务已退役。

同步跨域调用只依赖目标域 `*-api` 中的 Internal Feign 契约，统一使用 `/internal/v1/**` 和 Identity 签发的短期 Service JWT；每个客户端按 audience 获取最小 scope，Gateway 不暴露内部路径。领域表由所属域独占访问，跨域统计通过 Access 内部契约查询。

`call-record` Kafka 仅承担 Access 域内调用记录异步落库。Access 同步调用 Billing 完成费用计算与幂等日聚合，计费失败直接向上游返回错误，不生成假价格。

---

## 🗄️ 数据库模型

核心表由 `sql/init.sql` 与 `sql/migrations/` 共同定义；`call_record` 按月分区。

| 领域 | 核心表 |
|------|--------|
| masterdata | `vendor_info`、`data_type`、`vendor_config`、`vendor_config_extended`、`api_interface`、`interface_param`、`gray_rule` |
| access | `caller_info`、`caller_product`、`api_key`、`api_key_product`、`call_scene`、`call_record` |
| billing | `billing_template`、`billing_plan`、`billing_plan_tier`、`billing_event`（不可变账本）、`billing_usage_balance`、`billing_daily`（查询投影）、`billing_daily_event`、`billing_reconciliation` |
| identity | `tenant_info`、`user_info`、`role_info`、`user_role` |
| governance | `alert_rule`、`alert_record`、`circuit_breaker`、`operation_log`、`data_lineage`、`quality_rule`、`quality_score` |

---

## 📡 核心 API

### 厂商管理 (/api/v1/vendor)

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/vendor/list` | 厂商列表(分页) |
| GET | `/vendor/{id}` | 厂商详情 |
| POST | `/vendor` | 创建厂商 |
| PUT | `/vendor/{id}` | 更新厂商 |
| DELETE | `/vendor/{id}` | 删除厂商 |
| GET | `/vendor/{id}/config` | 厂商配置详情 |
| PUT | `/vendor/{id}/config` | 更新厂商配置 |

### 调用方管理 (/api/v1/caller)

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/caller/list` | 调用方列表 |
| GET | `/caller/{id}` | 调用方详情 |
| POST | `/caller` | 创建调用方 |
| PUT | `/caller/{id}` | 更新调用方 |
| DELETE | `/caller/{id}` | 删除调用方 |
| POST | `/caller/{id}/key` | 生成 API Key |
| DELETE | `/caller/{id}/key/{keyId}` | 禁用 API Key |

### 调用记录 (/api/v1/call)

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/call/record` | 调用记录查询 |
| POST | `/call/query` | 数据查询请求 |
| GET | `/call/statistics` | 调用统计 |

### 接口管理 (/api/v1/interface)

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/interface/list` | 接口列表(分页) |
| GET | `/interface/{id}` | 接口详情 |
| POST | `/interface` | 创建接口 |
| PUT | `/interface/{id}` | 更新接口 |
| DELETE | `/interface/{id}` | 删除接口 |
| PATCH | `/interface/{id}/status` | 更新接口状态 |
| GET | `/interface/{id}/schema` | 获取接口Schema |
| PUT | `/interface/{id}/schema` | 更新接口Schema |
| POST | `/interface/schema/validate` | 验证Schema格式 |
| GET | `/interface/{id}/stats` | 接口调用统计 |
| GET | `/interface/{id}/stats/daily` | 接口日统计 |
| GET | `/interface/by-data-type/{dataTypeId}` | 按数据类型获取接口 |
| GET | `/interface/{id}/params` | 获取接口参数定义 |
| POST | `/interface/{id}/params` | 新增接口参数定义 |
| PUT | `/interface/{id}/params/batch` | 批量保存接口参数定义 |
| PUT | `/interface/params/{paramId}` | 更新接口参数定义 |
| DELETE | `/interface/params/{paramId}` | 删除接口参数定义 |

### 计费管理 (/api/v1/billing)

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/billing/daily` | 日账单查询 |
| GET | `/billing/summary` | 账单汇总 |
| GET | `/billing/detail` | 账单明细 |

### 监控告警 (/api/v1/monitor)

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/monitor/alert-rule` | 告警规则列表 |
| POST | `/monitor/alert-rule` | 创建告警规则 |
| PUT | `/monitor/alert-rule/{id}` | 更新告警规则 |
| DELETE | `/monitor/alert-rule/{id}` | 删除告警规则 |
| GET | `/monitor/alert-record` | 告警记录 |
| GET | `/monitor/circuit-breaker` | 熔断记录 |

### 租户管理 (/api/v1/tenant)

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/tenant/list` | 租户列表 |
| GET | `/tenant/{id}` | 租户详情 |
| POST | `/tenant` | 创建租户 |
| PUT | `/tenant/{id}` | 更新租户 |
| DELETE | `/tenant/{id}` | 删除租户 |

---

## 🚀 快速开始

### 前置要求

- Java 21+
- Node.js 18+
- Maven 3.9+
- Docker (用于基础设施)

### 1. 启动本地基础设施

```bash
docker compose up -d
```

> `docker-compose.yml` 仅用于本地开发/测试，不作为生产部署模板。

服务端口：
- PostgreSQL: 5432
- Redis: 6379
- Nacos: 8848

如果本机已安装 PostgreSQL 并占用 5432，可使用 `POSTGRES_PORT=15432 docker compose up -d postgres` 为 compose 内数据库改用备用宿主端口；启动 Java 服务前同时设置 `DB_PORT=15432`。

### 2. 初始化数据库

```bash
# 初始化基础表，并按顺序应用迁移脚本（用于全新数据库）
psql -v ON_ERROR_STOP=1 -h localhost -U postgres -d dataplatform -f sql/init.sql
for migration in sql/migrations/*.sql; do
  psql -v ON_ERROR_STOP=1 -h localhost -U postgres -d dataplatform -f "$migration"
done
```

可在一次性数据库中验证完整初始化链路（脚本只允许删除名称匹配 `dataplatform_*_regression` 的数据库）：

```bash
PGPASSWORD=postgres DB_PORT=15432 bash verify-db-bootstrap.sh
```

### 3. 编译后端

```bash
mvn clean install -DskipTests
```

### 4. 启动微服务

```bash
# 网关 (端口 8888)
cd data-platform-gateway
mvn spring-boot:run

# 主数据服务 (端口 8081)
cd data-platform-masterdata/data-platform-masterdata-service
mvn spring-boot:run

# 或直接使用五域启动脚本
./start-services.sh
```

### 5. 启动前端

```bash
cd data-platform-web
npm install
npm run dev
```

### 访问地址

- 前端: http://localhost:3000
- API Gateway: http://localhost:8888/api/v1/

---

## 🔧 配置说明

### 环境变量

```bash
# 数据库配置
DB_HOST=localhost
DB_PORT=5432
DB_NAME=dataplatform
DB_USERNAME=postgres
DB_PASSWORD=postgres

# Redis 配置
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=redis_password

# Nacos 配置
NACOS_SERVER_ADDR=localhost:8848
NACOS_NAMESPACE=dev

# 服务间认证（dev 默认开启；启动脚本自动生成本地 RSA 密钥）
INTERNAL_AUTH_ENABLED=true
INTERNAL_AUTH_TOKEN_URI=http://localhost:8086/internal-auth/v1/token

# 字段加密主密钥（32字节随机值的Base64；必须由密钥管理系统注入）
PLATFORM_ENCRYPTION_MASTER_KEY=<base64-encoded-32-byte-key>
```

---

## 📁 目录结构

```
data-platform/
├── sql/
│   ├── init.sql                    # 基础 DDL 脚本
│   └── migrations/                 # 增量迁移脚本
├── pom.xml                         # 父 POM
├── docker-compose.yml              # 基础设施
├── data-platform-common-contract/   # 通用契约
├── data-platform-common-web/        # Web公共能力
├── data-platform-common-persistence/# 持久化公共能力
├── data-platform-common-runtime/    # 运行时公共能力
├── data-platform-gateway/          # API 网关
├── data-platform-masterdata/        # 主数据域
├── data-platform-access/            # 访问域
├── data-platform-billing/           # 计费域
├── data-platform-identity/          # 身份租户域
├── data-platform-governance/        # 观测治理域
├── data-platform-sdk/               # 纯 Jar SDK 模块
├── data-platform-test/              # 测试聚合模块
└── data-platform-web/              # 前端 Vue3
    ├── src/
    │   ├── api/                    # API 接口
    │   ├── views/                  # 页面组件
    │   ├── store/                  # Pinia 状态
    │   └── utils/                  # 工具函数
    └── package.json
```

---

## 📊 开发进度

| 阶段 | 状态 | 完成时间 |
|------|------|----------|
| DDL + CRUD | ✅ 100% | 2026-04-19 |
| 核心业务 (Call/Billing) | ✅ 100% | 2026-04-19 |
| 监控告警 (Monitor) | ✅ 100% | 2026-04-19 |
| 租户管理 (Tenant) | ✅ 100% | 2026-04-19 |
| 接口管理 (Interface) | ✅ 100% | 2026-04-26 |
| 集成测试 | ✅ 100% | 2026-04-26 |
| 代码质量修复 | ✅ 100% | 2026-04-26 |
| 五域收敛 (13→5 域合并) | ✅ 100% | 2026-05-16 |
| P1 基线守护 + 烟雾测试 + 链路回归 | ✅ 100% | 2026-05-17 |
| P2 配置热更新 + Kafka 异步化 + Prometheus | ✅ 100% | 2026-05-20 |
| SkyWalking 分布式追踪集成 | ✅ 100% | 2026-05-21 |
| 前端 API 类型化 + 联动补全 | ✅ 100% | 2026-05-17 |
| 外部系统统一入口增强 (OpenAPI) | ✅ 100% | 2026-05-19 |
| SkyWalking Agent 自动采集桥接 | ✅ 100% | 2026-05-27 |
| SDK 多语言生成 (Freemarker) | ✅ 100% | 2026-05-27 |
| 灰度发布增强 (厂商灰度路由) | ✅ 100% | 2026-05-27 |
| 全链路监控 (HTTP报文 + 厂商API日志) | ✅ 100% | 2026-06-22 |
| 上线就绪修复与本地运行态验证 | ✅ 100% | 2026-06-17 |
| 数据测试页自动填充接口参数 | ✅ 100% | 2026-07-10 |
| 跨域调用最小权限与领域数据边界整改 | ✅ 100% | 2026-07-14 |

---

## 📝 文档入口

- [架构知识库](CODE_WIKI.md)
- [API 文档](docs/API.md)
- [部署文档](docs/DEPLOYMENT.md)
- [当前任务清单](PENDING_TASKS.md)

---

## ✅ 合并前检查

PR 合入 `dev` 前必须全部通过：

```bash
# 1. Maven 依赖校验
mvn -q validate

# 2. 后端编译
mvn -q -DskipTests compile

# 3. 测试编译
mvn -q -DskipTests test-compile

# 4. 前端构建
cd data-platform-web && npm run build && cd ..

# 5. 架构边界扫描
bash arch-scan.sh
```

任一步骤失败不可合入。

---

## 📄 许可证

MIT
