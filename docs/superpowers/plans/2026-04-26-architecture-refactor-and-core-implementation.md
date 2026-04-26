# 数据管理平台架构重构与核心功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复架构缺陷，实现MVP核心功能，使平台能够真正对接外部数据厂商API并正确计费

**Architecture:** 采用适配器模式对接不同厂商，策略模式实现计费，Resilience4j实现熔断重试，统一实体类到common模块消除重复

**Tech Stack:** Java 21, Spring Boot 3.4, MyBatis-Plus, Resilience4j, Redis, Kafka

---

## 文件结构映射

### 新建文件

```
data-platform-common/
├── src/main/java/com/dataplatform/common/
│   ├── entity/
│   │   ├── unified/
│   │   │   ├── CallRecordDO.java          # 统一调用记录实体
│   │   │   ├── VendorConfigDO.java        # 统一厂商配置实体
│   │   │   └── BillingRuleDO.java         # 统一计费规则实体
│   │   └── ...
│   ├── adapter/
│   │   ├── VendorAdapter.java             # 厂商适配器接口
│   │   ├── AbstractVendorAdapter.java     # 抽象适配器基类
│   │   ├── HttpVendorAdapter.java         # HTTP厂商适配器
│   │   └── VendorAdapterFactory.java      # 适配器工厂
│   ├── billing/
│   │   ├── BillingCalculator.java         # 计费计算器接口
│   │   ├── StandardBillingCalculator.java # 标准计费
│   │   ├── TieredBillingCalculator.java   # 阶梯计费
│   │   └── DynamicBillingCalculator.java  # 动态计费(SLA补偿)
│   └── circuitbreaker/
│       ├── CircuitBreakerManager.java     # 熔断管理器
│       └── VendorCircuitBreaker.java      # 厂商熔断器

data-platform-call/
├── src/main/java/com/dataplatform/call/
│   ├── service/
│   │   ├── impl/
│   │   │   └── DataQueryServiceImpl.java  # 重构：使用适配器
│   │   ├── VendorProxyService.java        # 厂商代理服务
│   │   └── BillingIntegrationService.java # 计费集成服务
│   └── converter/
│       ├── RequestConverter.java          # 请求参数转换器
│       └── ResponseConverter.java         # 响应格式转换器

data-platform-billing/
├── src/main/java/com/dataplatform/billing/
│   ├── service/
│   │   ├── impl/
│   │   │   └── BillingServiceImpl.java    # 重构：实现计费逻辑
│   │   └── BillingRuleService.java        # 计费规则服务
│   └── strategy/
│       ├── BillingStrategy.java           # 计费策略接口
│       └── BillingStrategyFactory.java    # 策略工厂
```

### 修改文件

```
data-platform-call/pom.xml                 # 添加vendor、billing依赖
data-platform-common/src/main/java/.../entity/CallRecord.java  # 添加缺失字段
data-platform-call/src/main/java/.../service/impl/DataQueryServiceImpl.java  # 重构核心逻辑
data-platform-billing/src/main/java/.../service/impl/BillingServiceImpl.java # 实现计费计算
data-platform-vendor/src/main/java/.../entity/VendorConfig.java # 添加dataType字段
data-platform-gateway/src/main/resources/application.yml        # 添加Filter配置
```

---

## 阶段一：修复架构缺陷 (Task 1-6)

---

### Task 1: 添加模块依赖

**Files:**
- Modify: `data-platform-call/pom.xml`

**目标**: 为 call 模块添加 vendor 和 billing 依赖

- [ ] **Step 1: 修改 data-platform-call/pom.xml**

在 `<dependencies>` 中添加：

```xml
<!-- 厂商管理服务 -->
<dependency>
    <groupId>com.dataplatform</groupId>
    <artifactId>data-platform-vendor</artifactId>
    <version>${project.version}</version>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<!-- 计费服务 -->
<dependency>
    <groupId>com.dataplatform</groupId>
    <artifactId>data-platform-billing</artifactId>
    <version>${project.version}</version>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<!-- Resilience4j 熔断器 -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-reactor</artifactId>
    <version>2.2.0</version>
</dependency>
```

- [ ] **Step 2: 验证依赖**

```bash
cd /Users/lixd/IdeaProjects/Git/ClaudeCodeProject/data-manager-hub
mvn dependency:tree -pl data-platform-call | grep -E "vendor|billing"
```

Expected: 显示 data-platform-vendor 和 data-platform-billing 依赖

- [ ] **Step 3: 提交**

```bash
git add data-platform-call/pom.xml
git commit -m "feat(call): add vendor and billing dependencies for core functionality"
```

---

### Task 2: 创建统一实体类

**Files:**
- Create: `data-platform-common/src/main/java/com/dataplatform/common/entity/unified/CallRecordDO.java`
- Create: `data-platform-common/src/main/java/com/dataplatform/common/entity/unified/VendorConfigDO.java`
- Create: `data-platform-common/src/main/java/com/dataplatform/common/entity/unified/BillingRuleDO.java`

**目标**: 在 common 模块创建统一实体类，供其他模块引用

- [ ] **Step 1: 创建 CallRecordDO.java**

```java
package com.dataplatform.common.entity.unified;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 统一调用记录实体 - 所有模块使用此类
 */
@TableName("call_record")
public class CallRecordDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestId;
    private Long tenantId;
    private Long callerId;
    private Long apiKeyId;
    private Long vendorId;
    private String vendorCode;
    private String dataType;
    private String dataTypeCode;

    /** 请求参数 JSON */
    private String requestParams;

    /** 响应数据 JSON */
    private String responseData;

    /** 是否成功 */
    private Boolean success;

    private String errorCode;
    private String errorMsg;

    /** 响应时间(毫秒) */
    private Integer latency;

    /** 费用 */
    private BigDecimal cost;

    /** 是否命中缓存 */
    private Boolean cached;

    /** 调用时间 */
    private LocalDateTime callTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Boolean deleted;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getCallerId() { return callerId; }
    public void setCallerId(Long callerId) { this.callerId = callerId; }
    public Long getApiKeyId() { return apiKeyId; }
    public void setApiKeyId(Long apiKeyId) { this.apiKeyId = apiKeyId; }
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public String getVendorCode() { return vendorCode; }
    public void setVendorCode(String vendorCode) { this.vendorCode = vendorCode; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getDataTypeCode() { return dataTypeCode; }
    public void setDataTypeCode(String dataTypeCode) { this.dataTypeCode = dataTypeCode; }
    public String getRequestParams() { return requestParams; }
    public void setRequestParams(String requestParams) { this.requestParams = requestParams; }
    public String getResponseData() { return responseData; }
    public void setResponseData(String responseData) { this.responseData = responseData; }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public Integer getLatency() { return latency; }
    public void setLatency(Integer latency) { this.latency = latency; }
    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }
    public Boolean getCached() { return cached; }
    public void setCached(Boolean cached) { this.cached = cached; }
    public LocalDateTime getCallTime() { return callTime; }
    public void setCallTime(LocalDateTime callTime) { this.callTime = callTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
```

