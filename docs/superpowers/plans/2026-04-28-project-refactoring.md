# 项目骨架重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有单层 Maven 模块重构为契约分离架构（api + service 双模块），解决循环依赖和冗余依赖问题，实现分层依赖隔离。

**Architecture:** 采用 Maven 多模块父子聚合结构，每个业务域拆分为 api（契约层 Jar）和 service（业务实现层）两个独立模块。依赖方向：service → api → common，禁止反向依赖。

**Tech Stack:** Maven多模块、Spring Cloud Alibaba、OpenFeign、Spring Boot

---

## 当前问题分析

### 1. 现有模块结构（问题状态）
- 所有业务模块都是单层结构，同时包含 API 契约和业务实现
- `data-platform-interface` 模块错误地依赖了 `data-platform-common`（应只被依赖）
- 存在潜在的循环依赖和冗余依赖

### 2. 重构目标模块结构
```
data-platform (父聚合模块 - pom)
├── data-platform-common (公共模块 - jar)
├── data-platform-api (公共API模块 - jar) [新建]
├── data-platform-gateway (网关 - jar)
├── data-platform-config (配置中心 - jar)
│   ├── data-platform-config-api (契约层 - jar) [新建]
│   └── data-platform-config-service (业务层 - jar) [新建]
├── data-platform-user (用户服务)
│   ├── data-platform-user-api (契约层 - jar) [新建]
│   └── data-platform-user-service (业务层 - jar) [新建]
├── data-platform-tenant (租户服务)
│   ├── data-platform-tenant-api (契约层 - jar) [新建]
│   └── data-platform-tenant-service (业务层 - jar) [新建]
├── data-platform-role (角色服务)
│   ├── data-platform-role-api (契约层 - jar) [新建]
│   └── data-platform-role-service (业务层 - jar) [新建]
├── data-platform-vendor (厂商服务)
│   ├── data-platform-vendor-api (契约层 - jar) [新建]
│   └── data-platform-vendor-service (业务层 - jar) [已存在]
├── data-platform-billing (计费服务)
│   ├── data-platform-billing-api (契约层 - jar) [新建]
│   └── data-platform-billing-service (业务层 - jar) [新建]
├── data-platform-call (调用服务)
│   ├── data-platform-call-api (契约层 - jar) [新建]
│   └── data-platform-call-service (业务层 - jar) [新建]
├── data-platform-caller (调用方服务)
│   ├── data-platform-caller-api (契约层 - jar) [新建]
│   └── data-platform-caller-service (业务层 - jar) [新建]
├── data-platform-datatype (数据类型服务)
│   ├── data-platform-datatype-api (契约层 - jar) [新建]
│   └── data-platform-datatype-service (业务层 - jar) [新建]
├── data-platform-interface (接口管理服务)
│   ├── data-platform-interface-api (契约层 - jar) [新建]
│   └── data-platform-interface-service (业务层 - jar) [新建]
├── data-platform-log (日志服务)
│   ├── data-platform-log-api (契约层 - jar) [新建]
│   └── data-platform-log-service (业务层 - jar) [新建]
├── data-platform-monitor (监控服务)
│   ├── data-platform-monitor-api (契约层 - jar) [新建]
│   └── data-platform-monitor-service (业务层 - jar) [新建]
├── data-platform-quality (质量服务)
│   ├── data-platform-quality-api (契约层 - jar) [新建]
│   └── data-platform-quality-service (业务层 - jar) [新建]
├── data-platform-trace (链路追踪服务)
│   ├── data-platform-trace-api (契约层 - jar) [新建]
│   └── data-platform-trace-service (业务层 - jar) [新建]
├── data-platform-graylog (日志收集服务)
│   ├── data-platform-graylog-api (契约层 - jar) [新建]
│   └── data-platform-graylog-service (业务层 - jar) [新建]
└── data-platform-test (测试服务)
    ├── data-platform-test-api (契约层 - jar) [新建]
    └── data-platform-test-service (业务层 - jar) [新建]
```

---

## POM 依赖管理规则

