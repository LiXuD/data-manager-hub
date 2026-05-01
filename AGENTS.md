# 项目开发进度追踪

## 项目概述

- **项目名称**: 数据管理平台 (Data Management Platform)
- **项目路径**: `https://github.com/LiXuD/data-manager-hub.git`
- **技术栈**: Java 21 + Spring Boot 3.4 + Spring Cloud 2024.0.0 + MyBatis-Plus 3.5.7

---

## 开发进度总览

| 阶段 | 状态 | 完成时间 |
|------|------|----------|
| 阶段1: 基础架构 | ✅ 100% | 2026-04-19 |
| 阶段2: 核心业务 | ✅ 100% | 2026-04-19 |
| 阶段3: 监控告警 | ✅ 100% | 2026-04-19 |
| 阶段4: 前端完善 | ✅ 100% | 2026-04-20 |
| 阶段5: 配置与日志 | ✅ 100% | 2026-04-21 |
| 阶段6: 模块合并优化 | ✅ 100% | 2026-04-30 |

---

## 模块合并记录 (2026-04-30)

### 合并完成的模块

| 原模块 | 合并到 | 说明 |
|--------|--------|------|
| data-platform-datatype | data-platform-vendor | 数据类型功能合并到厂商管理 |
| data-platform-config | data-platform-vendor | 配置中心功能合并到厂商管理 |
| data-platform-user | data-platform-iam | 用户管理合并到IAM |
| data-platform-role | data-platform-iam | 角色管理合并到IAM |

### 新增功能

1. **操作日志注解功能**
   - 新增 `@OperationLog` 注解，支持声明式操作日志记录
   - 自动记录操作人、IP、参数、返回结果、耗时等信息
   - 所有Controller写操作已添加注解

2. **CircuitBreaker自动配置**
   - 新增熔断器自动配置，支持按需加载

3. **测试模块重构** (2026-05-01)
   - 合并 UserApiTest + RoleApiTest → IAMApiTest
   - 合并 DataTypeApiTest + ConfigApiTest → VendorApiTest
   - BaseTest 新增通用测试辅助方法

4. **服务认证配置** (2026-05-01)
   - 所有需要认证的服务统一配置 Sa-Token
   - IAM 服务新增 Nacos 服务发现
   - Gateway 路由支持 /data-type 和 /datatype 双路径

5. **前端假提交修复** (2026-05-01)
   - 修复 role/user/datatype/caller/monitor/graylog 页面假提交问题
   - 所有增删改操作现在真实调用后端 API
   - 新增 `updateCallerStatus` API

6. **跨服务操作日志** (2026-05-01)
   - IAM 服务通过 Feign 调用 Log 服务保存操作日志
   - 新增 LogClient 接口和 InternalLogController 内部 API
   - 新增 RemoteOperationLogService 实现跨服务日志保存
   - 新增 IpUtil 工具类（提取公共 IP 获取逻辑）
   - OperationLogAspect 添加 8KB 日志大小限制

---

## 后端模块端口

| 服务 | 端口 | 说明 |
|------|------|------|
| Gateway | 8888 | API网关 |
| vendor | 8081 | 厂商管理（含配置中心、数据类型） |
| caller | 8082 | API管理 |
| call | 8083 | 调用记录 |
| billing | 8084 | 计费管理 |
| monitor | 8085 | 监控告警 |
| tenant | 8086 | 多租户 |
| sdk | 8087 | SDK生成 |
| log | 8090 | 操作日志 |
| graylog | 8092 | 灰度发布 |
| iam | 8093 | 用户权限管理（含用户、角色） |
| security | 8094 | 数据安全 |
| trace | 8095 | 数据血缘 |
| quality | 8096 | 数据质量 |
| interface | 8097 | 接口管理 |

---

## 模块结构

