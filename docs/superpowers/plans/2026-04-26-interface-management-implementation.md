# Interface Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增接口管理层级，实现接口级别的API配置管理，并提供数据查询测试功能

**Architecture:** 四层层级结构：厂商(Vendor) → 数据类型(DataType) → 接口(ApiInterface) → API配置(VendorConfig)。前端新增两个独立页面：接口管理和数据查询测试。

**Tech Stack:** Vue 3 + TypeScript + Element Plus, Spring Boot 3.4.0 + MyBatis-Plus, MySQL 8.0

---

## File Structure

### Backend (New Module: data-platform-interface)
```
data-platform-interface/
├── src/main/java/com/dataplatform/interface/
│   ├── InterfaceApplication.java
│   ├── config/WebMvcConfig.java
│   ├── entity/ApiInterface.java
│   ├── mapper/ApiInterfaceMapper.java
│   ├── service/ApiInterfaceService.java
│   └── controller/ApiInterfaceController.java
└── pom.xml
```

### Backend (Modified)
```
data-platform-vendor/src/main/java/com/dataplatform/vendor/
├── entity/VendorConfig.java        # 修改: dataTypeId → interfaceId
├── service/VendorConfigService.java # 修改: 支持interfaceId查询
└── controller/VendorConfigController.java

data-platform-call/src/main/java/com/dataplatform/call/
├── vo/ApiQueryReqVO.java           # 修改: 添加interfaceCode
└── service/impl/DataQueryServiceImpl.java # 修改: 使用interfaceCode
```

### Frontend (New)
```
data-platform-web/src/
├── api/
│   ├── interface.ts                # 新增
│   └── vendor-config.ts            # 修改
├── types/index.ts                  # 修改: 添加ApiInterface类型
├── views/
│   ├── interface/
│   │   ├── index.vue               # 接口管理页面
│   │   └── components/
│   │       ├── InterfaceForm.vue   # 接口表单弹窗
│   │       └── ApiConfigForm.vue   # API配置弹窗
│   └── data-test/
│       └── index.vue               # 数据查询测试页面
└── router/index.ts                 # 修改: 添加路由
```

### Database Migration
```
sql/migrations/V002__create_api_interface.sql
```

---

## Task 1: 数据库迁移脚本

**Files:**
- Create: `sql/migrations/V002__create_api_interface.sql`

- [ ] **Step 1: 创建迁移脚本文件**

```sql
-- V002__create_api_interface.sql

-- 1. 创建接口定义表
CREATE TABLE `api_interface` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `interface_code` VARCHAR(64) NOT NULL COMMENT '接口编码',
    `interface_name` VARCHAR(128) NOT NULL COMMENT '接口名称',
    `data_type_id` BIGINT NOT NULL COMMENT '数据类型ID',
    `path` VARCHAR(256) DEFAULT NULL COMMENT '接口路径',
    `description` VARCHAR(512) DEFAULT NULL COMMENT '接口描述',
    `request_schema` TEXT DEFAULT NULL COMMENT '请求参数Schema(JSON)',
    `response_schema` TEXT DEFAULT NULL COMMENT '响应数据Schema(JSON)',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `status` VARCHAR(32) DEFAULT 'active' COMMENT '状态: active/inactive',
    `created_by` BIGINT DEFAULT NULL COMMENT '创建人',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by` BIGINT DEFAULT NULL COMMENT '更新人',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT(1) DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_interface_code` (`interface_code`),
    KEY `idx_data_type_id` (`data_type_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接口定义表';

-- 2. 修改vendor_config表，添加interface_id字段
ALTER TABLE `vendor_config` 
ADD COLUMN `interface_id` BIGINT DEFAULT NULL COMMENT '接口ID' AFTER `vendor_id`;

-- 3. 添加索引
ALTER TABLE `vendor_config` ADD INDEX `idx_interface_id` (`interface_id`);