### 依赖层级规范
1. **common 层**: 公共工具、基础实体、异常类、通用枚举 —— 无业务依赖
2. **api 层**: Feign 接口、DTO/VO/BO、统一返回结果 —— 只依赖 common
3. **service 层**: 业务实现、数据库访问、Web层 —— 依赖 api + common + 基础设施

### 禁止的依赖模式
- ❌ service 依赖同模块的 api（同一服务内部调用）
- ❌ api 依赖 service
- ❌ 循环依赖（A→B→A）
- ❌ 冗余依赖（同一依赖多次声明）

---

## 重构任务清单

### 阶段一：创建基础模块结构

#### Task 1: 创建 data-platform-api 公共契约模块

**Files:**
- Create: `data-platform-api/pom.xml`
- Create: `data-platform-api/src/main/java/com/dataplatform/api/Result.java`
- Create: `data-platform-api/src/main/java/com/dataplatform/api/PageResult.java`
- Create: `data-platform-api/src/main/java/com/dataplatform/api/exception/BusinessException.java`
- Create: `data-platform-api/src/main/java/com/dataplatform/api/exception/ErrorCode.java`

- [ ] **Step 1: 创建 data-platform-api 目录结构和 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.dataplatform</groupId>
        <artifactId>data-platform</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>data-platform-api</artifactId>
    <packaging>jar</packaging>
    <name>Data Platform API</name>
    <description>公共API契约模块 - 统一返回结果、异常定义、基础DTO</description>

    <dependencies>
        <!-- Spring Web (用于 @RestController 注解) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <scope>provided</scope>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
            <scope>provided</scope>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Jackson -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- Spring Cloud OpenFeign -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 创建统一返回结果类 Result.java**

```java
package com.dataplatform.api;

import lombok.Data;
import java.io.Serializable;

@Data
public class Result<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer code;
    private String msg;
    private T data;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg("success");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error(String msg) {
        return error(500, msg);
    }

    public static <T> Result<T> error(Integer code, String msg) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        return result;
    }
}
```

- [ ] **Step 3: 创建分页结果类 PageResult.java**

```java
package com.dataplatform.api;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class PageResult<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<T> list;
    private Long total;
    private Integer pageNum;
    private Integer pageSize;
    private Integer totalPages;

    public static <T> PageResult<T> of(List<T> list, Long total, Integer pageNum, Integer pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setList(list);
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setTotalPages((int) Math.ceil((double) total / pageSize));
        return result;
    }
}
```

- [ ] **Step 4: 创建业务异常类 BusinessException.java**

```java
package com.dataplatform.api.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final Integer code;
    private final String msg;

    public BusinessException(String msg) {
        this(500, msg);
    }

    public BusinessException(Integer code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }

    public BusinessException(ErrorCode errorCode) {
        this(errorCode.getCode(), errorCode.getMsg());
    }
}
```

- [ ] **Step 5: 创建错误码枚举 ErrorCode.java**

```java
package com.dataplatform.api.exception;

public enum ErrorCode {
    // 通用错误码
    SUCCESS(200, "success"),
    SYSTEM_ERROR(500, "系统异常"),
    PARAM_ERROR(400, "参数错误"),
    NOT_FOUND(404, "资源不存在"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问");

    private final Integer code;
    private final String msg;

    ErrorCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public Integer getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
```

- [ ] **Step 6: 更新父 pom.xml 添加 data-platform-api 模块**

修改根 pom.xml 的 `<modules>` 部分，添加：
```xml
<module>data-platform-api</module>
```

- [ ] **Step 7: 验证编译**

```bash
cd /Users/lixd/IdeaProjects/Git/ClaudeCodeProject/data-manager-hub
mvn clean compile -pl data-platform-api -am
```

预期：BUILD SUCCESS

- [ ] **Step 8: 提交**

```bash
git add data-platform-api/ pom.xml
git commit -m "refactor: 创建 data-platform-api 公共契约模块

- 新增统一返回结果类 Result
- 新增分页结果类 PageResult
- 新增业务异常类 BusinessException
- 新增错误码枚举 ErrorCode
- 更新父 pom.xml 添加模块聚合"
```

