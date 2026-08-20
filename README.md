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
| Spring Boot | 3.4.13 |
| Spring Cloud | 2024.0.3 |
| MyBatis-Plus | 3.5.8 |
| Flowable Process Engine | 7.1.0 |
| PostgreSQL | 16 |
| Redis | 7.4 |
| Nacos | 2.3（注册中心 + 配置中心） |

### 前端

| 技术 | 版本 |
|------|------|
| Vue | 3.5 |
| TypeScript | 5.9 |
| Element Plus | 2.13 |
| Vite | 6.4 |
| Pinia | 2.3 |

---

## 📦 模块架构

```
data-platform/
├── data-platform-common-contract/    # 通用契约：Result/PageResult、错误码、基础枚举/常量
├── data-platform-common-web/         # Web公共能力：异常、拦截器、MVC配置
├── data-platform-common-persistence/ # 持久化公共能力：MyBatis配置、审计字段
├── data-platform-common-runtime/     # 连接器加载、注册表、流水线、HTTP Transport 与内置 bridge
├── data-platform-plugin-spi/         # 六阶段连接器插件轻量 SPI Jar，不独立部署
├── data-platform-plugin-testkit/     # 高层连接器 SDK 的真实 runtime 契约测试工具与示例
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

外部请求已采用 PLUGIN-only 版本化连接器运行时：Masterdata 管理签名制品、Schema/secretRef 和
不可变厂商流水线，Access 负责 HTTPS 制品缓存、隔离加载、双实例激活/readiness、租约执行和卸载。
V044/V045 已完成存量配置迁移与旧字段/静态适配器退役；内置 `legacy-http` 只是插件内部 bridge，
不会增加第六个业务服务，也不会形成第二套执行入口。

普通连接器配置现已使用 `connectorSpec` 产品模型：配置人员只选择一个插件、固定一个版本并填写
一份 Schema 表单，Masterdata 通过 `ConnectorSpecCompiler` 确定性生成隐藏的六阶段快照，Access
仍只执行不可变 `pipelineSnapshot`。`generic-http:2.0.0` 覆盖标准单次 HTTPS，非标准协议使用
`AbstractVendorConnectorPlugin` 高层 SDK；Legacy 流水线只读可解释并可通过预检后 CAS 转换。

同步跨域调用只依赖目标域 `*-api` 中的 Internal Feign 契约，统一使用 `/internal/v1/**` 和 Identity 签发的短期 Service JWT；每个客户端按 audience 获取最小 scope，Gateway 不暴露内部路径。领域表由所属域独占访问，跨域统计通过 Access 内部契约查询。

`call-record` Kafka 仅承担 Access 域内调用记录异步落库。Access 同步调用 Billing 完成费用计算与幂等日聚合，计费失败直接向上游返回错误，不生成假价格。

---

## 🗄️ 数据库模型

核心表由 Liquibase 根变更日志 `sql/changelog/db.changelog-master.xml` 管理；`sql/init.sql` 与历史迁移固化为基线，后续变更使用独立 changeset。`call_record` 按月分区。

| 领域 | 核心表 |
|------|--------|
| masterdata | `vendor_info`、`data_type`、`vendor_config`、`vendor_config_extended`、`api_interface`、`interface_param`、`gray_rule`、`connector_plugin`、`connector_plugin_version`、`vendor_connector_version`、`vendor_connector_test_fact`、`vendor_connector_migration` |
| access | `caller_info`、`caller_product`、`api_key`、`api_key_interface`、`api_permission_application`、`api_permission_application_item`、`api_permission_action`、`api_approval_process_config`、`call_scene`、`call_record`、`connector_plugin_activation` |
| workflow | Flowable 7.1.0 原生流程、任务和历史表（独立 `workflow` schema） |
| billing | `billing_template`、`billing_plan`、`billing_plan_tier`、`billing_event`（不可变账本）、`billing_usage_balance`、`billing_daily`（查询投影）、`billing_daily_event`、`billing_reconciliation` |
| identity | `tenant_info`、`user_info`、`role_info`、`user_role` |
| governance | `alert_rule`、`alert_record`、`circuit_breaker`、`operation_log`、`data_lineage`、`quality_rule`、`quality_score` |

---

## 📡 核心 API

管理端统一使用 `/api/v1/**`，外部调用统一使用 `/openapi/v1/**`。当前关键入口：

| 范围 | 入口 |
|---|---|
| 厂商、数据类型与配置 | `/api/v1/vendor/**`、`/api/v1/datatype/**`、`/api/v1/config/**` |
| 连接器插件与产品配置 | `/api/v1/connector-plugin/**`、`/api/v1/vendor/config/{id}/connector-spec/**` |
| Legacy 迁移清点 | `GET /api/v1/vendor/config/connector-spec/inventory` |
| Legacy 高级流水线兼容 | `/api/v1/vendor/config/{id}/connector/**`（SIMPLE 只读校验；变更接口拒绝） |
| 已完成迁移历史（只读） | `GET /api/v1/vendor/connector-migration` |
| 接口契约 | `GET/PUT /api/v1/interface/{id}/contract` |
| 调用方与 API Key | `/api/v1/caller/**`、`/api/v1/caller/apikey/**` |
| 接口权限申请与审批 | `/api/v1/api-permission/applications/**`、`/api/v1/api-permission/tasks/**`、`/api/v1/api-permission/grants/**` |
| 调用记录 | `/api/v1/call-record/**` |
| 版本化计费 | `/api/v1/billing/plan/**`、`/api/v1/billing/event/**` |
| 身份与租户 | `/api/v1/auth/**`、`/api/v1/user/**`、`/api/v1/role/**`、`/api/v1/tenant/**` |
| 治理 | `/api/v1/alert/**`、`/api/v1/log/**`、`/api/v1/quality/**`、`/api/v1/trace/**` |
| 外部单条/批量调用 | `POST /openapi/v1/query`、`POST /openapi/v1/batch-query` |

完整方法、路径和错误语义见 [HTTP API 文档](docs/API.md)，审批领域设计见 [接口调用权限审批功能设计方案](docs/2026-07-23-api-permission-approval-design.md)。旧 `/interface/**/schema`、`/params`、访问域 `/data/**` 和重复 API Key 路由已删除。

---

## 🚀 快速开始

### 前置要求

- Java 21+
- Node.js 20.19+、22.13+ 或 24+
- Maven 3.9+
- Docker (用于基础设施)
- PostgreSQL 客户端（`psql`、`pg_dump`，用于迁移基线与备份恢复）

### 1. 启动本地基础设施

```bash
# 使用本机 PostgreSQL 时只启动其余必要中间件
docker compose up -d redis kafka nacos
```

> `docker-compose.yml` 仅用于本地开发/测试，不作为生产部署模板。

服务端口：
- PostgreSQL: 5432（本机服务）
- Redis: 6379
- Nacos: 8848

如需改用 Compose PostgreSQL，可执行 `POSTGRES_PORT=15432 docker compose up -d postgres`，并在发布 Nacos 开发配置前调整 `nacos-config/dev/data-platform-database-dev.properties`。

### 2. 发布 Nacos 配置

```bash
./publish-nacos-config.sh dev
```

脚本会幂等创建 `dev` namespace，发布数据库和六个服务的 Data ID，并在被 Git 忽略的 `.runtime/` 中生成开发环境 RSA/字段加密密钥。应用本地只保留应用名、Profile 和 Nacos 连接信息；DataSource、Redis、Kafka、路由及业务参数均从 Nacos 加载。

### 3. 初始化数据库

```bash
# 先预演待执行 SQL，再由 Liquibase 应用迁移
DB_PASSWORD=123456 ./migrate-db.sh dry-run
DB_PASSWORD=123456 ./migrate-db.sh update
```

Liquibase 使用数据库中的 `DATABASECHANGELOG`/`DATABASECHANGELOGLOCK` 记录版本、执行顺序和校验和。同一变更不会重复执行，迁移失败会返回非零状态；`start-services.sh` 也会在启动任何 Java 服务前自动执行 `update`，失败时终止启动。仅在明确需要跳过时设置 `MIGRATE_DB=false`。

常用数据库迁移命令：

```bash
./migrate-db.sh status                  # 查看已执行/待执行变更
./migrate-db.sh validate                # 校验变更日志与历史校验和
./migrate-db.sh dry-run                 # 预演升级 SQL
./migrate-db.sh backup                  # 生成 .runtime/db-backups/*.sql
./migrate-db.sh rollback-dry-run 1      # 预演最近一个变更的回滚 SQL

# 破坏性操作必须用目标数据库名显式确认
MIGRATION_CONFIRM_ROLLBACK=dataplatform ./migrate-db.sh rollback-count 1
MIGRATION_CONFIRM_RESTORE=dataplatform ./migrate-db.sh restore path/to/backup.sql
```

当前历史 SQL 被固化为 `baseline-2026-07-22` 单一基线，根变更日志最新为 V050。V042—V048 依次
落地插件控制面、PLUGIN-only、完整性冻结和显式主备路由；V049 增加 Manifest v2、SIMPLE Spec 和
编译事实；V050 以不可覆盖的静态事实种入 `generic-http:2.0.0`。后续变更必须追加独立 Liquibase
changeset；U049/U050 仅在没有 SIMPLE/v2/Generic 引用时允许，其他情况 HALT 并使用前向恢复或备份。
完整规则见 [数据库迁移说明](sql/MIGRATIONS.md)。

已有数据库如果过去通过手工 SQL 初始化，不要直接运行 `update`。`baseline` 会先自动备份，再在单个事务中幂等补齐历史迁移；只有结构校验通过后才登记基线：

```bash
MIGRATION_CONFIRM_BASELINE=dataplatform ./migrate-db.sh baseline
./migrate-db.sh status
```

通过 `baseline` 接管的旧库不会允许回滚删除初始基线；需要恢复时使用命令输出的迁移前备份执行 `restore`。

可在一次性数据库中验证 dry-run、首次迁移、重复执行、回滚和重新应用的完整链路（脚本只允许删除名称匹配 `dataplatform_*_regression` 的数据库）：

```bash
PGPASSWORD=postgres DB_PORT=15432 bash verify-db-bootstrap.sh
```

### 4. 编译后端

```bash
mvn clean install -DskipTests
```

### 5. 启动微服务

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

### 6. 启动前端

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

### Nacos 启动配置

```bash
NACOS_SERVER_ADDR=localhost:8848
NACOS_NAMESPACE=dev
NACOS_GROUP=DEFAULT_GROUP
```

开发环境业务配置位于 `nacos-config/dev/`；生产模板位于 `nacos-config/prod/`。生产密码和密钥仍由部署环境注入，发布方式见 `nacos-config/README.md`。`start-services.sh` 默认在启动前执行 Nacos 配置同步；仅在明确由外部发布系统管理配置时设置 `NACOS_CONFIG_SYNC=false`。

---

## 📁 目录结构

```
data-platform/
├── sql/
│   ├── changelog/                  # Liquibase 根变更日志
│   ├── init.sql                    # 历史基线输入，不单独执行
│   ├── migrations/                 # 变更 SQL
│   └── rollbacks/                  # 显式回滚 SQL
├── pom.xml                         # 父 POM
├── docker-compose.yml              # 基础设施
├── data-platform-common-contract/   # 通用契约
├── data-platform-common-web/        # Web公共能力
├── data-platform-common-persistence/# 持久化公共能力
├── data-platform-common-runtime/    # 运行时公共能力
├── data-platform-plugin-spi/        # 连接器插件轻量 SPI
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
| 深度清理、契约收敛与知识库刷新 | ✅ 100% | 2026-07-23 |
| 外部请求连接器插件化阶段 0—5 | ✅ 已实现并通过双 Access 隔离 OpenAPI/浏览器验收（未声称生产部署） | 2026-08-10 |
| 粗粒度连接器产品模型阶段 0—4 | ✅ 代码与隔离自动化验收完成；逐厂商生产迁移和旧入口退役未完成 | 2026-08-20 |

---

## 📝 文档入口

- [架构知识库](CODE_WIKI.md)
- [API 文档](docs/API.md)
- [部署文档](docs/DEPLOYMENT.md)
- [2026-07-23 深度清理审查](docs/2026-07-23-deep-cleanup-review.md)
- [外部请求连接器插件化升级设计（已实现并通过隔离验收）](docs/2026-08-03-external-request-connector-plugin-upgrade-design.md)
- [连接器粗粒度插件与配置简化设计（阶段 0—4 已实现，阶段 5—6 待生产门禁）](docs/2026-08-12-connector-product-model-simplification-design.md)
- [当前任务清单](PENDING_TASKS.md)

---

## ✅ 合并前检查

PR 合入 `dev` 前必须全部通过：

```bash
# 1. 后端全量验证
mvn verify

# 2. 前端依赖、安全、规范与生产构建
cd data-platform-web
npm audit
npm run lint
npm test
npm run build
cd ..

# 3. 数据库变更校验与预演
./migrate-db.sh validate
./migrate-db.sh dry-run
./verify-v049-connector-product-spec.sh
./verify-v050-generic-http.sh

# 4. 架构边界扫描
bash arch-scan.sh
```

任一步骤失败不可合入。

---

## 📄 许可证

MIT