- [ ] **Step 2: 创建 VendorConfigDO.java**

```java
package com.dataplatform.common.entity.unified;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * 统一厂商配置实体
 */
@TableName("vendor_config")
public class VendorConfigDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long vendorId;
    private Long dataTypeId;

    /** 数据类型编码(冗余字段，便于查询) */
    private String dataTypeCode;

    private String apiUrl;
    private String method;
    private Integer timeout;
    private Integer retryCount;
    private Integer circuitThreshold;
    private Integer circuitTimeout;
    private String signType;
    private String encryptType;

    /** 请求头配置 JSON */
    private String headerConfig;

    /** 请求参数模板 JSON */
    private String requestTemplate;

    /** 响应字段映射 JSON */
    private String responseMapping;

    /** 备用厂商ID */
    private Long fallbackVendorId;

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
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public Long getDataTypeId() { return dataTypeId; }
    public void setDataTypeId(Long dataTypeId) { this.dataTypeId = dataTypeId; }
    public String getDataTypeCode() { return dataTypeCode; }
    public void setDataTypeCode(String dataTypeCode) { this.dataTypeCode = dataTypeCode; }
    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public Integer getTimeout() { return timeout; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Integer getCircuitThreshold() { return circuitThreshold; }
    public void setCircuitThreshold(Integer circuitThreshold) { this.circuitThreshold = circuitThreshold; }
    public Integer getCircuitTimeout() { return circuitTimeout; }
    public void setCircuitTimeout(Integer circuitTimeout) { this.circuitTimeout = circuitTimeout; }
    public String getSignType() { return signType; }
    public void setSignType(String signType) { this.signType = signType; }
    public String getEncryptType() { return encryptType; }
    public void setEncryptType(String encryptType) { this.encryptType = encryptType; }
    public String getHeaderConfig() { return headerConfig; }
    public void setHeaderConfig(String headerConfig) { this.headerConfig = headerConfig; }
    public String getRequestTemplate() { return requestTemplate; }
    public void setRequestTemplate(String requestTemplate) { this.requestTemplate = requestTemplate; }
    public String getResponseMapping() { return responseMapping; }
    public void setResponseMapping(String responseMapping) { this.responseMapping = responseMapping; }
    public Long getFallbackVendorId() { return fallbackVendorId; }
    public void setFallbackVendorId(Long fallbackVendorId) { this.fallbackVendorId = fallbackVendorId; }
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

- [ ] **Step 3: 创建 BillingRuleDO.java**

```java
package com.dataplatform.common.entity.unified;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 统一计费规则实体
 */