---

#### Task 2: 重构 data-platform-common 公共模块

**Files:**
- Modify: `data-platform-common/pom.xml`
- Modify: `data-platform-common/src/main/java/com/dataplatform/common/result/Result.java`
- Modify: `data-platform-common/src/main/java/com/dataplatform/common/result/PageResult.java`
- Modify: `data-platform-common/src/main/java/com/dataplatform/common/exception/BusinessException.java`
- Modify: `data-platform-common/src/main/java/com/dataplatform/common/enums/ErrorCode.java`

- [ ] **Step 1: 修改 data-platform-common/pom.xml，移除业务依赖**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.dataplatform</groupId>
        <artifactId>data-platform</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>data-platform-common</artifactId>
    <packaging>jar</packaging>
    <name>Data Platform Common</name>
    <description>公共模块 - 通用工具类、基础实体、适配器</description>

    <dependencies>
        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Hutool -->
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
            <scope>provided</scope>
        </dependency>

        <!-- Jackson -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <scope>provided</scope>
        </dependency>

        <!-- Apache Commons -->
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>commons-io</groupId>
            <artifactId>commons-io</artifactId>
            <version>2.15.1</version>
            <scope>provided</scope>
        </dependency>

        <!-- Spring Context (用于动态加载) -->
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <scope>provided</scope>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 验证编译**

```bash
mvn clean compile -pl data-platform-common -am
```

预期：BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add data-platform-common/pom.xml
git commit -m "refactor: 重构 data-platform-common，移除业务依赖

- 移除 spring-boot-starter-web (业务依赖)
- 移除 spring-boot-starter-validation (业务依赖)
- 移除 mybatis-plus-spring-boot3-starter (数据库依赖)
- 移除 okhttp、resilience4j 等中间件依赖
- 保留纯工具类和通用实体"
```

---

### 阶段二：重构业务服务模块（以 data-platform-vendor 为例）

#### Task 3: 重构 data-platform-vendor 服务为 api + service 双模块

**Files:**
- Create: `data-platform-vendor/data-platform-vendor-api/pom.xml`
- Create: `data-platform-vendor/data-platform-vendor-api/src/main/java/com/dataplatform/vendor/api/VendorApi.java`
- Create: `data-platform-vendor/data-platform-vendor-api/src/main/java/com/dataplatform/vendor/api/dto/VendorConfigDTO.java`
- Create: `data-platform-vendor/data-platform-vendor-api/src/main/java/com/dataplatform/vendor/api/dto/VendorDTO.java`
- Modify: `data-platform-vendor/data-platform-vendor-service/pom.xml`
- Modify: `data-platform-vendor/pom.xml`

- [ ] **Step 1: 创建 data-platform-vendor-api 目录和 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.dataplatform</groupId>
        <artifactId>data-platform-vendor</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>data-platform-vendor-api</artifactId>
    <packaging>jar</packaging>
    <name>Data Platform Vendor API</name>
    <description>厂商管理服务 - API契约层</description>

    <dependencies>
        <!-- 依赖公共API模块 -->
        <dependency>
            <groupId>com.dataplatform</groupId>
            <artifactId>data-platform-api</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- 依赖公共工具模块 -->
        <dependency>
            <groupId>com.dataplatform</groupId>
            <artifactId>data-platform-common</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
            <scope>provided</scope>
        </dependency>

        <!-- Spring Cloud OpenFeign -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 创建 vendor-api 模块的父 pom 聚合**

修改 `data-platform-vendor/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.dataplatform</groupId>
        <artifactId>data-platform</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>data-platform-vendor</artifactId>
    <packaging>pom</packaging>
    <name>Data Platform Vendor</name>
    <description>厂商管理服务 - 父聚合模块</description>

    <modules>
        <module>data-platform-vendor-api</module>
        <module>data-platform-vendor-service</module>
    </modules>
</project>
```

- [ ] **Step 3: 创建 Feign 远程调用接口 VendorApi.java**

```java
package com.dataplatform.vendor.api;