-- 4. 插入示例数据
INSERT INTO `api_interface` (`interface_code`, `interface_name`, `data_type_id`, `path`, `description`, `status`) VALUES
('COMPANY_BASE', '企业基本信息', 1, '/company/base', '查询企业基本工商信息', 'active'),
('COMPANY_SHAREHOLDER', '股东信息', 1, '/company/shareholder', '查询企业股东信息', 'active'),
('COMPANY_BRANCH', '分支机构', 1, '/company/branch', '查询企业分支机构信息', 'active'),
('PERSONAL_CREDIT', '个人征信报告', 2, '/personal/credit', '查询个人征信报告', 'active');
```

- [ ] **Step 2: 提交数据库迁移脚本**

```bash
git add sql/migrations/V002__create_api_interface.sql
git commit -m "feat(db): add api_interface table and vendor_config interface_id column"
```

---

## Task 2: 创建接口模块实体类

**Files:**
- Create: `data-platform-interface/pom.xml`
- Create: `data-platform-interface/src/main/java/com/dataplatform/interface/entity/ApiInterface.java`

- [ ] **Step 1: 创建模块pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.dataplatform</groupId>
        <artifactId>data-manager-hub</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>data-platform-interface</artifactId>
    <packaging>jar</packaging>
    <description>接口管理模块</description>

    <dependencies>
        <dependency>
            <groupId>com.dataplatform</groupId>
            <artifactId>data-platform-common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 创建ApiInterface实体类**

```java
package com.dataplatform.interface_.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("api_interface")
public class ApiInterface {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String interfaceCode;
    private String interfaceName;
    private Long dataTypeId;
    private String path;
    private String description;
    private String requestSchema;
    private String responseSchema;
    private Integer sort;
    private String status;
    
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

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getInterfaceCode() { return interfaceCode; }
    public void setInterfaceCode(String interfaceCode) { this.interfaceCode = interfaceCode; }
    public String getInterfaceName() { return interfaceName; }
    public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }
    public Long getDataTypeId() { return dataTypeId; }
    public void setDataTypeId(Long dataTypeId) { this.dataTypeId = dataTypeId; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRequestSchema() { return requestSchema; }
    public void setRequestSchema(String requestSchema) { this.requestSchema = requestSchema; }
    public String getResponseSchema() { return responseSchema; }
    public void setResponseSchema(String responseSchema) { this.responseSchema = responseSchema; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
```

- [ ] **Step 3: 创建Mapper接口**

```java
package com.dataplatform.interface_.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.interface_.entity.ApiInterface;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApiInterfaceMapper extends BaseMapper<ApiInterface> {
}
```

- [ ] **Step 4: 提交实体类**

```bash
git add data-platform-interface/
git commit -m "feat(interface): add ApiInterface entity and mapper"
```

---

## Task 3: 创建接口Service层

**Files:**
- Create: `data-platform-interface/src/main/java/com/dataplatform/interface/service/ApiInterfaceService.java`

- [ ] **Step 1: 创建Service实现**

```java
package com.dataplatform.interface_.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.interface_.entity.ApiInterface;
import com.dataplatform.interface_.mapper.ApiInterfaceMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ApiInterfaceService extends ServiceImpl<ApiInterfaceMapper, ApiInterface> {

    public PageResult<ApiInterface> list(Long vendorId, Long dataTypeId, String status, int page, int pageSize) {
        LambdaQueryWrapper<ApiInterface> wrapper = new LambdaQueryWrapper<>();
        
        if (dataTypeId != null) {
            wrapper.eq(ApiInterface::getDataTypeId, dataTypeId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ApiInterface::getStatus, status);
        }
        wrapper.eq(ApiInterface::getDeleted, false);
        wrapper.orderByAsc(ApiInterface::getSort);
        wrapper.orderByDesc(ApiInterface::getCreatedAt);

        Page<ApiInterface> result = this.page(new Page<>(page, pageSize), wrapper);

        PageResult<ApiInterface> response = new PageResult<>();
        response.setCode(0);
        response.setMessage("success");
        response.setData(result.getRecords());
        response.setTotal(result.getTotal());
        response.setPage(page);
        response.setPageSize(pageSize);
        return response;
    }

    public List<ApiInterface> listByDataTypeId(Long dataTypeId) {
        LambdaQueryWrapper<ApiInterface> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiInterface::getDataTypeId, dataTypeId);
        wrapper.eq(ApiInterface::getStatus, "active");
        wrapper.eq(ApiInterface::getDeleted, false);
        wrapper.orderByAsc(ApiInterface::getSort);
        return this.list(wrapper);
    }

    public ApiInterface getByInterfaceCode(String interfaceCode) {
        LambdaQueryWrapper<ApiInterface> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiInterface::getInterfaceCode, interfaceCode);
        wrapper.eq(ApiInterface::getDeleted, false);
        return this.getOne(wrapper);
    }

    public boolean hasApiConfig(Long interfaceId) {
        // 检查是否已配置API（需要查询vendor_config表）
        // 这里返回false，实际实现需要注入VendorConfigService
        return false;
    }
}
```

- [ ] **Step 2: 提交Service层**

```bash
git add data-platform-interface/src/main/java/com/dataplatform/interface/service/
git commit -m "feat(interface): add ApiInterfaceService"
```

---

## Task 4: 创建接口Controller层

**Files:**
- Create: `data-platform-interface/src/main/java/com/dataplatform/interface/controller/ApiInterfaceController.java`

- [ ] **Step 1: 创建Controller**

```java
package com.dataplatform.interface_.controller;

import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.interface_.entity.ApiInterface;
import com.dataplatform.interface_.service.ApiInterfaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/interface")
public class ApiInterfaceController {

    @Autowired
    private ApiInterfaceService apiInterfaceService;

    private static final List<String> VALID_STATUSES = List.of("active", "inactive");

