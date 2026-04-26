# 接口管理与数据查询测试设计文档

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增接口管理层级，实现接口级别的API配置管理，并提供数据查询测试功能

**Architecture:** 四层层级结构：厂商(Vendor) → 数据类型(DataType) → 接口(ApiInterface) → API配置(VendorConfig)。前端新增两个独立页面：接口管理和数据查询测试。

**Tech Stack:** Vue 3 + TypeScript + Element Plus, 后端Spring Boot + MyBatis-Plus

---

## 1. 背景与问题

### 当前问题
- API配置直接关联到数据类型层级，无法精确到具体接口
- 一个数据类型（如企业工商）下有10+个接口，无法区分调用哪个
- 数据查询测试无法指定具体接口

### 解决方案
新增"接口(ApiInterface)"层级，实现：
- 厂商 → 数据类型 → 接口 → API配置 的四层结构
- 接口级别的API配置管理
- 测试页面可选择具体接口进行查询

---

## 2. 数据模型设计

### 2.1 新增实体：ApiInterface

```java
@TableName("api_interface")
public class ApiInterface {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String interfaceCode;      // 接口编码，如 COMPANY_BASE
    private String interfaceName;      // 接口名称，如 企业基本信息查询
    private Long dataTypeId;           // 关联数据类型ID
    private String path;               // 接口路径，如 /company/base
    private String description;        // 接口描述
    private String requestSchema;      // 请求参数Schema (JSON)
    private String responseSchema;     // 响应数据Schema (JSON)
    private Integer sort;              // 排序
    private String status;             // 状态：active/inactive
    
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Boolean deleted;
}
```

### 2.2 修改实体：VendorConfig

将原有字段 `dataTypeId` 改为 `interfaceId`：

```java
@TableName("vendor_config")
public class VendorConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long vendorId;             // 厂商ID
    private Long interfaceId;          // 接口ID (原dataTypeId改为interfaceId)
    private String apiUrl;             // API地址
    private String method;             // 请求方法 GET/POST
    private Integer timeout;           // 超时时间(ms)
    private Integer retryCount;        // 重试次数
    private Integer circuitThreshold;  // 熔断阈值
    private Integer circuitTimeout;    // 熔断超时(s)
    private String signType;           // 签名类型
    private String encryptType;        // 加密类型
    private String headerConfig;       // 请求头配置(JSON)
    private String requestTemplate;    // 请求参数模板(JSON)
    private String responseMapping;    // 响应映射(JSON)
    private Long fallbackInterfaceId;  // 降级接口ID (原fallbackVendorId改为接口级别)
    private String status;             // 状态
    
    // 审计字段...
}
```

### 2.3 数据库迁移脚本

```sql
-- V002__create_api_interface.sql

-- 1. 创建接口表
CREATE TABLE `api_interface` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `interface_code` VARCHAR(64) NOT NULL COMMENT '接口编码',
    `interface_name` VARCHAR(128) NOT NULL COMMENT '接口名称',
    `data_type_id` BIGINT NOT NULL COMMENT '数据类型ID',
    `path` VARCHAR(256) COMMENT '接口路径',
    `description` VARCHAR(512) COMMENT '接口描述',
    `request_schema` TEXT COMMENT '请求参数Schema',
    `response_schema` TEXT COMMENT '响应数据Schema',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `status` VARCHAR(32) DEFAULT 'active' COMMENT '状态',
    `created_by` BIGINT,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_by` BIGINT,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT(1) DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_interface_code` (`interface_code`),
    KEY `idx_data_type_id` (`data_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口定义表';

-- 2. 修改vendor_config表，添加interface_id字段
ALTER TABLE `vendor_config` 
ADD COLUMN `interface_id` BIGINT COMMENT '接口ID' AFTER `vendor_id`;

-- 3. 迁移数据：将现有dataTypeId映射到interfaceId（需要根据实际情况调整）
-- UPDATE vendor_config SET interface_id = ...

