# 架构评估报告

**评估时间**: 2026-04-26
**评估范围**: 数据管理平台整体架构
**评估目的**: 为MVP核心功能实现做准备

---

## 一、模块结构与依赖评估

### 1.1 模块划分

项目共19个模块：

| 模块 | 职责 | 状态 |
|------|------|------|
| data-platform-gateway | API网关 | ✅ 基础路由已实现 |
| data-platform-common | 公共组件 | ✅ 基础工具类已实现 |
| data-platform-config | 配置中心 | ⚠️ 功能简单 |
| data-platform-vendor | 厂商管理 | ⚠️ CRUD已实现，核心适配器缺失 |
| data-platform-caller | 调用方管理 | ✅ API Key管理已实现 |
| data-platform-billing | 计费服务 | ⚠️ 基础CRUD，计费逻辑缺失 |
| data-platform-call | 调用服务 | ❌ 核心功能模拟实现 |
| data-platform-user | 用户管理 | ✅ 已实现 |
| data-platform-role | 角色权限 | ✅ 已实现 |
| data-platform-tenant | 租户管理 | ✅ 已实现 |
| data-platform-datatype | 数据类型 | ✅ 已实现 |
| data-platform-monitor | 监控告警 | ⚠️ 基础功能 |
| data-platform-trace | 链路追踪 | ⚠️ 基础功能 |
| data-platform-quality | 数据质量 | ⚠️ 基础功能 |
| data-platform-security | 安全服务 | ⚠️ 基础功能 |
| data-platform-log | 日志服务 | ✅ 已实现 |
| data-platform-graylog | 灰度发布 | ⚠️ 基础功能 |
| data-platform-sdk | SDK服务 | ⚠️ 基础功能 |

### 1.2 技术栈评估

| 技术 | 版本 | 评估 |
|------|------|------|
| Java | 21 LTS | ✅ 支持虚拟线程 |
| Spring Boot | 3.4.0 | ✅ 最新稳定版 |
| Spring Cloud | 2024.0.0 | ✅ 最新版本 |
| MyBatis-Plus | 3.5.7 | ✅ 主流ORM |
| PostgreSQL | 16 | ✅ 企业级数据库 |
| Redis | 7.x | ✅ 高性能缓存 |
| Kafka | 3.7.0 | ✅ 高吞吐消息队列 |
| Nacos | 2.3.2 | ⚠️ 已引入但未启用 |

### 1.3 模块依赖问题

**问题1：核心服务依赖缺失**

```
data-platform-call 当前依赖：
├── data-platform-common ✅
└── data-platform-caller ✅

缺失依赖：
├── data-platform-vendor ❌ (无法获取厂商配置)
└── data-platform-billing ❌ (无法动态计费)
```

**问题2：实体类重复定义**

| 实体类 | 定义位置 | 问题 |
|--------|----------|------|
| CallRecord | common、billing、call | 三处重复定义 |
| DataType | common、vendor、billing | 三处重复定义 |

**问题3：服务间通信方式**

- 当前：通过共享数据库表
- 推荐：通过API或消息队列，实现服务解耦

---

## 二、数据层设计评估

### 2.1 表结构评估

**✅ 优点：**

1. 表设计符合业务需求
2. 调用记录使用分区表（按月分区）
3. 索引设计合理

**⚠️ 问题：**

| 问题 | 说明 | 影响 |
|------|------|------|
| vendor_config.data_type_id 字段语义不清 | 应关联 data_type 表，但查询时用 likeRight | 查询效率低，语义混乱 |
| 缺少 vendor_health_record 表 | 无法记录厂商健康状态历史 | 影响自动路由决策 |
| billing_rule 缺少动态计费字段 | 已添加 sla_threshold、compensation_rate | 需要实现计算逻辑 |

### 2.2 实体映射问题

**VendorConfig 实体与数据库不一致：**

```java
// 实体定义
private Long dataTypeId;  // 存储的是 Long 类型

// 数据库定义
data_type_id BIGINT,  -- 应该关联 data_type 表的 id

// 服务查询逻辑（错误）
.likeRight(VendorConfig::getDataTypeId, dataType)  // 用 like 查询数字类型？
```

**正确做法：**
1. vendor_config.data_type_id 关联 data_type.id
2. 查询时先根据 dataType code 查询 id，再用 id 精确匹配

### 2.3 分区表维护

**当前状态：**
- 已创建 2026-04、05、06 三个月分区
- 需要定期创建未来月份分区

**建议：**
- 添加定时任务自动创建分区
- 或使用 PostgreSQL 自动分区扩展（pg_partman）

---

## 三、核心服务实现评估

### 3.1 数据查询服务（data-platform-call）

**当前实现：**

```java
// DataQueryServiceImpl.java
private Map<String, Object> callVendorApi(...) {
    // TODO: 实际实现应该根据 vendorCode 获取厂商配置
    // 构建HTTP请求，调用厂商API，解析响应
    
    // 模拟响应
    return buildMockData(dataType, params);
}

// 计费价格硬编码
private static final Map<String, BigDecimal> UNIT_PRICES = Map.of(
    "company_info", new BigDecimal("0.30"),
    "person_phone", new BigDecimal("0.15"),
    "id_card_verify", new BigDecimal("0.20")
);
```

**核心功能缺失：**