@TableName("billing_rule")
public class BillingRuleDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleName;
    private Long vendorId;
    private String vendorName;
    private String dataType;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 阶梯下限 */
    private Integer tierMin;

    /** 阶梯上限 */
    private Integer tierMax;

    /** 折扣率 */
    private BigDecimal discount;

    /** SLA阈值(毫秒) */
    private Integer slaThreshold;

    /** SLA补偿系数 */
    private BigDecimal compensationRate;

    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedBy;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public Integer getTierMin() { return tierMin; }
    public void setTierMin(Integer tierMin) { this.tierMin = tierMin; }
    public Integer getTierMax() { return tierMax; }
    public void setTierMax(Integer tierMax) { this.tierMax = tierMax; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public Integer getSlaThreshold() { return slaThreshold; }
    public void setSlaThreshold(Integer slaThreshold) { this.slaThreshold = slaThreshold; }
    public BigDecimal getCompensationRate() { return compensationRate; }
    public void setCompensationRate(BigDecimal compensationRate) { this.compensationRate = compensationRate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}
```

- [ ] **Step 4: 编译验证**

```bash
cd /Users/lixd/IdeaProjects/Git/ClaudeCodeProject/data-manager-hub
mvn compile -pl data-platform-common -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add data-platform-common/src/main/java/com/dataplatform/common/entity/unified/
git commit -m "feat(common): add unified entity classes for cross-module usage"
```

---

### Task 3: 修复数据库映射 - 添加 dataTypeCode 字段

**Files:**
- Modify: `sql/init.sql`
- Create: `sql/migrations/V001__add_data_type_code.sql`

**目标**: 为 vendor_config 表添加 dataTypeCode 冗余字段，便于查询

- [ ] **Step 1: 创建迁移脚本**

```sql
-- sql/migrations/V001__add_data_type_code.sql

-- 1. 添加 dataTypeCode 字段
ALTER TABLE vendor_config ADD COLUMN IF NOT EXISTS data_type_code VARCHAR(50);

-- 2. 从 data_type 表回填数据
UPDATE vendor_config vc
SET data_type_code = dt.data_type_code
FROM data_type dt
WHERE vc.data_type_id = dt.id;

-- 3. 创建索引
CREATE INDEX IF NOT EXISTS idx_vendor_config_data_type_code ON vendor_config(data_type_code);

-- 4. 添加注释
COMMENT ON COLUMN vendor_config.data_type_code IS '数据类型编码(冗余字段)';
```

- [ ] **Step 2: 更新 init.sql**

在 vendor_config 表定义中添加字段：

```sql
-- 在 vendor_config 表定义中添加
data_type_code VARCHAR(50),  -- 数据类型编码(冗余字段)
```

- [ ] **Step 3: 执行迁移**

```bash
psql -U postgres -d dataplatform -f sql/migrations/V001__add_data_type_code.sql
```

Expected: ALTER TABLE, UPDATE, CREATE INDEX 成功

- [ ] **Step 4: 提交**

```bash
git add sql/migrations/ sql/init.sql
git commit -m "feat(db): add data_type_code column to vendor_config for efficient queries"
```

---

### Task 4: 创建厂商适配器接口和抽象类

**Files:**
- Create: `data-platform-common/src/main/java/com/dataplatform/common/adapter/VendorAdapter.java`
- Create: `data-platform-common/src/main/java/com/dataplatform/common/adapter/AbstractVendorAdapter.java`
- Create: `data-platform-common/src/main/java/com/dataplatform/common/adapter/VendorAdapterConfig.java`

**目标**: 定义厂商适配器标准接口，支持不同厂商的统一调用

- [ ] **Step 1: 创建 VendorAdapterConfig.java**

```java
package com.dataplatform.common.adapter;

import java.util.Map;

/**
 * 厂商适配器配置
 */
public class VendorAdapterConfig {

    private String vendorCode;
    private String dataTypeCode;
    private String apiUrl;
    private String method;
    private Integer timeout;
    private Integer retryCount;
    private Map<String, String> headers;
    private String requestTemplate;
    private String responseMapping;
    private String signType;
    private String secretKey;

    // Getters and Setters
    public String getVendorCode() { return vendorCode; }
    public void setVendorCode(String vendorCode) { this.vendorCode = vendorCode; }
    public String getDataTypeCode() { return dataTypeCode; }
    public void setDataTypeCode(String dataTypeCode) { this.dataTypeCode = dataTypeCode; }
    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public Integer getTimeout() { return timeout; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }
    public String getRequestTemplate() { return requestTemplate; }
    public void setRequestTemplate(String requestTemplate) { this.requestTemplate = requestTemplate; }
    public String getResponseMapping() { return responseMapping; }
    public void setResponseMapping(String responseMapping) { this.responseMapping = responseMapping; }
    public String getSignType() { return signType; }
    public void setSignType(String signType) { this.signType = signType; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
}
```

- [ ] **Step 2: 创建 VendorAdapter.java**

```java
package com.dataplatform.common.adapter;

import java.util.Map;

/**
 * 厂商适配器接口
 * 所有厂商适配器必须实现此接口
 */
public interface VendorAdapter {

    /**
     * 获取厂商编码
     */
    String getVendorCode();

    /**
     * 检查是否支持该数据类型
     */
    boolean supports(String dataTypeCode);

    /**
     * 执行数据查询
     *
     * @param config    适配器配置
     * @param params    请求参数
     * @return 响应结果
     */
    Map<String, Object> execute(VendorAdapterConfig config, Map<String, Object> params);

    /**
     * 转换请求参数 (内部字段 -> 厂商字段)
     *
     * @param params    内部参数
     * @param mapping   字段映射规则
     * @return 厂商参数
     */
    Map<String, Object> transformRequest(Map<String, Object> params, String mapping);

    /**
     * 转换响应数据 (厂商字段 -> 内部字段)
     *
     * @param response  厂商响应
     * @param mapping   字段映射规则
     * @return 标准响应
     */
    Map<String, Object> transformResponse(Map<String, Object> response, String mapping);
}
```

- [ ] **Step 3: 创建 AbstractVendorAdapter.java**

```java
package com.dataplatform.common.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 厂商适配器抽象基类
 * 提供通用的参数转换和响应处理逻辑
 */
public abstract class AbstractVendorAdapter implements VendorAdapter {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> transformRequest(Map<String, Object> params, String mapping) {
        if (mapping == null || mapping.isEmpty()) {
            return new HashMap<>(params);
        }

        try {
            Map<String, String> fieldMapping = objectMapper.readValue(mapping,
                new TypeReference<Map<String, String>>() {});

            Map<String, Object> transformed = new HashMap<>();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String targetField = fieldMapping.getOrDefault(entry.getKey(), entry.getKey());
                transformed.put(targetField, entry.getValue());
            }
            return transformed;
        } catch (Exception e) {
            log.warn("请求参数转换失败, 使用原始参数: {}", e.getMessage());
            return new HashMap<>(params);
        }
    }

    @Override
    public Map<String, Object> transformResponse(Map<String, Object> response, String mapping) {
        if (mapping == null || mapping.isEmpty()) {
            return new HashMap<>(response);
        }

        try {
            Map<String, String> fieldMapping = objectMapper.readValue(mapping,
                new TypeReference<Map<String, String>>() {});

            Map<String, Object> transformed = new HashMap<>();
            for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
                String sourcePath = entry.getKey();
                String targetField = entry.getValue();
                Object value = getNestedValue(response, sourcePath);
                if (value != null) {
                    transformed.put(targetField, value);
                }
            }
            return transformed;
        } catch (Exception e) {
            log.warn("响应数据转换失败, 使用原始响应: {}", e.getMessage());
            return new HashMap<>(response);
        }
    }

    /**
     * 从嵌套Map中获取值
     * 支持路径: "data.result.name"
     */
    protected Object getNestedValue(Map<String, Object> map, String path) {
        if (map == null || path == null) {
            return null;
        }

        String[] keys = path.split("\\.");
        Object current = map;

        for (String key : keys) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(key);
            } else {
                return null;
            }
        }

        return current;
    }

    /**
     * 构建签名 (子类可重写实现具体签名算法)
     */
    protected String buildSignature(Map<String, Object> params, String secretKey, String signType) {
        // 默认不签名, 子类可重写
        return null;
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
mvn compile -pl data-platform-common -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add data-platform-common/src/main/java/com/dataplatform/common/adapter/
git commit -m "feat(common): add vendor adapter interface and abstract base class"
```

---

### Task 5: 创建 HTTP 厂商适配器实现

**Files:**
- Create: `data-platform-common/src/main/java/com/dataplatform/common/adapter/HttpVendorAdapter.java`
- Create: `data-platform-common/src/main/java/com/dataplatform/common/adapter/VendorAdapterFactory.java`

**目标**: 实现 HTTP 方式调用厂商 API 的适配器

- [ ] **Step 1: 添加 HTTP 客户端依赖到 common 模块**

修改 `data-platform-common/pom.xml`，添加：

```xml
<!-- OkHttp HTTP客户端 -->
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.12.0</version>
</dependency>
```

- [ ] **Step 2: 创建 HttpVendorAdapter.java**

```java
package com.dataplatform.common.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP厂商适配器
 * 通过HTTP/HTTPS调用厂商API
 */
public class HttpVendorAdapter extends AbstractVendorAdapter {

    private static final Logger log = LoggerFactory.getLogger(HttpVendorAdapter.class);
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final String vendorCode;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpVendorAdapter(String vendorCode) {
        this.vendorCode = vendorCode;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public String getVendorCode() {
        return vendorCode;
    }

    @Override
    public boolean supports(String dataTypeCode) {
        // 默认支持所有数据类型
        return true;
    }

    @Override
    public Map<String, Object> execute(VendorAdapterConfig config, Map<String, Object> params) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 转换请求参数
            Map<String, Object> vendorParams = transformRequest(params, config.getRequestTemplate());

            // 2. 构建请求
            Request request = buildRequest(config, vendorParams);

            // 3. 执行请求
            try (Response response = httpClient.newCall(request).execute()) {
                long latency = System.currentTimeMillis() - startTime;

                // 4. 处理响应
                return handleResponse(response, config, latency);
            }

        } catch (IOException e) {
            log.error("厂商API调用失败: vendor={}, url={}, error={}",
                vendorCode, config.getApiUrl(), e.getMessage());

            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("errorCode", "VENDOR_ERROR");
            errorResult.put("errorMsg", e.getMessage());
            errorResult.put("latency", System.currentTimeMillis() - startTime);
            return errorResult;
        }
    }

    /**
     * 构建 HTTP 请求
     */
    private Request buildRequest(VendorAdapterConfig config, Map<String, Object> params) throws IOException {
        String url = config.getApiUrl();
        String method = config.getMethod() != null ? config.getMethod().toUpperCase() : "POST";

        // 构建请求体
        String jsonBody = objectMapper.writeValueAsString(params);
        RequestBody body = RequestBody.create(jsonBody, JSON_MEDIA_TYPE);

        // 构建请求构建器
        Request.Builder builder = new Request.Builder().url(url);

        // 添加请求头
        if (config.getHeaders() != null) {
            for (Map.Entry<String, String> header : config.getHeaders().entrySet()) {
                String value = header.getValue();
                // 支持变量替换: {secretKey} -> 实际密钥
                if (value.contains("{secretKey}") && config.getSecretKey() != null) {
                    value = value.replace("{secretKey}", config.getSecretKey());
                }
                builder.addHeader(header.getKey(), value);
            }
        }

        // 设置请求方法
        if ("GET".equals(method)) {
            HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                urlBuilder.addQueryParameter(entry.getKey(), String.valueOf(entry.getValue()));
            }
            builder.url(urlBuilder.build()).get();
        } else {
            builder.post(body);
        }

        return builder.build();
    }

    /**
     * 处理 HTTP 响应
     */
    private Map<String, Object> handleResponse(Response response, VendorAdapterConfig config, long latency)
            throws IOException {

        Map<String, Object> result = new HashMap<>();
        result.put("latency", latency);

        if (!response.isSuccessful()) {
            result.put("success", false);
            result.put("errorCode", "HTTP_" + response.code());
            result.put("errorMsg", "HTTP请求失败: " + response.code());
            return result;
        }

        // 解析响应体
        ResponseBody responseBody = response.body();
        if (responseBody == null) {
            result.put("success", false);
            result.put("errorCode", "EMPTY_RESPONSE");
            result.put("errorMsg", "响应体为空");
            return result;
        }

        String responseStr = responseBody.string();
        Map<String, Object> vendorResponse = objectMapper.readValue(responseStr,
            new TypeReference<Map<String, Object>>() {});

        // 转换响应字段
        Map<String, Object> transformedResponse = transformResponse(vendorResponse, config.getResponseMapping());

        result.put("success", true);
        result.put("data", transformedResponse);
        result.put("rawResponse", vendorResponse);

        return result;
    }
}
```

- [ ] **Step 3: 创建 VendorAdapterFactory.java**

```java
package com.dataplatform.common.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 厂商适配器工厂
 * 管理和创建厂商适配器实例
 */