-- 4. 添加外键索引
ALTER TABLE `vendor_config` ADD INDEX `idx_interface_id` (`interface_id`);
```

---

## 3. 后端API设计

### 3.1 接口管理API

```
GET    /api/interface/list          # 分页查询接口列表
GET    /api/interface/{id}          # 获取接口详情
POST   /api/interface               # 创建接口
PUT    /api/interface/{id}          # 更新接口
DELETE /api/interface/{id}          # 删除接口
GET    /api/interface/by-data-type/{dataTypeId}  # 按数据类型查询接口
```

### 3.2 API配置API（调整）

```
GET    /api/vendor-config/list      # 分页查询（支持vendorId/interfaceId筛选）
GET    /api/vendor-config/{id}      # 获取配置详情
GET    /api/vendor-config/by-interface/{interfaceId}  # 按接口获取配置
POST   /api/vendor-config           # 创建配置
PUT    /api/vendor-config/{id}      # 更新配置
DELETE /api/vendor-config/{id}      # 删除配置
```

### 3.3 数据查询API（调整）

```
POST   /data/query                 # 数据查询（参数调整）
{
    "vendorCode": "TIANYANCHA",
    "dataTypeCode": "COMPANY",      # 数据类型编码
    "interfaceCode": "COMPANY_BASE", # 新增：接口编码
    "params": {"companyName": "阿里巴巴"}
}

POST   /data/batch-query           # 批量查询（参数调整）
```

---

## 4. 前端页面设计

### 4.1 页面一：接口管理 (Interface Management)

**路由:** `/interface`
**文件:** `src/views/interface/index.vue`

**页面布局:**
```
┌─────────────────────────────────────────────────────────────┐
│ 接口管理                                    [新增接口]       │
├─────────────────────────────────────────────────────────────┤
│ [厂商 ▼] [数据类型 ▼] [状态 ▼] [搜索] [重置]                 │
├─────────────────────────────────────────────────────────────┤
│ 接口编码 │ 接口名称 │ 厂商 │ 数据类型 │ 路径 │ API配置 │ 状态 │ 操作 │
│ COMPANY_BASE │ 企业基本信息 │ 天眼查 │ 企业工商 │ /company/base │ ✓已配置 │ 启用 │ 配置 编辑 删除 │
│ COMPANY_SHAREHOLDER │ 股东信息 │ 天眼查 │ 企业工商 │ /company/shareholder │ ✗未配置 │ 启用 │ 配置 编辑 删除 │
└─────────────────────────────────────────────────────────────┘
```

**功能说明:**
1. 列表展示所有接口，支持按厂商、数据类型、状态筛选
2. "API配置"列显示配置状态：已配置(绿色)/未配置(橙色)
3. 点击"配置"按钮打开API配置弹窗
4. 支持新增、编辑、删除接口

**接口表单字段:**
- 接口编码 (必填)
- 接口名称 (必填)
- 数据类型 (下拉选择)
- 接口路径
- 描述
- 请求参数Schema (JSON编辑器)
- 响应Schema (JSON编辑器)
- 排序
- 状态

### 4.2 页面二：API配置弹窗

**文件:** `src/views/interface/components/ApiConfigForm.vue`

**表单字段分组:**

**基础配置:**
- 接口 (只读，从父页面传入)
- API地址 (必填)
- 请求方法 (GET/POST)
- 超时时间(ms)

**重试与熔断:**
- 重试次数
- 熔断阈值
- 熔断超时(s)
- 降级接口 (下拉选择)

**安全配置:**
- 签名类型
- 加密类型
- 请求头配置 (JSON编辑器)

**数据转换:**
- 请求参数模板 (JSON编辑器)
- 响应映射 (JSON编辑器)

**状态:**
- 启用/禁用

### 4.3 页面三：数据查询测试 (Data Query Test)

**路由:** `/data-test`
**文件:** `src/views/data-test/index.vue`

**页面布局 (上下结构):**
```
┌─────────────────────────────────────────────────────────────┐
│ 数据查询测试                                                  │
├─────────────────────────────────────────────────────────────┤
│ 查询参数                                                      │
│ ┌─────────────┬─────────────┬─────────────────┐             │
│ │ 厂商        │ 数据类型     │ 接口            │             │
│ │ 天眼查 ▼    │ 企业工商 ▼   │ 企业基本信息 ▼   │             │
│ └─────────────┴─────────────┴─────────────────┘             │
│ 请求参数 (JSON)                                               │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ {"companyName": "阿里巴巴"}                              │ │
│ └─────────────────────────────────────────────────────────┘ │
│ [执行查询] [清空]                                             │
├─────────────────────────────────────────────────────────────┤
│ 查询结果                           接口: 企业基本信息 | 156ms │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ {                                                       │ │
│ │   "success": true,                                      │ │
│ │   "data": { "companyName": "阿里巴巴集团", ... }         │ │
│ │ }                                                       │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