```
data-manager-hub/
├── pom.xml                              # 父POM
├── docker-compose.yml                   # 基础设施 (Redis)
├── AGENTS.md                            # 项目文档
├── data-platform-api/                   # 公共API契约
├── data-platform-common/                # 公共模块
│   ├── result/                          # 统一响应封装
│   ├── exception/                       # 异常处理
│   ├── log/                             # 操作日志注解
│   └── circuitbreaker/                  # 熔断器配置
├── data-platform-gateway/               # 网关 (8888)
├── data-platform-vendor/                # 厂商管理 (8081)
│   ├── data-platform-vendor-api/
│   └── data-platform-vendor-service/
├── data-platform-caller/                # 调用方管理 (8082)
├── data-platform-call/                  # 调用服务 (8083)
├── data-platform-billing/               # 计费服务 (8084)
├── data-platform-monitor/               # 监控告警 (8085)
├── data-platform-tenant/                # 租户管理 (8086)
├── data-platform-sdk/                   # SDK生成 (8087)
├── data-platform-log/                   # 操作日志 (8090)
├── data-platform-graylog/               # 灰度发布 (8092)
├── data-platform-iam/                   # 用户权限管理 (8093)
│   ├── data-platform-iam-api/
│   └── data-platform-iam-service/
├── data-platform-security/              # 数据安全 (8094)
├── data-platform-trace/                 # 数据血缘 (8095)
├── data-platform-quality/               # 数据质量 (8096)
├── data-platform-interface/             # 接口管理 (8097)
└── data-platform-web/                   # 前端 (3000)
```

---

## 页面路由

| 路径 | 页面 | 后端模块 |
|------|------|----------|
| /vendor | 厂商管理 | vendor |
| /caller | 调用方管理 | caller |
| /call | 调用记录 | call |
| /billing | 计费管理 | billing |
| /monitor | 监控告警 | monitor |
| /tenant | 租户管理 | tenant |
| /user | 用户管理 | iam |
| /role | 角色管理 | iam |
| /audit | 操作日志 | log |
| /config | 配置中心 | vendor |
| /graylog | 灰度发布 | graylog |

---

## 数据库表

| 序号 | 表名 | 说明 |
|------|------|------|
| 1 | tenant_info | 租户信息 |
| 2 | vendor_info | 厂商信息 |
| 3 | data_type | 数据类型 |
| 4 | vendor_config | 厂商API配置 |
| 5 | vendor_config_extended | 厂商扩展配置 |
| 6 | config_version | 配置版本历史 |
| 7 | caller_info | 调用方信息 |
| 8 | api_key | API Key |
| 9 | call_record | 调用记录 (按月分区) |
| 10 | billing_daily | 日账单 |
| 11 | billing_rule | 计费规则 |
| 12 | user_info | 用户 |
| 13 | role_info | 角色 |
| 14 | user_role | 用户角色关联 |
| 15 | alert_rule | 告警规则 |
| 16 | alert_record | 告警记录 |
| 17 | circuit_breaker | 熔断记录 |
| 18 | operation_log | 操作日志 |
| 19 | gray_rule | 灰度规则 |

---

## 技术栈

- **后端**: Java 21 + Spring Boot 3.4.0 + Spring Cloud 2024.0.0 + MyBatis-Plus 3.5.7
- **数据库**: PostgreSQL 16 (localhost:5432)
- **缓存**: Redis 7 (Redisson 3.27.0)
- **服务发现**: Nacos 2023.0.1.0 (本地配置模式)
- **认证**: Sa-Token 1.37.0
- **前端**: Vue3 + TypeScript + Element Plus + Vite + Pinia

---

## Git 提交规范

### commit message格式

```
<type>(<scope>): <summary>

<正文：描述本次变更的背景与动机>

Agent-Task: <原始任务描述或任务 ID>
Agent-Model: <使用的模型，如 gpt-4o、gemini-2.5-pro>
Agent-Decision: <关键设计决策及理由>
Agent-Limitation: <已知局限或后续 TODO>
```

### 提交类型

- `feat`: 新功能
- `fix`: 修复bug
- `refactor`: 重构
- `docs`: 文档更新
- `style`: 代码格式
- `test`: 测试相关
- `chore`: 构建/工具相关

### 提交节点

在完成以下关键节点时执行 git commit：
- 完成数据模型/接口定义
- 完成核心逻辑实现
- 完成测试编写
- 完成文档更新