import com.dataplatform.api.Result;
import com.dataplatform.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.vendor.api.dto.VendorDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "data-platform-vendor-service", path = "/vendor")
public interface VendorApi {

    @GetMapping("/{id}")
    Result<VendorDTO> getVendor(@PathVariable("id") Long id);

    @PostMapping
    Result<Long> createVendor(@RequestBody VendorDTO vendorDTO);

    @PutMapping("/{id}")
    Result<Void> updateVendor(@PathVariable("id") Long id, @RequestBody VendorDTO vendorDTO);

    @DeleteMapping("/{id}")
    Result<Void> deleteVendor(@PathVariable("id") Long id);

    @GetMapping("/config/{vendorId}")
    Result<VendorConfigDTO> getVendorConfig(@PathVariable("vendorId") Long vendorId);

    @PutMapping("/config/{vendorId}")
    Result<Void> updateVendorConfig(@PathVariable("vendorId") Long vendorId, @RequestBody VendorConfigDTO configDTO);
}
```

- [ ] **Step 4: 创建 DTO 类**

VendorDTO.java:
```java
package com.dataplatform.vendor.api.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class VendorDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "厂商名称不能为空")
    private String vendorName;

    private String vendorCode;

    @NotNull(message = "厂商类型不能为空")
    private Integer vendorType;

    private Integer status;

    private String description;

    private String contactPerson;

    private String contactPhone;

    private String contactEmail;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
```

VendorConfigDTO.java:
```java
package com.dataplatform.vendor.api.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class VendorConfigDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long vendorId;

    private String apiBaseUrl;

    private String apiKey;

    private String apiSecret;

    private BigDecimal maxCallLimit;

    private BigDecimal unitPrice;

    private Integer timeout;

    private Integer retryCount;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
```

- [ ] **Step 5: 验证 vendor-api 编译**

```bash
mvn clean compile -pl data-platform-vendor/data-platform-vendor-api -am
```

预期：BUILD SUCCESS

- [ ] **Step 6: 修改 vendor-service 的 pom.xml，添加 api 依赖**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.dataplatform</groupId>
        <artifactId>data-platform-vendor</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>data-platform-vendor-service</artifactId>
    <packaging>jar</packaging>
    <name>Data Platform Vendor Service</name>
    <description>厂商管理服务 - 业务实现层</description>

    <dependencies>
        <!-- 依赖契约模块 -->
        <dependency>
            <groupId>com.dataplatform</groupId>
            <artifactId>data-platform-vendor-api</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- 依赖公共模块 -->
        <dependency>
            <groupId>com.dataplatform</groupId>
            <artifactId>data-platform-common</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Nacos -->
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
        </dependency>

        <!-- MyBatis-Plus -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>

        <!-- PostgreSQL -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
        </dependency>

        <!-- Redisson -->
        <dependency>
            <groupId>org.redisson</groupId>
            <artifactId>redisson-spring-boot-starter</artifactId>
        </dependency>

        <!-- Kafka -->
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <parameters>true</parameters>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>3.4.0</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                        <configuration>
                            <classifier>exec</classifier>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 7: 验证 vendor-service 编译**

```bash
mvn clean compile -pl data-platform-vendor/data-platform-vendor-service -am
```

预期：BUILD SUCCESS

- [ ] **Step 8: 提交**

```bash
git add data-platform-vendor/
git commit -m "refactor: 重构 data-platform-vendor 为 api + service 双模块