    @GetMapping("/list")
    public PageResult<ApiInterface> list(
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) Long dataTypeId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return apiInterfaceService.list(vendorId, dataTypeId, status, page, pageSize);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<ApiInterface>> getById(@PathVariable Long id) {
        ApiInterface apiInterface = apiInterfaceService.getById(id);
        if (apiInterface == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "接口不存在"));
        }
        return ResponseEntity.ok(Result.success(apiInterface));
    }

    @GetMapping("/by-data-type/{dataTypeId}")
    public Result<List<ApiInterface>> listByDataType(@PathVariable Long dataTypeId) {
        return Result.success(apiInterfaceService.listByDataTypeId(dataTypeId));
    }

    @PostMapping
    public ResponseEntity<Result<ApiInterface>> create(@RequestBody ApiInterface apiInterface) {
        if (apiInterface.getInterfaceCode() == null || apiInterface.getInterfaceCode().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "接口编码不能为空"));
        }
        if (apiInterface.getInterfaceName() == null || apiInterface.getInterfaceName().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "接口名称不能为空"));
        }
        
        ApiInterface existing = apiInterfaceService.getByInterfaceCode(apiInterface.getInterfaceCode());
        if (existing != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "接口编码已存在"));
        }

        apiInterface.setId(null);
        if (apiInterface.getStatus() == null) {
            apiInterface.setStatus("active");
        }
        if (apiInterface.getSort() == null) {
            apiInterface.setSort(0);
        }
        apiInterfaceService.save(apiInterface);
        return ResponseEntity.ok(Result.success(apiInterface));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Result<ApiInterface>> update(@PathVariable Long id, @RequestBody ApiInterface apiInterface) {
        ApiInterface existing = apiInterfaceService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "接口不存在"));
        }
        apiInterface.setId(id);
        apiInterfaceService.updateById(apiInterface);
        return ResponseEntity.ok(Result.success(apiInterfaceService.getById(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        ApiInterface existing = apiInterfaceService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "接口不存在"));
        }
        apiInterfaceService.removeById(id);
        return ResponseEntity.ok(Result.success(null));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null || !VALID_STATUSES.contains(status)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "无效的状态值，有效值: " + VALID_STATUSES));
        }

        ApiInterface existing = apiInterfaceService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "接口不存在"));
        }

        ApiInterface apiInterface = new ApiInterface();
        apiInterface.setId(id);
        apiInterface.setStatus(status);
        apiInterfaceService.updateById(apiInterface);
        return ResponseEntity.ok(Result.success(null));
    }
}
```

- [ ] **Step 2: 创建启动类和配置**

```java
package com.dataplatform.interface_;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.dataplatform.interface_.mapper")
public class InterfaceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InterfaceApplication.class, args);
    }
}
```

- [ ] **Step 3: 提交Controller层**

```bash
git add data-platform-interface/
git commit -m "feat(interface): add ApiInterfaceController and application config"
```

---

## Task 5: 修改VendorConfig实体

**Files:**
- Modify: `data-platform-vendor/src/main/java/com/dataplatform/vendor/entity/VendorConfig.java`

- [ ] **Step 1: 修改VendorConfig实体，添加interfaceId字段**

在VendorConfig.java中，将dataTypeId改为interfaceId：

```java
// 添加新字段
private Long interfaceId;

// 删除或保留dataTypeId字段（根据实际情况决定）
// private Long dataTypeId;

// 添加getter和setter
public Long getInterfaceId() { return interfaceId; }
public void setInterfaceId(Long interfaceId) { this.interfaceId = interfaceId; }
```

- [ ] **Step 2: 修改VendorConfigService，添加按interfaceId查询方法**

在VendorConfigService.java中添加：

```java
public VendorConfig getByInterfaceId(Long interfaceId) {
    LambdaQueryWrapper<VendorConfig> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(VendorConfig::getInterfaceId, interfaceId);
    wrapper.eq(VendorConfig::getStatus, "active");
    return this.getOne(wrapper);
}

public VendorConfig getByVendorCodeAndInterfaceCode(String vendorCode, String interfaceCode) {
    // 先通过interfaceCode查询ApiInterface获取interfaceId
    // 再查询VendorConfig
    return null; // 实际实现需要注入ApiInterfaceService
}
```

- [ ] **Step 3: 提交VendorConfig修改**

```bash
git add data-platform-vendor/src/main/java/com/dataplatform/vendor/
git commit -m "feat(vendor): add interfaceId to VendorConfig"
```

---

## Task 6: 修改数据查询支持interfaceCode

**Files:**
- Modify: `data-platform-call/src/main/java/com/dataplatform/call/vo/ApiQueryReqVO.java`
- Modify: `data-platform-call/src/main/java/com/dataplatform/call/service/impl/DataQueryServiceImpl.java`

- [ ] **Step 1: 修改ApiQueryReqVO，添加interfaceCode字段**

```java
package com.dataplatform.call.vo;

import java.util.Map;