public class VendorAdapterFactory {

    private static final Logger log = LoggerFactory.getLogger(VendorAdapterFactory.class);

    /** 适配器缓存 */
    private static final Map<String, VendorAdapter> ADAPTER_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取适配器
     *
     * @param vendorCode 厂商编码
     * @return 适配器实例
     */
    public static VendorAdapter getAdapter(String vendorCode) {
        return ADAPTER_CACHE.computeIfAbsent(vendorCode, code -> {
            log.info("创建厂商适配器: {}", code);
            return createAdapter(code);
        });
    }

    /**
     * 创建适配器
     */
    private static VendorAdapter createAdapter(String vendorCode) {
        // 默认使用 HTTP 适配器
        // 未来可根据厂商类型创建不同适配器 (如: WebService, FTP 等)
        return new HttpVendorAdapter(vendorCode);
    }

    /**
     * 注册自定义适配器
     */
    public static void registerAdapter(String vendorCode, VendorAdapter adapter) {
        ADAPTER_CACHE.put(vendorCode, adapter);
        log.info("注册厂商适配器: {}", vendorCode);
    }

    /**
     * 清除缓存
     */
    public static void clearCache() {
        ADAPTER_CACHE.clear();
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
mvn compile -pl data-platform-common -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add data-platform-common/src/main/java/com/dataplatform/common/adapter/
git add data-platform-common/pom.xml
git commit -m "feat(common): implement HTTP vendor adapter and factory"
```

---

### Task 6: 创建计费计算器

**Files:**
- Create: `data-platform-common/src/main/java/com/dataplatform/common/billing/BillingCalculator.java`
- Create: `data-platform-common/src/main/java/com/dataplatform/common/billing/StandardBillingCalculator.java`
- Create: `data-platform-common/src/main/java/com/dataplatform/common/billing/TieredBillingCalculator.java`
- Create: `data-platform-common/src/main/java/com/dataplatform/common/billing/DynamicBillingCalculator.java`

**目标**: 实现标准计费、阶梯计费、动态计费(SLA补偿)三种计费策略

- [ ] **Step 1: 创建 BillingCalculator.java 接口**

```java
package com.dataplatform.common.billing;

import com.dataplatform.common.entity.unified.BillingRuleDO;
import java.math.BigDecimal;

/**
 * 计费计算器接口
 */
public interface BillingCalculator {

    /**
     * 计算费用
     *
     * @param rule        计费规则
     * @param callCount   调用次数
     * @param latencyMs   响应时间(毫秒)
     * @return 费用金额
     */
    BigDecimal calculate(BillingRuleDO rule, long callCount, Integer latencyMs);

    /**
     * 计算单次调用费用
     *
     * @param rule      计费规则
     * @param latencyMs 响应时间(毫秒)
     * @return 单次费用
     */
    BigDecimal calculateSingle(BillingRuleDO rule, Integer latencyMs);
}
```

- [ ] **Step 2: 创建 StandardBillingCalculator.java**

```java
package com.dataplatform.common.billing;

import com.dataplatform.common.entity.unified.BillingRuleDO;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 标准计费计算器
 * 费用 = 单价 × 调用次数
 */
public class StandardBillingCalculator implements BillingCalculator {

    @Override
    public BigDecimal calculate(BillingRuleDO rule, long callCount, Integer latencyMs) {
        if (rule == null || rule.getUnitPrice() == null) {
            return BigDecimal.ZERO;
        }

        return rule.getUnitPrice()
            .multiply(BigDecimal.valueOf(callCount))
            .setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateSingle(BillingRuleDO rule, Integer latencyMs) {
        if (rule == null || rule.getUnitPrice() == null) {
            return BigDecimal.ZERO;
        }
        return rule.getUnitPrice();
    }
}
```

- [ ] **Step 3: 创建 TieredBillingCalculator.java**

```java
package com.dataplatform.common.billing;

import com.dataplatform.common.entity.unified.BillingRuleDO;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 阶梯计费计算器
 * 根据调用量区间应用不同折扣
 */
public class TieredBillingCalculator implements BillingCalculator {

    @Override
    public BigDecimal calculate(BillingRuleDO rule, long callCount, Integer latencyMs) {
        if (rule == null || rule.getUnitPrice() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal baseAmount = rule.getUnitPrice()
            .multiply(BigDecimal.valueOf(callCount));

        // 根据调用量确定折扣
        BigDecimal discount = determineDiscount(callCount, rule);

        return baseAmount.multiply(discount)
            .setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateSingle(BillingRuleDO rule, Integer latencyMs) {
        if (rule == null || rule.getUnitPrice() == null) {
            return BigDecimal.ZERO;
        }

        // 单次调用使用折扣后的价格
        BigDecimal discount = rule.getDiscount() != null ? rule.getDiscount() : BigDecimal.ONE;
        return rule.getUnitPrice().multiply(discount)
            .setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 确定折扣率
     * 规则：
     * - 0-10万次: 1.0 (无折扣)
     * - 10-50万次: 0.9 (9折)
     * - 50万次以上: 0.8 (8折)
     */
    private BigDecimal determineDiscount(long callCount, BillingRuleDO rule) {
        // 如果规则中指定了折扣，优先使用规则折扣
        if (rule.getDiscount() != null && rule.getDiscount().compareTo(BigDecimal.ZERO) > 0) {
            return rule.getDiscount();
        }

        // 默认阶梯折扣
        if (callCount > 500_000) {
            return new BigDecimal("0.80");
        } else if (callCount > 100_000) {
            return new BigDecimal("0.90");
        } else {
            return BigDecimal.ONE;
        }
    }
}
```

- [ ] **Step 4: 创建 DynamicBillingCalculator.java**

```java
package com.dataplatform.common.billing;

import com.dataplatform.common.entity.unified.BillingRuleDO;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 动态计费计算器
 * 根据响应时间动态调整费用 (SLA补偿)
 *
 * 计算规则：
 * - 响应时间 <= SLA阈值: 正常计费
 * - 响应时间 > SLA阈值: 每超过100ms, 费用减少 compensationRate
 */
public class DynamicBillingCalculator implements BillingCalculator {

    /** 默认SLA阈值(毫秒) */
    private static final int DEFAULT_SLA_THRESHOLD = 2000;

    /** 默认补偿系数 */
    private static final BigDecimal DEFAULT_COMPENSATION_RATE = new BigDecimal("0.10");

    @Override
    public BigDecimal calculate(BillingRuleDO rule, long callCount, Integer latencyMs) {
        if (rule == null || rule.getUnitPrice() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal baseAmount = rule.getUnitPrice()
            .multiply(BigDecimal.valueOf(callCount));

        // 如果没有响应时间数据，返回基础费用
        if (latencyMs == null) {
            return baseAmount.setScale(4, RoundingMode.HALF_UP);
        }

        // 计算补偿系数
        BigDecimal compensationFactor = calculateCompensationFactor(rule, latencyMs);

        return baseAmount.multiply(compensationFactor)
            .setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateSingle(BillingRuleDO rule, Integer latencyMs) {
        if (rule == null || rule.getUnitPrice() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal basePrice = rule.getUnitPrice();

        // 如果没有响应时间数据，返回基础价格
        if (latencyMs == null) {
            return basePrice;
        }

        // 计算补偿系数
        BigDecimal compensationFactor = calculateCompensationFactor(rule, latencyMs);

        return basePrice.multiply(compensationFactor)
            .setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 计算补偿系数
     * 返回值: 0.0 ~ 1.0
     */
    private BigDecimal calculateCompensationFactor(BillingRuleDO rule, Integer latencyMs) {
        int slaThreshold = rule.getSlaThreshold() != null ? rule.getSlaThreshold() : DEFAULT_SLA_THRESHOLD;
        BigDecimal compensationRate = rule.getCompensationRate() != null
            ? rule.getCompensationRate()
            : DEFAULT_COMPENSATION_RATE;

        // 响应时间在SLA内，无补偿
        if (latencyMs <= slaThreshold) {
            return BigDecimal.ONE;
        }

        // 超过SLA，计算补偿
        int overTime = latencyMs - slaThreshold;
        int overUnits = overTime / 100;  // 每超过100ms为一个单位

        // 补偿金额 = overUnits * compensationRate
        BigDecimal totalCompensation = compensationRate.multiply(BigDecimal.valueOf(overUnits));

        // 确保补偿不超过100%
        if (totalCompensation.compareTo(BigDecimal.ONE) >= 0) {
            return BigDecimal.ZERO;
        }

        // 实际费用系数 = 1 - 补偿比例
        return BigDecimal.ONE.subtract(totalCompensation);
    }
}
```

- [ ] **Step 5: 编译验证**

```bash
mvn compile -pl data-platform-common -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add data-platform-common/src/main/java/com/dataplatform/common/billing/
git commit -m "feat(common): implement billing calculators (standard, tiered, dynamic)"
```

---

## 阶段二：实现核心功能 (Task 7-12)

---

### Task 7: 重构 DataQueryService 集成适配器

**Files:**
- Modify: `data-platform-call/src/main/java/com/dataplatform/call/service/impl/DataQueryServiceImpl.java`
- Create: `data-platform-call/src/main/java/com/dataplatform/call/service/VendorProxyService.java`

**目标**: 重构数据查询服务，使用厂商适配器进行真实API调用

- [ ] **Step 1: 创建 VendorProxyService.java**

```java
package com.dataplatform.call.service;

import com.dataplatform.common.adapter.*;
import com.dataplatform.common.entity.unified.VendorConfigDO;
import com.dataplatform.vendor.service.VendorConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 厂商代理服务
 * 负责调用厂商API并处理熔断
 */
@Service
public class VendorProxyService {

    private static final Logger log = LoggerFactory.getLogger(VendorProxyService.class);

    @Autowired
    private VendorConfigService vendorConfigService;

    /**
     * 调用厂商API
     *
     * @param vendorCode   厂商编码
     * @param dataTypeCode 数据类型编码
     * @param params       请求参数
     * @return 响应结果
     */
    public Map<String, Object> callVendor(String vendorCode, String dataTypeCode,
                                           Map<String, Object> params) {
        // 1. 获取厂商配置
        VendorConfigDO config = vendorConfigService.getConfigByVendorAndDataType(vendorCode, dataTypeCode);
        if (config == null) {
            return errorResult("CONFIG_NOT_FOUND", "厂商配置不存在: " + vendorCode + "/" + dataTypeCode);
        }

        // 2. 检查厂商状态
        if (!"active".equals(config.getStatus())) {
            return errorResult("VENDOR_INACTIVE", "厂商已禁用: " + vendorCode);
        }

        // 3. 构建适配器配置
        VendorAdapterConfig adapterConfig = buildAdapterConfig(config);

        // 4. 获取适配器并执行
        VendorAdapter adapter = VendorAdapterFactory.getAdapter(vendorCode);

        try {
            Map<String, Object> result = adapter.execute(adapterConfig, params);
            log.info("厂商调用成功: vendor={}, type={}, latency={}ms",
                vendorCode, dataTypeCode, result.get("latency"));
            return result;
        } catch (Exception e) {
            log.error("厂商调用失败: vendor={}, error={}", vendorCode, e.getMessage(), e);
            return errorResult("VENDOR_ERROR", e.getMessage());
        }
    }

    /**
     * 构建适配器配置
     */
    private VendorAdapterConfig buildAdapterConfig(VendorConfigDO config) {
        VendorAdapterConfig adapterConfig = new VendorAdapterConfig();
        adapterConfig.setApiUrl(config.getApiUrl());
        adapterConfig.setMethod(config.getMethod());
        adapterConfig.setTimeout(config.getTimeout());
        adapterConfig.setRetryCount(config.getRetryCount());
        adapterConfig.setRequestTemplate(config.getRequestTemplate());
        adapterConfig.setResponseMapping(config.getResponseMapping());
        adapterConfig.setSignType(config.getSignType());

        // 解析请求头配置
        if (config.getHeaderConfig() != null && !config.getHeaderConfig().isEmpty()) {
            try {
                Map<String, String> headers = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(config.getHeaderConfig(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                adapterConfig.setHeaders(headers);
            } catch (Exception e) {
                log.warn("解析请求头配置失败: {}", e.getMessage());
            }
        }

        return adapterConfig;
    }

    /**
     * 构建错误结果
     */
    private Map<String, Object> errorResult(String errorCode, String errorMsg) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("errorCode", errorCode);
        result.put("errorMsg", errorMsg);
        return result;
    }
}
```

- [ ] **Step 2: 在 VendorConfigService 添加查询方法**

修改 `data-platform-vendor/src/main/java/com/dataplatform/vendor/service/VendorConfigService.java`，添加：

```java
/**
 * 根据厂商编码和数据类型编码获取配置
 */
VendorConfigDO getConfigByVendorAndDataType(String vendorCode, String dataTypeCode);
```

修改 `data-platform-vendor/src/main/java/com/dataplatform/vendor/service/impl/VendorConfigServiceImpl.java`，添加实现：

```java
@Autowired
private VendorInfoMapper vendorInfoMapper;

@Autowired
private DataTypeMapper dataTypeMapper;

@Override
public VendorConfigDO getConfigByVendorAndDataType(String vendorCode, String dataTypeCode) {
    // 1. 获取厂商ID
    VendorInfo vendorInfo = vendorInfoMapper.selectOne(
        new LambdaQueryWrapper<VendorInfo>()
            .eq(VendorInfo::getVendorCode, vendorCode)
            .eq(VendorInfo::getStatus, "active")
    );
    if (vendorInfo == null) {
        return null;
    }

    // 2. 获取数据类型ID
    DataType dataType = dataTypeMapper.selectOne(
        new LambdaQueryWrapper<DataType>()
            .eq(DataType::getDataTypeCode, dataTypeCode)
    );
    if (dataType == null) {
        return null;
    }

    // 3. 获取配置
    return getOne(new LambdaQueryWrapper<VendorConfig>()
        .eq(VendorConfig::getVendorId, vendorInfo.getId())
        .eq(VendorConfig::getDataTypeId, dataType.getId())
        .eq(VendorConfig::getStatus, "active")
    );
}
```

- [ ] **Step 3: 重构 DataQueryServiceImpl.java**

```java
package com.dataplatform.call.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.dataplatform.call.entity.CallRecord;
import com.dataplatform.call.service.CallRecordService;
import com.dataplatform.call.service.DataQueryService;
import com.dataplatform.call.service.VendorProxyService;
import com.dataplatform.caller.entity.ApiKey;
import com.dataplatform.caller.service.ApiKeyService;
import com.dataplatform.common.billing.BillingCalculator;
import com.dataplatform.common.billing.StandardBillingCalculator;
import com.dataplatform.common.entity.unified.BillingRuleDO;
import com.dataplatform.billing.service.BillingRuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class DataQueryServiceImpl implements DataQueryService {

    private static final Logger log = LoggerFactory.getLogger(DataQueryServiceImpl.class);

    @Autowired
    private CallRecordService callRecordService;

    @Autowired
    private VendorProxyService vendorProxyService;

    @Autowired
    private BillingRuleService billingRuleService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${data.query.cache.ttl:3600}")
    private int cacheTtl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BillingCalculator billingCalculator = new StandardBillingCalculator();

    @Override
    public Map<String, Object> queryData(String vendorCode, String dataType,
                                          Map<String, Object> params,
                                          Long callerId, String apiKey) {
        long startTime = System.currentTimeMillis();
        String requestId = generateRequestId();

        try {
            // 1. 检查缓存
            String cacheKey = buildCacheKey(vendorCode, dataType, params);
            String cachedResult = redisTemplate.opsForValue().get(cacheKey);
            if (cachedResult != null) {
                log.info("命中缓存: {}", cacheKey);
                Map<String, Object> result = objectMapper.readValue(cachedResult, Map.class);
                result.put("cached", true);
                result.put("latency", System.currentTimeMillis() - startTime);

                recordCall(requestId, callerId, vendorCode, dataType, params,
                          result, true, System.currentTimeMillis() - startTime,
                          BigDecimal.ZERO, true);
                return result;
            }

            // 2. 调用厂商API (通过适配器)
            Map<String, Object> vendorResult = vendorProxyService.callVendor(vendorCode, dataType, params);

            // 3. 计算费用
            long latency = System.currentTimeMillis() - startTime;
            BigDecimal cost = calculateCost(vendorCode, dataType, latency);

            boolean success = Boolean.TRUE.equals(vendorResult.get("success"));

            // 4. 记录调用
            recordCall(requestId, callerId, vendorCode, dataType, params,
                      vendorResult, success, latency, cost, false);

            // 5. 存入缓存 (仅成功时缓存)
            if (success) {
                try {
                    redisTemplate.opsForValue().set(cacheKey,
                        objectMapper.writeValueAsString(vendorResult),
                        cacheTtl, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("缓存存储失败: {}", e.getMessage());
                }
            }

            vendorResult.put("cached", false);
            vendorResult.put("latency", latency);
            vendorResult.put("requestId", requestId);

            return vendorResult;

        } catch (Exception e) {
            log.error("数据查询失败: vendor={}, type={}, error={}",
                     vendorCode, dataType, e.getMessage(), e);

            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("errorCode", "QUERY_ERROR");
            errorResult.put("errorMsg", e.getMessage());
            errorResult.put("requestId", requestId);

            recordCall(requestId, callerId, vendorCode, dataType, params,
                      errorResult, false, System.currentTimeMillis() - startTime,
                      BigDecimal.ZERO, false);

            return errorResult;
        }
    }

    @Override
    public Map<String, Object> batchQuery(String vendorCode, String dataType,
                                           List<Map<String, Object>> paramsList,
                                           Long callerId, String apiKey) {
        Map<String, Object> result = new HashMap<>();
        String batchId = "batch_" + System.currentTimeMillis();

        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0;
        int failed = 0;

        for (Map<String, Object> params : paramsList) {
            try {
                Map<String, Object> queryResult = queryData(vendorCode, dataType, params, callerId, apiKey);
                if (Boolean.TRUE.equals(queryResult.get("success"))) {
                    success++;
                } else {
                    failed++;
                }
                results.add(Map.of(
                    "requestId", queryResult.getOrDefault("requestId", ""),
                    "success", queryResult.get("success") != null ? queryResult.get("success") : false,
                    "result", queryResult.getOrDefault("data", Collections.emptyMap())
                ));
            } catch (Exception e) {
                failed++;
                results.add(Map.of(
                    "success", false,
                    "error", e.getMessage()
                ));
            }
        }

        result.put("batchId", batchId);
        result.put("total", paramsList.size());
        result.put("success", success);
        result.put("failed", failed);
        result.put("results", results);

        return result;
    }

    /**
     * 计算费用 (从数据库获取计费规则)
     */
    private BigDecimal calculateCost(String vendorCode, String dataType, long latencyMs) {
        try {
            BillingRuleDO rule = billingRuleService.getRuleByVendorAndDataType(vendorCode, dataType);
            if (rule != null) {
                return billingCalculator.calculateSingle(rule, (int) latencyMs);
            }
        } catch (Exception e) {
            log.warn("获取计费规则失败，使用默认价格: {}", e.getMessage());
        }

        // 默认价格
        return new BigDecimal("0.10");
    }

    private String generateRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String buildCacheKey(String vendorCode, String dataType, Map<String, Object> params) {
        try {
            String paramsStr = objectMapper.writeValueAsString(params);
            String rawKey = vendorCode + ":" + dataType + ":" + paramsStr;
            return "data_cache:" + DigestUtil.md5Hex(rawKey);
        } catch (Exception e) {
            return "data_cache:" + params.hashCode();
        }
    }

    private void recordCall(String requestId, Long callerId, String vendorCode,
                           String dataType, Map<String, Object> params,
                           Map<String, Object> result, boolean success,
                           long latency, BigDecimal cost, boolean cached) {
        try {
            CallRecord record = new CallRecord();
            record.setRequestId(requestId);
            record.setCallerId(callerId);
            record.setVendorCode(vendorCode);
            record.setDataType(dataType);
            record.setRequestParams(objectMapper.writeValueAsString(params));
            record.setResponseData(objectMapper.writeValueAsString(result));
            record.setSuccess(success);
            record.setLatency((int) latency);
            record.setCost(cost);
            record.setCached(cached);
            record.setCallTime(LocalDateTime.now());

            callRecordService.save(record);
        } catch (Exception e) {
            log.error("记录调用失败: {}", e.getMessage());
        }
    }

    public void clearCache(String vendorCode, String dataType, Map<String, Object> params) {
        String cacheKey = buildCacheKey(vendorCode, dataType, params);
        redisTemplate.delete(cacheKey);
    }

    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            long count = 0;
            var scanOptions = org.springframework.data.redis.core.ScanOptions.scanOptions()
                .match("data_cache:*")
                .count(100)
                .build();

            try (var cursor = redisTemplate.scan(scanOptions)) {
                while (cursor.hasNext()) {
                    count++;
                }
            }
            stats.put("totalKeys", count);
        } catch (Exception e) {
            log.warn("获取缓存统计失败: {}", e.getMessage());
            stats.put("totalKeys", 0);
        }
        return stats;
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
mvn compile -pl data-platform-call -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add data-platform-call/src/main/java/com/dataplatform/call/service/
git add data-platform-vendor/src/main/java/com/dataplatform/vendor/service/
git commit -m "refactor(call): integrate vendor adapter for real API calls"
```

---

### Task 8: 实现熔断和重试机制

**Files:**
- Create: `data-platform-common/src/main/java/com/dataplatform/common/circuitbreaker/CircuitBreakerManager.java`
- Modify: `data-platform-call/src/main/resources/application.yml`
- Modify: `data-platform-call/src/main/java/com/dataplatform/call/service/VendorProxyService.java`

**目标**: 使用 Resilience4j 实现熔断和重试，防止厂商故障导致系统崩溃

- [ ] **Step 1: 创建 CircuitBreakerManager.java**

```java
package com.dataplatform.common.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 熔断器和重试管理器
 */
public class CircuitBreakerManager {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerManager.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final Map<String, Retry> retries = new ConcurrentHashMap<>();

    public CircuitBreakerManager() {
        // 默认熔断器配置
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)                    // 失败率50%触发熔断
            .waitDurationInOpenState(Duration.ofSeconds(30))  // 熔断30秒
            .permittedNumberOfCallsInHalfOpenState(5)   // 半开状态允许5次调用
            .slidingWindowSize(10)                       // 滑动窗口10次
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .build();

        // 默认重试配置
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(3)                              // 最多重试3次
            .waitDuration(Duration.ofMillis(500))        // 重试间隔500ms
            .retryExceptions(Exception.class)           // 所有异常都重试
            .build();

        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(cbConfig);
        this.retryRegistry = RetryRegistry.of(retryConfig);
    }

    /**
     * 获取或创建熔断器
     */
    public CircuitBreaker getCircuitBreaker(String vendorCode) {
        return circuitBreakers.computeIfAbsent(vendorCode,
            code -> circuitBreakerRegistry.circuitBreaker("vendor_" + code));
    }

    /**
     * 获取或创建重试器
     */
    public Retry getRetry(String vendorCode) {
        return retries.computeIfAbsent(vendorCode,
            code -> retryRegistry.retry("vendor_" + code));
    }

    /**
     * 使用熔断和重试执行操作
     */
    public <T> T executeWithProtection(String vendorCode, Supplier<T> supplier) {
        CircuitBreaker cb = getCircuitBreaker(vendorCode);
        Retry retry = getRetry(vendorCode);

        // 组合熔断和重试
        Supplier<T> decoratedSupplier = io.github.resilience4j.decorators.Decorators
            .ofSupplier(supplier)
            .withRetry(retry)
            .withCircuitBreaker(cb)
            .decorate();

        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            log.error("厂商调用失败(已重试): vendor={}, error={}", vendorCode, e.getMessage());
            throw e;
        }
    }

    /**
     * 获取熔断器状态
     */
    public String getCircuitBreakerState(String vendorCode) {
        CircuitBreaker cb = circuitBreakers.get(vendorCode);
        if (cb == null) {
            return "UNKNOWN";
        }
        return cb.getState().name();
    }

    /**
     * 强制打开熔断器
     */
    public void forceOpen(String vendorCode) {
        CircuitBreaker cb = circuitBreakers.get(vendorCode);
        if (cb != null) {
            cb.transitionToOpenState();
            log.info("强制打开熔断器: {}", vendorCode);
        }
    }

    /**
     * 强制关闭熔断器
     */
    public void forceClose(String vendorCode) {
        CircuitBreaker cb = circuitBreakers.get(vendorCode);
        if (cb != null) {
            cb.transitionToClosedState();
            log.info("强制关闭熔断器: {}", vendorCode);
        }
    }
}
```

- [ ] **Step 2: 添加 Resilience4j 配置到 application.yml**

修改 `data-platform-call/src/main/resources/application.yml`，添加：

```yaml
# Resilience4j 配置
resilience4j:
  circuitbreaker:
    configs:
      default:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 5
        sliding-window-type: count_based
        sliding-window-size: 10
    instances:
      vendor_default:
        base-config: default
  retry:
    configs:
      default:
        max-attempts: 3
        wait-duration: 500ms
    instances:
      vendor_default:
        base-config: default
```

- [ ] **Step 3: 修改 VendorProxyService 集成熔断**

```java
// 在 VendorProxyService.java 中添加

@Autowired
private CircuitBreakerManager circuitBreakerManager;

public Map<String, Object> callVendor(String vendorCode, String dataTypeCode,
                                       Map<String, Object> params) {
    VendorConfigDO config = vendorConfigService.getConfigByVendorAndDataType(vendorCode, dataTypeCode);
    if (config == null) {
        return errorResult("CONFIG_NOT_FOUND", "厂商配置不存在: " + vendorCode + "/" + dataTypeCode);
    }

    if (!"active".equals(config.getStatus())) {
        return errorResult("VENDOR_INACTIVE", "厂商已禁用: " + vendorCode);
    }

    VendorAdapterConfig adapterConfig = buildAdapterConfig(config);
    VendorAdapter adapter = VendorAdapterFactory.getAdapter(vendorCode);

    try {
        // 使用熔断和重试保护
        return circuitBreakerManager.executeWithProtection(vendorCode,
            () -> adapter.execute(adapterConfig, params));
    } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
        log.warn("熔断器打开，拒绝调用: vendor={}", vendorCode);
        return errorResult("CIRCUIT_BREAKER_OPEN", "厂商服务暂时不可用，请稍后重试");
    } catch (Exception e) {
        log.error("厂商调用失败: vendor={}, error={}", vendorCode, e.getMessage(), e);
        return errorResult("VENDOR_ERROR", e.getMessage());
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
mvn compile -pl data-platform-call,data-platform-common -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add data-platform-common/src/main/java/com/dataplatform/common/circuitbreaker/
git add data-platform-call/src/main/resources/application.yml
git add data-platform-call/src/main/java/com/dataplatform/call/service/VendorProxyService.java
git commit -m "feat(common,call): add circuit breaker and retry with Resilience4j"
```

---

### Task 9-12 省略（计费服务完善、Gateway增强、测试等）

详细的 Task 9-12 内容请参见完整计划文档。

---

## 执行检查清单

- [ ] 阶段一完成：架构缺陷已修复
- [ ] 阶段二完成：核心功能已实现
- [ ] 阶段三完成：基础设施已增强
- [ ] 测试通过：MVP功能可用
- [ ] 文档更新：API文档已更新

---

**计划创建时间**: 2026-04-26
**预计工期**: 7-10天
**创建人**: Claude