**功能说明:**
1. 三级联动下拉：厂商 → 数据类型 → 接口
2. JSON编辑器输入请求参数
3. 执行查询按钮调用 `/data/query` API
4. 结果区域显示JSON响应（语法高亮）
5. 显示耗时信息

---

## 5. 前端类型定义

### 5.1 新增类型 (src/types/index.ts)

```typescript
// 接口定义
export interface ApiInterface {
  id: number
  interfaceCode: string
  interfaceName: string
  dataTypeId: number
  dataTypeName?: string
  vendorId?: number
  vendorName?: string
  path: string
  description?: string
  requestSchema?: string
  responseSchema?: string
  sort: number
  status: 'active' | 'inactive'
  hasConfig?: boolean  // 是否已配置API
  createdAt: string
  updatedAt: string
}

// API配置（调整）
export interface VendorConfig {
  id: number
  vendorId: number
  vendorName?: string
  interfaceId: number
  interfaceName?: string
  apiUrl: string
  method: 'GET' | 'POST'
  timeout: number
  retryCount?: number
  circuitThreshold?: number
  circuitTimeout?: number
  signType?: string
  encryptType?: string
  headerConfig?: string
  requestTemplate?: string
  responseMapping?: string
  fallbackInterfaceId?: number
  status: 'active' | 'inactive'
  createdAt: string
  updatedAt: string
}

// 数据查询请求（调整）
export interface DataQueryRequest {
  vendorCode: string
  dataTypeCode: string
  interfaceCode: string  // 新增
  params: Record<string, any>
}

// 数据查询响应
export interface DataQueryResponse {
  success: boolean
  data?: any
  errorCode?: string
  errorMsg?: string
  latency?: number
  cached?: boolean
}
```

---

## 6. 实现任务清单

### Task 1: 数据库迁移
- 创建 `api_interface` 表
- 修改 `vendor_config` 表添加 `interface_id` 字段
- 编写数据迁移脚本

### Task 2: 后端实体与Mapper
- 创建 `ApiInterface` 实体类
- 修改 `VendorConfig` 实体（dataTypeId → interfaceId）
- 创建 `ApiInterfaceMapper`
- 修改 `VendorConfigMapper`

### Task 3: 后端Service层
- 创建 `ApiInterfaceService` 接口和实现
- 修改 `VendorConfigService` 支持按interfaceId查询
- 修改 `VendorProxyService` 支持interfaceCode参数

### Task 4: 后端Controller层
- 创建 `ApiInterfaceController`
- 修改 `VendorConfigController`
- 修改 `DataQueryController` 支持interfaceCode

### Task 5: 前端类型定义
- 添加 `ApiInterface` 类型
- 修改 `VendorConfig` 类型
- 添加 `DataQueryRequest` 类型

### Task 6: 前端API封装
- 创建 `src/api/interface.ts`
- 修改 `src/api/vendor-config.ts`
- 创建 `src/api/data-query.ts`

### Task 7: 接口管理页面
- 创建 `src/views/interface/index.vue`
- 创建 `src/views/interface/components/InterfaceForm.vue`
- 创建 `src/views/interface/components/ApiConfigForm.vue`
- 添加路由配置

### Task 8: 数据查询测试页面
- 创建 `src/views/data-test/index.vue`
- 添加路由配置

### Task 9: 菜单配置
- 添加"接口管理"菜单项
- 添加"数据测试"菜单项

---

## 7. 验收标准

1. **接口管理**
   - 可新增、编辑、删除接口
   - 可按厂商、数据类型筛选
   - API配置状态正确显示
   - 点击"配置"可打开配置弹窗

2. **API配置**
   - 可为接口配置完整的API参数
   - 配置保存后状态正确更新

3. **数据查询测试**
   - 三级联动正常工作
   - 可执行查询并显示结果
   - 结果JSON语法高亮
   - 显示耗时信息

4. **数据一致性**
   - 接口与API配置正确关联
   - 查询时正确使用对应接口的配置