| 功能 | 状态 | MVP必要性 |
|------|------|-----------|
| 厂商适配器 | ❌ 模拟实现 | **必须** |
| 参数映射转换 | ❌ 未实现 | **必须** |
| 响应格式转换 | ❌ 未实现 | **必须** |
| 签名认证 | ❌ 未实现 | **必须** |
| 动态计费 | ❌ 硬编码 | **必须** |
| 熔断机制 | ❌ 无实现 | **必须** |
| 重试机制 | ❌ 无实现 | **必须** |
| 多厂商路由 | ❌ 无实现 | P1 |
| 缓存策略 | ⚠️ 简单实现 | 已有基础 |

### 3.2 计费服务（data-platform-billing）

**当前状态：**

| 功能 | 状态 | 说明 |
|------|------|------|
| 计费规则CRUD | ✅ | 基础增删改查 |
| 计费计算 | ❌ | 未实现 |
| 阶梯计费 | ❌ | 未实现 |
| 动态计费(SLA) | ❌ | 未实现 |
| 对账功能 | ⚠️ | 接口存在，逻辑简单 |

### 3.3 厂商管理服务（data-platform-vendor）

**当前状态：**

| 功能 | 状态 | 说明 |
|------|------|------|
| 厂商信息CRUD | ✅ | 已实现 |
| 厂商配置CRUD | ✅ | 已实现 |
| 参数映射管理 | ⚠️ | 实体存在，服务简单 |
| 厂商健康检查 | ⚠️ | 接口存在，未实现定时检查 |

---

## 四、基础设施评估

### 4.1 API Gateway

**当前配置：**

```yaml
spring.cloud.gateway:
  routes: # 16个服务路由已配置
  globalcors: # CORS已配置
```

**缺失功能：**

| 功能 | 状态 | 影响 |
|------|------|------|
| 限流Filter | ❌ | 无法在网关层限流 |
| 认证Filter | ❌ | 认证分散在各服务 |
| 熔断Filter | ❌ | 无熔断保护 |
| 请求日志 | ❌ | 无法追踪问题 |
| 请求ID传递 | ❌ | 链路追踪不完整 |

### 4.2 中间件使用

**Redis：**
- ✅ 已配置使用
- ✅ 用于缓存和限流
- ⚠️ 未使用 Redisson 分布式锁

**Kafka：**
- ❌ 已配置但未使用
- ❌ 调用记录同步写入数据库
- **影响**：高峰期数据库压力大

**Nacos：**
- ❌ 已禁用 (discovery.enabled: false)
- ❌ 无法实现配置热更新
- **影响**：修改配置需要重启服务

### 4.3 监控告警

**当前状态：**
- Prometheus/Grafana 已配置
- ELK Stack 已配置
- 但服务未暴露 metrics 端点

---

## 五、风险评估矩阵

### 5.1 高风险（阻塞MVP）

| 风险 | 描述 | 影响 | 解决方案 |
|------|------|------|----------|
| 🔴 厂商适配器缺失 | 无法真正调用外部API | 核心功能不可用 | 实现适配器模式 |
| 🔴 计费硬编码 | 价格写死在代码中 | 无法动态调整 | 从数据库读取 |
| 🔴 模块依赖缺失 | call服务无法访问厂商配置 | 架构缺陷 | 添加Maven依赖 |

### 5.2 中风险（影响稳定性）

| 风险 | 描述 | 影响 | 解决方案 |
|------|------|------|----------|
| 🟡 熔断机制缺失 | 厂商故障时无保护 | 级联故障 | 集成Resilience4j |
| 🟡 重试机制缺失 | 网络抖动导致失败 | 成功率降低 | 实现重试策略 |
| 🟡 Gateway无防护 | 无统一限流认证 | 安全风险 | 添加Filter |

### 5.3 低风险（可后续优化）

| 风险 | 描述 | 影响 | 解决方案 |
|------|------|------|----------|
| 🟢 实体类重复 | 维护困难 | 代码冗余 | 统一到common |
| 🟢 Nacos未启用 | 配置需重启 | 运维效率 | 启用Nacos |
| 🟢 Kafka未使用 | 同步写数据库 | 性能瓶颈 | 异步化改造 |

---

## 六、改进路线图

### 阶段一：修复架构缺陷（1-2天）

**目标**：建立正确的模块依赖关系

**任务清单**：
1. 添加模块依赖
2. 统一实体类定义
3. 修复数据库映射问题

### 阶段二：实现核心功能（3-5天）

**目标**：实现MVP必需功能

**任务清单**：
1. 实现厂商适配器
2. 实现动态计费
3. 实现熔断重试机制
4. 实现参数映射引擎

### 阶段三：增强基础设施（2-3天）

**目标**：提升系统稳定性和可观测性

**任务清单**：
1. Gateway增强（限流、认证、熔断）
2. 启用Kafka异步处理
3. 启用Nacos配置中心
4. 完善监控指标

---

## 七、总结

### 7.1 当前状态

- **测试通过率**：99%（208/210）
- **基础框架**：✅ 已搭建完成
- **核心功能**：❌ 未实现（模拟实现）
- **MVP就绪度**：30%

### 7.2 主要问题

1. **架构层面**：模块依赖不完整，实体类重复
2. **功能层面**：核心的厂商适配器、动态计费未实现
3. **稳定性层面**：熔断、重试机制缺失
4. **运维层面**：配置中心、消息队列未启用

### 7.3 建议

优先解决高风险问题，确保MVP核心功能可用。再逐步完善基础设施和优化代码质量。

---

**评估人**: Claude
**文档版本**: v1.0
**最后更新**: 2026-04-26