- 新增 data-platform-vendor-api 契约层模块
- 契约层包含 Feign 接口和 DTO
- vendor-service 依赖 vendor-api 实现业务
- 更新 vendor 父 pom 为聚合模块"
```

---

### 阶段三：批量重构其他业务模块

#### Task 4: 批量重构其他业务服务模块

按照 Task 3 的模式，依次重构以下模块：
- data-platform-billing (计费服务)
- data-platform-call (调用服务)
- data-platform-caller (调用方服务)
- data-platform-datatype (数据类型服务)
- data-platform-interface (接口管理服务)
- data-platform-log (日志服务)
- data-platform-monitor (监控服务)
- data-platform-quality (质量服务)
- data-platform-role (角色服务)
- data-platform-tenant (租户服务)
- data-platform-trace (链路追踪服务)
- data-platform-graylog (日志收集服务)
- data-platform-test (测试服务)

**重复性操作（每个模块）：**
1. 创建 `xxx-api/pom.xml` - 依赖 data-platform-api + data-platform-common
2. 创建 Feign 接口和 DTO
3. 修改父模块 pom.xml 为聚合模块
4. 修改 `xxx-service/pom.xml` 移除原直接依赖 common 的部分
5. 编译验证

- [ ] **Step 1: 批量创建各模块的 api 子模块**

使用脚本或手动依次创建以下模块结构：

```
data-platform-billing/
├── data-platform-billing-api/
│   ├── pom.xml
│   └── src/main/java/com/dataplatform/billing/api/
├── data-platform-billing-service/
│   └── (原 src 内容移动至此)
├── pom.xml (改为聚合)
```

- [ ] **Step 2: 批量修改各模块的 service pom.xml**

每个 service 模块的 pom.xml 修改：
- 移除 `<parent>` 中的 `<artifactId>data-platform</artifactId>` 
- 改为 `<parent>` 指向各自的父模块
- 添加对各自 api 模块的依赖
- 保留对 data-platform-common 和 data-platform-api 的依赖

- [ ] **Step 3: 批量验证编译**

```bash
# 依次验证每个模块
mvn clean compile -pl data-platform-billing/data-platform-billing-api -am
mvn clean compile -pl data-platform-billing/data-platform-billing-service -am
# ... 重复其他模块
```

- [ ] **Step 4: 批量提交**

每完成一个模块的重构即提交：
```bash
git add data-platform-xxx/
git commit -m "refactor: 重构 data-platform-xxx 为 api + service 双模块"
```

---

### 阶段四：修复 data-platform-interface 依赖问题

#### Task 5: 修复 data-platform-interface 模块的依赖问题

**问题分析：**
- 当前 `data-platform-interface` 依赖了 `data-platform-common`
- 这是冗余依赖，interface 应该是契约层，只需要依赖 `data-platform-api`

**Files:**
- Modify: `data-platform-interface/pom.xml`

- [ ] **Step 1: 修改 data-platform-interface/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.dataplatform</groupId>
        <artifactId>data-platform</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>data-platform-interface</artifactId>
    <packaging>jar</packaging>
    <description>接口管理模块 - 契约层</description>

    <dependencies>
        <!-- 依赖公共API模块 -->
        <dependency>
            <groupId>com.dataplatform</groupId>
            <artifactId>data-platform-api</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- 依赖公共工具模块 -->
        <dependency>
            <groupId>com.dataplatform</groupId>
            <artifactId>data-platform-common</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Spring Cloud OpenFeign -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 验证编译**

```bash
mvn clean compile -pl data-platform-interface -am
```

- [ ] **Step 3: 提交**

---

### 阶段五：清理和验证

#### Task 6: 清理旧模块，验证无循环依赖

- [ ] **Step 1: 检查循环依赖**

```bash
mvn dependency:tree -pl data-platform-vendor/data-platform-vendor-service | grep -E "(data-platform-common|data-platform-api)"
```

- [ ] **Step 2: 全量编译测试**

```bash
mvn clean compile -DskipTests
```

- [ ] **Step 3: 提交最终版本**

---

## 总结

本计划包含以下关键任务：

1. **Task 1**: 创建 data-platform-api 公共契约模块
2. **Task 2**: 重构 data-platform-common 公共模块，移除业务依赖
3. **Task 3**: 重构 data-platform-vendor 为 api + service 双模块（示例）
4. **Task 4**: 批量重构其他业务模块
5. **Task 5**: 修复 data-platform-interface 依赖问题
6. **Task 6**: 清理和验证无循环依赖

**预期成果：**
- 所有业务模块都遵循 api + service 双模块结构
- 依赖关系清晰：service → api → common → (无)
- 无循环依赖、无冗余依赖
- 支持 OpenFeign 声明式远程调用