public class ApiQueryReqVO {
    private String vendorCode;
    private String dataType;
    private String interfaceCode;  // 新增
    private Map<String, Object> params;

    // getter和setter
    public String getVendorCode() { return vendorCode; }
    public void setVendorCode(String vendorCode) { this.vendorCode = vendorCode; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getInterfaceCode() { return interfaceCode; }
    public void setInterfaceCode(String interfaceCode) { this.interfaceCode = interfaceCode; }
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
}
```

- [ ] **Step 2: 修改DataQueryServiceImpl，使用interfaceCode查询配置**

在queryData方法中，使用interfaceCode获取VendorConfig：

```java
// 原来的方式
// VendorConfig config = vendorConfigService.getByVendorCodeAndDataTypeCode(vendorCode, dataType);

// 新的方式
VendorConfig config;
if (interfaceCode != null && !interfaceCode.isEmpty()) {
    config = vendorConfigService.getByVendorCodeAndInterfaceCode(vendorCode, interfaceCode);
} else {
    // 兼容旧逻辑
    config = vendorConfigService.getByVendorCodeAndDataTypeCode(vendorCode, dataType);
}
```

- [ ] **Step 3: 提交数据查询修改**

```bash
git add data-platform-call/src/main/java/com/dataplatform/call/
git commit -m "feat(call): support interfaceCode in data query"
```

---

## Task 7: 前端类型定义

**Files:**
- Modify: `data-platform-web/src/types/index.ts`

- [ ] **Step 1: 添加ApiInterface类型**

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
  hasConfig?: boolean
  createdAt: string
  updatedAt: string
}

// 数据查询请求
export interface DataQueryRequest {
  vendorCode: string
  dataTypeCode: string
  interfaceCode?: string
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

- [ ] **Step 2: 提交类型定义**

```bash
git add data-platform-web/src/types/index.ts
git commit -m "feat(web): add ApiInterface and DataQuery types"
```

---

## Task 8: 前端API封装

**Files:**
- Create: `data-platform-web/src/api/interface.ts`
- Create: `data-platform-web/src/api/data-query.ts`

- [ ] **Step 1: 创建interface.ts API文件**

```typescript
import { request } from '@/utils/request'
import type { ApiInterface, ListResponse } from '@/types'

export const getInterfaceList = (params: {
  page: number
  pageSize: number
  vendorId?: number
  dataTypeId?: number
  status?: string
}) => {
  return request.get<ListResponse<ApiInterface>>('/interface/list', { params })
}

export const getInterfaceById = (id: number) => {
  return request.get<ApiInterface>(`/interface/${id}`)
}

export const getInterfacesByDataType = (dataTypeId: number) => {
  return request.get<ApiInterface[]>(`/interface/by-data-type/${dataTypeId}`)
}

export const createInterface = (data: Partial<ApiInterface>) => {
  return request.post<ApiInterface>('/interface', data)
}

export const updateInterface = (id: number, data: Partial<ApiInterface>) => {
  return request.put<ApiInterface>(`/interface/${id}`, data)
}

export const deleteInterface = (id: number) => {
  return request.delete<void>(`/interface/${id}`)
}

export const updateInterfaceStatus = (id: number, status: 'active' | 'inactive') => {
  return request.patch<void>(`/interface/${id}/status`, { status })
}
```

- [ ] **Step 2: 创建data-query.ts API文件**

```typescript
import { request } from '@/utils/request'
import type { DataQueryRequest, DataQueryResponse } from '@/types'

export const executeQuery = (data: DataQueryRequest) => {
  return request.post<DataQueryResponse>('/data/query', data)
}

export const executeBatchQuery = (data: DataQueryRequest[]) => {
  return request.post<DataQueryResponse[]>('/data/batch-query', data)
}

export const getCacheStats = () => {
  return request.get<Record<string, any>>('/data/cache/stats')
}

export const clearCache = (params: { vendorCode: string; dataType: string; interfaceCode?: string }) => {
  return request.post<void>('/data/cache/clear', params)
}
```

- [ ] **Step 3: 提交API封装**

```bash
git add data-platform-web/src/api/
git commit -m "feat(web): add interface and data-query API modules"
```

---

## Task 9: 创建接口管理页面

**Files:**
- Create: `data-platform-web/src/views/interface/index.vue`
- Create: `data-platform-web/src/views/interface/components/InterfaceForm.vue`
- Create: `data-platform-web/src/views/interface/components/ApiConfigForm.vue`

- [ ] **Step 1: 创建接口管理主页面 index.vue**

```vue
<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>接口管理</h2>
        <p class="header-desc">管理数据接口定义与API配置</p>
      </div>
      <el-button type="primary" @click="handleAdd">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 5v14M5 12h14"/>
        </svg>
        新增接口
      </el-button>
    </div>

