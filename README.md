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
| Spring Cloud | 2023.0.x |
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
├── data-platform-api/           # 公共API契约模块
├── data-platform-common/        # 公共模块
├── data-platform-gateway/       # API网关 (端口 8888)
├── data-platform-vendor/        # 厂商管理服务 (端口 8081)
│   ├── data-platform-vendor-api/
│   └── data-platform-vendor-service/  # 含数据类型、配置中心功能
├── data-platform-caller/        # 调用方管理服务 (端口 8082)
├── data-platform-call/          # 调用记录服务 (端口 8083)
├── data-platform-billing/       # 计费管理服务 (端口 8084)
├── data-platform-monitor/       # 监控告警服务 (端口 8085)
├── data-platform-tenant/        # 租户管理服务 (端口 8086)
├── data-platform-sdk/           # SDK生成服务 (端口 8087)
├── data-platform-log/           # 操作日志服务 (端口 8090)
├── data-platform-graylog/       # 灰度发布服务 (端口 8092)
├── data-platform-iam/           # 用户权限管理服务 (端口 8093)
│   ├── data-platform-iam-api/
│   └── data-platform-iam-service/     # 含用户、角色管理功能
├── data-platform-security/      # 数据安全服务 (端口 8094)
├── data-platform-trace/         # 数据血缘服务 (端口 8095)
├── data-platform-quality/       # 数据质量服务 (端口 8096)
├── data-platform-interface/     # 接口管理服务 (端口 8097)
├── data-platform-test/          # 测试模块
└── data-platform-web/           # 前端 Vue3 项目
```

> **模块合并说明**: data-platform-datatype 和 data-platform-config 已合并到 vendor；data-platform-user 和 data-platform-role 已合并到 iam。

---

## 🗄️ 数据库表 (21张)

| 序号 | 表名 | 说明 |
|------|------|------|
| 1 | tenant_info | 租户信息 |
| 2 | vendor_info | 厂商信息 |
| 3 | data_type | 数据类型 |
| 4 | vendor_config | 厂商配置 |
| 5 | vendor_config_extended | 厂商扩展配置 |
| 6 | config_version | 配置版本历史 |
| 7 | caller_info | 调用方信息 |
| 8 | api_key | API Key |
| 9 | api_interface | 接口定义 |
| 10 | interface_param | 接口参数定义 |
| 11 | call_record | 调用记录 (按月分区) |
| 12 | billing_daily | 日账单 |
| 13 | billing_rule | 计费规则 |
| 14 | user_info | 用户 |
| 15 | role_info | 角色 |
| 16 | user_role | 用户角色关联 |
| 17 | alert_rule | 告警规则 |
| 18 | alert_record | 告警记录 |
| 19 | circuit_breaker | 熔断记录 |
| 20 | operation_log | 操作日志 |
| 21 | gray_rule | 灰度规则 |

---

## 📡 API 接口 (56个)

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

### 1. 启动基础设施

```bash
cd data-platform
docker-compose up -d
```

服务端口：
- PostgreSQL: 5432 (本地安装)
- Redis: 6379
- Nacos: 8848

### 2. 初始化数据库

```bash
# 执行 DDL 脚本
psql -h localhost -U postgres -d dataplatform -f sql/init.sql
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

# 厂商服务 (端口 8081)
cd data-platform-vendor
mvn spring-boot:run

# ... 其他服务类似
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
DB_USER=postgres
DB_PASSWORD=postgres

# Redis 配置
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=redis_password

# Nacos 配置
NACOS_SERVER_ADDR=localhost:8848
NACOS_NAMESPACE=dev
```

---

## 📁 目录结构

```
data-platform/
├── sql/
│   └── init.sql                    # DDL 脚本 (21表)
├── pom.xml                         # 父 POM
├── docker-compose.yml              # 基础设施
├── data-platform-common/           # 公共模块
│   └── src/main/java/...
├── data-platform-gateway/          # API 网关
│   └── src/main/java/...
├── data-platform-vendor/           # 厂商管理
│   └── src/main/java/...
├── data-platform-caller/           # 调用方管理
│   └── src/main/java/...
├── data-platform-call/             # 调用记录
│   └── src/main/java/...
├── data-platform-billing/          # 计费管理
│   └── src/main/java/...
├── data-platform-monitor/          # 监控告警
│   └── src/main/java/...
├── data-platform-tenant/           # 租户管理
│   └── src/main/java/...
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

---

## 📝 设计文档

详见: [设计文档](../2026-04-17-data-management-platform-design.md)

---

## 📄 许可证

MIT