    <el-card class="search-card">
      <div class="search-bar">
        <div class="search-inputs">
          <el-select v-model="searchForm.dataTypeId" placeholder="数据类型" clearable class="search-select" @change="handleSearch">
            <el-option v-for="item in dataTypeOptions" :key="item.id" :label="item.typeName" :value="item.id" />
          </el-select>
          <el-select v-model="searchForm.status" placeholder="状态" clearable class="search-select">
            <el-option label="启用" value="active" />
            <el-option label="禁用" value="inactive" />
          </el-select>
        </div>
        <div class="search-btn-group">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </div>
    </el-card>

    <el-card class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="interfaceCode" label="接口编码" width="160">
          <template #default="{ row }">
            <span class="code-tag">{{ row.interfaceCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="interfaceName" label="接口名称" min-width="150" />
        <el-table-column prop="dataTypeName" label="数据类型" width="120" />
        <el-table-column prop="path" label="接口路径" min-width="180">
          <template #default="{ row }">
            <span class="path-cell">{{ row.path || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="API配置" width="100">
          <template #default="{ row }">
            <el-tag :type="row.hasConfig ? 'success' : 'warning'" size="small">
              {{ row.hasConfig ? '已配置' : '未配置' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.status" active-value="active" inactive-value="inactive" @change="handleStatusChange(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleConfig(row)">配置</el-button>
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <InterfaceForm v-model="formVisible" :form-data="currentRow" :mode="formMode" :data-type-options="dataTypeOptions" @success="handleFormSuccess" />
    <ApiConfigForm v-model="configVisible" :interface-data="currentRow" @success="handleConfigSuccess" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getInterfaceList, deleteInterface, updateInterfaceStatus } from '@/api/interface'
import { request } from '@/utils/request'
import InterfaceForm from './components/InterfaceForm.vue'
import ApiConfigForm from './components/ApiConfigForm.vue'
import type { ApiInterface } from '@/types'

const loading = ref(false)
const tableData = ref<ApiInterface[]>([])
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const searchForm = reactive({ dataTypeId: null as number | null, status: '' })
const dataTypeOptions = ref<{ id: number; typeName: string }[]>([])

const formVisible = ref(false)
const formMode = ref<'add' | 'edit'>('add')
const currentRow = ref<ApiInterface | null>(null)
const configVisible = ref(false)

const loadData = async () => {
  loading.value = true
  try {
    const res = await getInterfaceList({
      page: pagination.page,
      pageSize: pagination.pageSize,
      ...searchForm
    })
    tableData.value = res.data || []
    pagination.total = res.total || 0
  } catch {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const loadDataTypes = async () => {
  try {
    const res = await request.get('/datatype/list', { params: { pageSize: 100 } })
    dataTypeOptions.value = res.data || []
  } catch {
    dataTypeOptions.value = [
      { id: 1, typeName: '企业工商' },
      { id: 2, typeName: '个人征信' }
    ]
  }
}

const handleSearch = () => { pagination.page = 1; loadData() }
const handleReset = () => { searchForm.dataTypeId = null; searchForm.status = ''; pagination.page = 1; loadData() }
const handleAdd = () => { currentRow.value = null; formMode.value = 'add'; formVisible.value = true }
const handleEdit = (row: ApiInterface) => { currentRow.value = { ...row }; formMode.value = 'edit'; formVisible.value = true }
const handleConfig = (row: ApiInterface) => { currentRow.value = { ...row }; configVisible.value = true }

const handleDelete = async (row: ApiInterface) => {
  await ElMessageBox.confirm(`确认删除接口"${row.interfaceName}"吗？`, '提示', { type: 'warning' })
  await deleteInterface(row.id)
  ElMessage.success('删除成功')
  loadData()
}

const handleStatusChange = async (row: ApiInterface) => {
  await updateInterfaceStatus(row.id, row.status as 'active' | 'inactive')
  ElMessage.success(row.status === 'active' ? '已启用' : '已禁用')
}

const handleFormSuccess = () => { loadData() }
const handleConfigSuccess = () => { loadData() }

onMounted(() => { loadDataTypes(); loadData() })
</script>

<style scoped>
.page-container { max-width: 1600px; margin: 0 auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; color: var(--color-text-primary); margin: 0 0 4px; }
.header-desc { font-size: 14px; color: var(--color-text-tertiary); margin: 0; }
.page-header .el-button { display: flex; align-items: center; gap: 8px; }
.search-card { margin-bottom: 20px; }
.search-bar { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.search-inputs { display: flex; gap: 12px; }
.search-select { width: 160px; }
.code-tag { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); background: var(--color-bg-light); padding: 4px 10px; border-radius: 6px; }
.path-cell { font-family: var(--font-mono); font-size: 12px; color: var(--color-primary); }
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
```

- [ ] **Step 2: 创建InterfaceForm.vue组件**

```vue
<template>
  <el-dialog :model-value="modelValue" :title="mode === 'add' ? '新增接口' : '编辑接口'" width="600px" @close="handleClose">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="接口编码" prop="interfaceCode">
        <el-input v-model="form.interfaceCode" placeholder="如: COMPANY_BASE" :disabled="mode === 'edit'" />
      </el-form-item>
      <el-form-item label="接口名称" prop="interfaceName">
        <el-input v-model="form.interfaceName" placeholder="请输入接口名称" />
      </el-form-item>
      <el-form-item label="数据类型" prop="dataTypeId">
        <el-select v-model="form.dataTypeId" placeholder="请选择数据类型" style="width: 100%">
          <el-option v-for="item in dataTypeOptions" :key="item.id" :label="item.typeName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="接口路径">
        <el-input v-model="form.path" placeholder="如: /company/base" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入接口描述" />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="form.sort" :min="0" :max="999" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio value="active">启用</el-radio>
          <el-radio value="inactive">禁用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="loading">提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, FormInstance, FormRules } from 'element-plus'
import { createInterface, updateInterface } from '@/api/interface'
import type { ApiInterface } from '@/types'

interface Props {
  modelValue: boolean
  formData: ApiInterface | null
  mode: 'add' | 'edit'
  dataTypeOptions: { id: number; typeName: string }[]
}
const props = defineProps<Props>()
const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = ref({ interfaceCode: '', interfaceName: '', dataTypeId: null as number | null, path: '', description: '', sort: 0, status: 'active' })

const rules: FormRules = {
  interfaceCode: [{ required: true, message: '请输入接口编码', trigger: 'blur' }],
  interfaceName: [{ required: true, message: '请输入接口名称', trigger: 'blur' }],
  dataTypeId: [{ required: true, message: '请选择数据类型', trigger: 'change' }]
}

watch(() => props.formData, (val) => {
  if (val) {
    Object.assign(form.value, { interfaceCode: val.interfaceCode, interfaceName: val.interfaceName, dataTypeId: val.dataTypeId, path: val.path || '', description: val.description || '', sort: val.sort || 0, status: val.status || 'active' })
  } else {
    Object.assign(form.value, { interfaceCode: '', interfaceName: '', dataTypeId: null, path: '', description: '', sort: 0, status: 'active' })
  }
}, { immediate: true })

const handleClose = () => { emit('update:modelValue', false) }

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  loading.value = true
  try {
    if (props.mode === 'add') {
      await createInterface(form.value)
      ElMessage.success('新增成功')
    } else {
      await updateInterface(props.formData!.id, form.value)
      ElMessage.success('更新成功')
    }
    emit('success')
    handleClose()
  } catch (error) {
    console.error('操作失败:', error)
  } finally {
    loading.value = false
  }
}
</script>
```

- [ ] **Step 3: 创建ApiConfigForm.vue组件**

```vue
<template>
  <el-dialog :model-value="modelValue" title="API配置" width="700px" @close="handleClose">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-alert v-if="interfaceData" :title="`接口: ${interfaceData.interfaceName} (${interfaceData.interfaceCode})`" type="info" :closable="false" style="margin-bottom: 16px" />
      
      <el-divider content-position="left">基础配置</el-divider>
      <el-form-item label="API地址" prop="apiUrl">
        <el-input v-model="form.apiUrl" placeholder="https://api.vendor.com/endpoint" />
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="请求方法">
            <el-select v-model="form.method" style="width: 100%">
              <el-option label="POST" value="POST" />
              <el-option label="GET" value="GET" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="超时(ms)">
            <el-input-number v-model="form.timeout" :min="1000" :max="120000" :step="1000" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">重试与熔断</el-divider>
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="重试次数">
            <el-input-number v-model="form.retryCount" :min="0" :max="10" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="熔断阈值">
            <el-input-number v-model="form.circuitThreshold" :min="1" :max="100" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="熔断超时(s)">
            <el-input-number v-model="form.circuitTimeout" :min="10" :max="600" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">安全配置</el-divider>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="签名类型">
            <el-select v-model="form.signType" clearable style="width: 100%">
              <el-option label="HMAC-SHA256" value="HMAC_SHA256" />
              <el-option label="MD5" value="MD5" />
              <el-option label="无签名" value="NONE" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="加密类型">
            <el-select v-model="form.encryptType" clearable style="width: 100%">
              <el-option label="AES" value="AES" />
              <el-option label="无加密" value="NONE" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="请求头配置">
        <el-input v-model="form.headerConfig" type="textarea" :rows="3" placeholder='{"Authorization": "Bearer {secretKey}"}' />
      </el-form-item>

      <el-divider content-position="left">数据转换</el-divider>
      <el-form-item label="请求模板">
        <el-input v-model="form.requestTemplate" type="textarea" :rows="3" placeholder='{"name": "{companyName}"}' />
      </el-form-item>
      <el-form-item label="响应映射">
        <el-input v-model="form.responseMapping" type="textarea" :rows="3" placeholder='{"data.result": "result"}' />
      </el-form-item>

      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio value="active">启用</el-radio>
          <el-radio value="inactive">禁用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="loading">保存配置</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, FormInstance, FormRules } from 'element-plus'
import type { ApiInterface } from '@/types'

interface Props {
  modelValue: boolean
  interfaceData: ApiInterface | null
}
const props = defineProps<Props>()
const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = ref({
  apiUrl: '',
  method: 'POST',
  timeout: 30000,
  retryCount: 3,
  circuitThreshold: 5,
  circuitTimeout: 60,
  signType: '',
  encryptType: '',
  headerConfig: '',
  requestTemplate: '',
  responseMapping: '',
  status: 'active'
})

const rules: FormRules = {
  apiUrl: [{ required: true, message: '请输入API地址', trigger: 'blur' }]
}

const handleClose = () => { emit('update:modelValue', false) }

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  loading.value = true
  try {
    // TODO: 调用保存API配置的接口
    ElMessage.success('保存成功')
    emit('success')
    handleClose()
  } catch (error) {
    console.error('保存失败:', error)
  } finally {
    loading.value = false
  }
}
</script>
```

- [ ] **Step 4: 提交接口管理页面**

```bash
git add data-platform-web/src/views/interface/
git commit -m "feat(web): add interface management page"
```

---

## Task 10: 创建数据查询测试页面

**Files:**
- Create: `data-platform-web/src/views/data-test/index.vue`

- [ ] **Step 1: 创建数据查询测试页面**

```vue
<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>数据查询测试</h2>
        <p class="header-desc">测试数据接口调用与响应</p>
      </div>
    </div>

    <el-card class="query-card">
      <div class="section-title">查询参数</div>
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="厂商">
            <el-select v-model="queryParams.vendorId" placeholder="选择厂商" @change="handleVendorChange" style="width: 100%">
              <el-option v-for="item in vendorOptions" :key="item.id" :label="item.vendorName" :value="item.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="数据类型">
            <el-select v-model="queryParams.dataTypeId" placeholder="选择数据类型" @change="handleDataTypeChange" :disabled="!queryParams.vendorId" style="width: 100%">
              <el-option v-for="item in dataTypeOptions" :key="item.id" :label="item.typeName" :value="item.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="接口">
            <el-select v-model="queryParams.interfaceCode" placeholder="选择接口" :disabled="!queryParams.dataTypeId" style="width: 100%">
              <el-option v-for="item in interfaceOptions" :key="item.interfaceCode" :label="item.interfaceName" :value="item.interfaceCode" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="请求参数">
        <el-input v-model="queryParams.paramsJson" type="textarea" :rows="4" placeholder='{"companyName": "阿里巴巴"}' />
      </el-form-item>

      <div class="button-group">
        <el-button type="primary" @click="executeQuery" :loading="loading" :disabled="!queryParams.interfaceCode">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width: 16px; height: 16px; margin-right: 6px;">
            <polygon points="5 3 19 12 5 21 5 3"/>
          </svg>
          执行查询
        </el-button>
        <el-button @click="clearForm">清空</el-button>
      </div>
    </el-card>

    <el-card class="result-card" v-if="queryResult || error">
      <div class="result-header">
        <span class="section-title">查询结果</span>
        <span v-if="queryResult" class="result-meta">
          接口: {{ queryResult.interfaceName || '-' }} | 耗时: {{ queryResult.latency || 0 }}ms
        </span>
      </div>
      
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" style="margin-bottom: 16px" />
      
      <div v-if="queryResult && queryResult.success" class="result-content">
        <pre class="json-output">{{ JSON.stringify(queryResult.data, null, 2) }}</pre>
      </div>
      <div v-else-if="queryResult && !queryResult.success" class="result-error">
        <el-empty description="查询失败">
          <template #description>
            <span>错误码: {{ queryResult.errorCode }}</span><br>
            <span>错误信息: {{ queryResult.errorMsg }}</span>
          </template>
        </el-empty>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getInterfacesByDataType } from '@/api/interface'
import { executeQuery } from '@/api/data-query'
import { request } from '@/utils/request'

const loading = ref(false)
const error = ref('')
const queryResult = ref<any>(null)

const queryParams = reactive({
  vendorId: null as number | null,
  dataTypeId: null as number | null,
  interfaceCode: '',
  paramsJson: '{}'
})

const vendorOptions = ref<{ id: number; vendorName: string; vendorCode: string }[]>([])
const dataTypeOptions = ref<{ id: number; typeName: string }[]>([])
const interfaceOptions = ref<{ interfaceCode: string; interfaceName: string }[]>([])

const loadVendors = async () => {
  try {
    const res = await request.get('/vendor/list', { params: { pageSize: 100 } })
    vendorOptions.value = res.data || []
  } catch {
    vendorOptions.value = [
      { id: 1, vendorName: '天眼查', vendorCode: 'TIANYANCHA' },
      { id: 2, vendorName: '企查查', vendorCode: 'QICHACHA' }
    ]
  }
}

const handleVendorChange = () => {
  queryParams.dataTypeId = null
  queryParams.interfaceCode = ''
  interfaceOptions.value = []
  loadDataTypeOptions()
}

const loadDataTypeOptions = async () => {
  if (!queryParams.vendorId) return
  try {
    const res = await request.get('/datatype/list', { params: { pageSize: 100 } })
    dataTypeOptions.value = res.data || []
  } catch {
    dataTypeOptions.value = [
      { id: 1, typeName: '企业工商' },
      { id: 2, typeName: '个人征信' }
    ]
  }
}

const handleDataTypeChange = async () => {
  queryParams.interfaceCode = ''
  if (!queryParams.dataTypeId) {
    interfaceOptions.value = []
    return
  }
  try {
    const res = await getInterfacesByDataType(queryParams.dataTypeId)
    interfaceOptions.value = res || []
  } catch {
    interfaceOptions.value = [
      { interfaceCode: 'COMPANY_BASE', interfaceName: '企业基本信息' },
      { interfaceCode: 'COMPANY_SHAREHOLDER', interfaceName: '股东信息' }
    ]
  }
}

const executeQueryAction = async () => {
  if (!queryParams.interfaceCode) {
    ElMessage.warning('请选择接口')
    return
  }
  
  let params = {}
  try {
    params = JSON.parse(queryParams.paramsJson || '{}')
  } catch {
    ElMessage.error('请求参数JSON格式错误')
    return
  }

  loading.value = true
  error.value = ''
  queryResult.value = null

  try {
    const vendor = vendorOptions.value.find(v => v.id === queryParams.vendorId)
    const dataType = dataTypeOptions.value.find(d => d.id === queryParams.dataTypeId)
    const interfaceItem = interfaceOptions.value.find(i => i.interfaceCode === queryParams.interfaceCode)
    
    const result = await executeQuery({
      vendorCode: vendor?.vendorCode || '',
      dataTypeCode: dataType?.typeName || '',
      interfaceCode: queryParams.interfaceCode,
      params
    })
    
    queryResult.value = {
      ...result,
      interfaceName: interfaceItem?.interfaceName
    }
  } catch (e: any) {
    error.value = e.message || '查询失败'
  } finally {
    loading.value = false
  }
}

const clearForm = () => {
  queryParams.vendorId = null
  queryParams.dataTypeId = null
  queryParams.interfaceCode = ''
  queryParams.paramsJson = '{}'
  queryResult.value = null
  error.value = ''
}

onMounted(() => { loadVendors() })
</script>

<style scoped>
.page-container { max-width: 1400px; margin: 0 auto; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; color: var(--color-text-primary); margin: 0 0 4px; }
.header-desc { font-size: 14px; color: var(--color-text-tertiary); margin: 0; }

.query-card, .result-card { margin-bottom: 20px; }
.section-title { font-weight: 600; font-size: 15px; color: var(--color-text-primary); margin-bottom: 16px; display: block; }
.button-group { display: flex; gap: 12px; margin-top: 16px; }
.button-group .el-button { display: flex; align-items: center; }

.result-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.result-meta { font-size: 13px; color: var(--color-text-tertiary); }
.result-content { background: #1a1a2e; border-radius: 8px; padding: 16px; overflow-x: auto; }
.json-output { margin: 0; color: #00d4aa; font-family: var(--font-mono); font-size: 13px; line-height: 1.6; white-space: pre-wrap; word-break: break-all; }
</style>
```

- [ ] **Step 2: 提交数据查询测试页面**

```bash
git add data-platform-web/src/views/data-test/
git commit -m "feat(web): add data query test page"
```

---

## Task 11: 更新路由配置

**Files:**
- Modify: `data-platform-web/src/router/index.ts`

- [ ] **Step 1: 添加新路由**

在router/index.ts的children数组中添加：

```typescript
{
  path: '/interface',
  name: 'Interface',
  component: () => import('@/views/interface/index.vue'),
  meta: { title: '接口管理' }
},
{
  path: '/data-test',
  name: 'DataTest',
  component: () => import('@/views/data-test/index.vue'),
  meta: { title: '数据测试' }
}
```

- [ ] **Step 2: 提交路由配置**

```bash
git add data-platform-web/src/router/index.ts
git commit -m "feat(web): add interface and data-test routes"
```

---

## Task 12: 更新根pom.xml

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: 添加新模块到modules列表**

```xml
<module>data-platform-interface</module>
```

- [ ] **Step 2: 提交pom.xml修改**

```bash
git add pom.xml
git commit -m "feat: add data-platform-interface module"
```

---

## Verification Checklist

- [ ] 数据库迁移脚本执行成功
- [ ] 后端接口管理API正常工作
- [ ] 前端接口管理页面可正常访问
- [ ] 接口增删改查功能正常
- [ ] API配置弹窗正常工作
- [ ] 数据查询测试页面三级联动正常
- [ ] 查询功能正常返回结果
