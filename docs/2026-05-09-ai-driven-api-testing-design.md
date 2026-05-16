# AI 驱动 API 业务链路测试 — 设计方案

**项目**: 数据管理平台 (data-manager-hub)
**文档版本**: v1.1
**创建日期**: 2026-05-09
**状态**: L2 试点已完成 ✅

---

## 1. 核心愿景

利用 AI 的全代码库感知能力，跳过不稳定的 UI 模拟点击，通过扫描前后端源码，自动生成、执行并维护一套**具有业务上下文**的 API 测试流。

**核心区别**：不是孤立地测单个接口，而是模拟"真实人类业务链路"。

---

## 2. 核心测试逻辑 (Business Flow)

### 2.1 上下文关联

AI 必须识别接口间的依赖关系。例如：从 `POST /user` 的响应中提取 `userId`，自动填充到后续 `PUT /user/{id}` 的请求中。

### 2.2 数据自动建模

AI 通过分析后端 Java 实体类（Entity/DTO）和校验注解（`@NotBlank`、`@NotNull`、`@Size` 等），使用 Faker 库生成符合业务规则的真实测试数据。

### 2.3 全闭环验证

不仅验证状态码，还要验证数据在"增删改查"全流程后的最终一致性：

```
创建 → 查询确认 → 修改 → 查询验证 → 删除 → 查询确认已删除
```

---

## 3. API 依赖图扫描策略

### 3.1 四层信息源

| 扫描层 | 能提取的信息 |
|--------|-------------|
| **前端 `src/api/`** | 所有 API 调用函数、URL、参数类型 |
| **前端 Vue 组件** | API 调用时序（`onMounted` → `@click` → `watch`） |
| **后端 Controller** | 接口签名、`@RequestBody` 类型、请求方式 |
| **后端 Entity/DTO** | 字段定义、校验注解、外键关系 |

### 3.2 核心洞察：前端组件即隐式 API 依赖图

前端页面加载时的行为，已经编码了完整的 API 依赖关系。例如：

```
用户打开"接口配置"页面
  → GET /api/interfaces?page=1&size=20        (加载接口列表)
  → GET /api/vendors                          (加载厂商下拉框)
  → GET /api/data-types                       (加载数据类型下拉框)
用户点击"配置"按钮
  → GET /api/interfaces/{id}/config           (加载该接口的当前配置)
  → GET /api/callers                          (加载调用方列表)
用户填写表单，点保存
  → POST /api/interface-config                (目标接口)
```

**前端代码中这些调用顺序已经写死**——`onMounted` 里调什么、按钮 `@click` 里调什么、弹窗 `watch` 里调什么。因此扫描前端组件就可以还原出业务链路的依赖关系，无需担心"数据库外键约束导致前置数据缺失"的问题。

---

## 4. 故障自修复机制 (Self-Healing)

### 4.1 400 错误处理

自动提取 response body 中的 `message`，匹配对应 `@NotBlank`/`@NotNull` 校验注解，自动修正测试数据后重跑。

### 4.2 500 + NPE 四步排查法

```
Step 1: 检查请求参数
  ├── 测试脚本是否传了这个字段？
  │   ├── 没传  → AI 补传，重跑
  │   └── 传了  → 进入 Step 2

Step 2: 检查后端实体
  ├── Controller 的 @RequestBody DTO 是否有对应字段？
  │   ├── 没有  → 前端传了但后端没收 → 对齐字段定义
  │   └── 有    → 进入 Step 3

Step 3: 检查数据库表
  ├── 数据库表是否有同名字段？
  │   ├── 没有  → DDL 缺失字段 → 生成 ALTER TABLE 建议
  │   └── 有但类型不匹配 → 生成类型修正建议
  │   └── 字段完全一致  → 进入 Step 4

Step 4: 深层排查
  └── MyBatis XML 映射是否正确？
      └── resultMap / column 映射错误 → AI 修正
```

### 4.3 其他 500 错误分类

| 异常类型 | 修复方式 |
|----------|---------|
| `DataIntegrityViolationException` | 外键约束，补前置依赖数据 |
| `ConstraintViolationException` | 唯一键冲突，改测试数据后重试 |
| `NullPointerException`（根因不明） | 标记报告，人工介入 |

### 4.4 重试上限

- 每个测试最多自动修复重试 **3 次**
- 超过 3 次 → 停止，输出诊断报告

---

## 5. 安全边界

### 5.1 自动修复允许范围

| 修复类型 | 允许 | 条件 |
|----------|------|------|
| 测试数据缺少必填字段 | ✅ | 400 + 校验注解匹配 |
| DTO 缺少 `@JsonProperty` 等注解 | ✅ | 仅影响序列化 |
| MyBatis XML 映射遗漏 | ✅ | 仅 column/resultMap 对齐 |
| 补传前置依赖数据 | ✅ | 500 + `DataIntegrityViolationException` |

### 5.2 标记报告、不自动修改的情况

| 场景 | 原因 |
|------|------|
| 需要新增对其他模块源码包的直接引用 | 跨模块架构变更 |
| 需要在 common 模块新增业务实现 | 公共模块不应承载业务逻辑 |
| 需要新增跨服务写操作 | 影响服务边界 |
| 需要在多服务间新增共享实体 | 架构级决策 |
| 需要新增网关到旧模块的专属路由 | 路由策略变更 |
| NPE 根因不明（四步排查法未定位） | 无法确认是测试问题还是后端 bug |

---

## 6. 测试数据清理

### 6.1 问题分析

业务链路测试每个用例走"创建→查询→修改→删除"全流程，每条链路至少产生 3-5 条业务数据，加上 `operation_log` 等自动记录的审计数据。按 10 个模块 × 3 条链路计算，单次全量运行产生 90+ 条测试数据。自愈重试（最多 3 次）时数据量进一步膨胀。

### 6.2 清理策略分层

| 层级 | 机制 | 适用场景 | 可靠性 |
|------|------|---------|--------|
| **L1: 测试自清理** | 链路末尾 DELETE 掉创建的数据 | 单条链路可独立完成 CRUD 闭环 | ⭐⭐⭐ |
| **L2: @AfterEach 兜底** | 记录测试中创建的所有 ID，teardown 时批量 DELETE | 测试中途失败导致 DELETE 没执行到 | ⭐⭐⭐ |
| **L3: 事务回滚** | `@Transactional` + `@Rollback` | 纯查询或不需要持久化的单接口测试 | ⭐⭐⭐⭐⭐ |
| **L4: 测试专用 Schema** | 每次测试前重建 Schema | 集成测试环境 | ⭐⭐⭐⭐ |

### 6.3 推荐方案：L1 + L2 组合

链路测试天然自带清理——每个链路的最后一步就是 DELETE。核心风险在于**测试中途失败**，DELETE 没执行到，产生残留数据。

因此在 `BaseTest` 中新增 ID 追踪机制作为兜底：

```java
// BaseTest 中新增（必须是 static，JUnit5 每个 @Test 创建新实例）
protected static final List<Runnable> cleanupTasks = new ArrayList<>();

/**
 * 便捷方法：注册按 ID 删除
 */
protected void registerDeleteById(String urlTemplate, Long id) {
    cleanupTasks.add(() -> {
        try {
            given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + authToken)
                .when()
                .delete(GATEWAY_URL + "/api/v1" + urlTemplate, id);
        } catch (Exception ignored) {
            // 清理失败不阻塞其他清理
        }
    });
}

@AfterAll
static void cleanupAll() {
    List<Runnable> reversed = new ArrayList<>(cleanupTasks);
    Collections.reverse(reversed);
    reversed.forEach(Runnable::run);
    cleanupTasks.clear();
}
```

**链路测试中的用法**：

```java
@Test
void testCreateVendor() {
    Map<String, Object> data = new HashMap<>();
    data.put("vendorCode", uniqueId("VN"));
    data.put("vendorName", "链路测试厂商");

    Response response = getAuthRequest()
        .body(data)
        .when()
        .post("/vendor");

    verifySuccess(response);
    testVendorId = extractId(response);
    // 注册清理：即使后续步骤失败，@AfterAll 也会删掉这个 vendor
    registerDeleteById("/vendor/{id}", testVendorId);
}
```

### 6.4 不推荐的方案

| 方案 | 不推荐原因 |
|------|-----------|
| `@Transactional` + `@Rollback` | MockMvc 发真实 HTTP 请求，事务边界跨越请求，回滚不可靠 |
| 每次测试前 TRUNCATE 全表 | 会清掉已有的种子数据（如默认租户、admin 用户） |
| 测试专用数据库 | 运维成本高，与当前开发环境割裂 |

### 6.5 AI 自动生成清理代码的规范

AI 生成链路测试时，必须遵循以下清理规范：

1. **每次 `extractId()` 后立即 `registerDeleteById()`** — 创建即注册
2. **注册顺序与创建顺序一致** — `@AfterAll` 逆序执行确保子记录先删
3. **前置依赖数据（如下拉框查询到的已有数据）不注册清理** — 只清理测试自身创建的数据

---

## 7. 技术栈

| 维度 | 方案 | 理由 |
|------|------|------|
| **测试框架** | Spring Boot Test + JUnit 5 + MockMvc | 复用现有 `BaseTest`、`UserContext` 等基础设施，与现有 14 个 API 测试类同体系 |
| **AI 交互入口** | Claude Code / Cursor Chat | 通过 `@Codebase` 或项目索引感知全仓代码 |
| **数据生成** | Faker (Java) 或在 Entity/DTO 扫描后由 AI 直接生成 | 基于校验注解推导合法测试数据 |
| **认证** | 复用 `UserContext.login()` | 已在 BaseTest 中集成 |

---

## 8. 现有基础设施盘点

### 8.1 测试模块 (data-platform-test)

| 维度 | 现状 |
|------|------|
| 测试风格 | Spring Boot Test + JUnit 5 + RestAssured (through Gateway) |
| 基类 | `BaseTest` — 提供 `NON_EXISTENT_ID`、`verifySuccess()`、`extractId()`、`registerDeleteById()` 等通用辅助方法 |
| 认证方式 | Sa-Token，通过 `UserContext.login()` 注入测试用户 |
| 测试组织 | 已按业务域整合：`IAMApiTest`（用户+角色）、`VendorApiTest`（厂商+配置+数据类型）等 14 个 API 测试 |
| 测试模式 | API 测试以单接口为单位；业务链路测试模拟前端完整 CRUD 生命周期 |
| 链式串联 | **6 个业务链路测试** — `@TestMethodOrder` + `@Order` 确保执行顺序，跨接口传递 ID |
| 清理机制 | `static cleanupTasks` + `@AfterAll` 逆序清理（JUnit5 每个 @Test 创建新实例） |

### 8.2 前端 API 层 (src/api/)

```
src/api/
├── caller.ts       # 调用方管理 + API Key
├── monitor.ts      # 告警规则 + 告警记录
├── tenant.ts       # 租户管理
└── vendor.ts       # 厂商管理 + 数据类型 + 配置
```

统一使用 `request.get/post/put/patch/delete(url, data)` 模式，token 通过 axios 拦截器自动注入。

---

## 9. 实施阶段规划

| 阶段 | 名称 | 内容 | 风险 |
|------|------|------|------|
| **L1** | 参数校验增强 | AI 扫描 Controller + Entity → 生成边界/异常输入测试，追加到现有 `*ApiTest` 中 | 低 |
| **L2** | 业务链路串联 | AI 扫描 Vue 组件 + 前端 API → 生成含前置依赖的**业务链路测试**（"创建→查询→修改→删除"全流程） | 中 | ✅ 已完成 |

### L2 完成结果 (2026-05-09)

| 模块 | 测试数 | 修复的后端 Bug |
|------|--------|----------------|
| Vendor | 33 | — |
| Tenant | 13 | — |
| Monitor | 15 | — |
| Caller | 16 | CallerController createApiKey 手动构建实体缺字段 |
| IAM | 29 | — |
| Interface | 26 | interface_param 表缺失；Schema jsonb 类型更新失败 |
| **合计** | **132** | **3 个 bug** |

关键经验：
- `cleanupTasks` 必须 `static` + `@AfterAll`（JUnit5 每个 @Test 创建新实例，非 static 字段在测试间不可见）
- Gateway auth exclusions 导致部分路由不需要 token，边界测试需适配
- PostgreSQL `jsonb` 列需要 `::jsonb` 类型转换，MyBatis-Plus 默认不处理
| **L3** | 400 级自愈 | 400 + 校验注解匹配 → AI 自动修测试数据 → 重跑 | 中高 |
| **L4** | 500 级自愈 | 四步排查法驱动 → 分类修复或标记报告 | 高 |

---

## 10. 试点计划 — Vendor 模块

### 10.1 选 Vendor 的理由

- Vendor 模块合并了厂商管理、数据类型、配置中心三个功能域，**依赖关系最丰富**
- 前端 `VendorManagement.vue` 中有明确的下拉框加载逻辑（`GET /data-types`、`GET /vendors`）

### 10.2 试点步骤

| 序号 | 步骤 | 产出 |
|------|------|------|
| 1 | AI 扫描 `VendorManagement.vue` → 提取 API 调用时序 | 依赖图 (JSON) |
| 2 | AI 扫描 `VendorController` + `VendorInfo` 等 Entity | 字段/校验映射 |
| 3 | 生成一条完整业务链路测试（创建厂商 → 配置 → 查询 → 删除） | `VendorBusinessFlowTest.java` |
| 4 | 运行测试，验证生成的测试能否通过 | 绿灯或收集失败信息 |
| 5 | 用一个故意构造的错误验证自愈流水线 | 自愈闭环验证 |

### 10.3 试点成功标准

- [x] 生成的链路测试无需人工修改即可通过（Vendor: 33 tests, 0 failures）
- [x] 链路测试覆盖 ≥3 个接口的 CRUD 串联（覆盖 Vendor + DataType + Config 三个域）
- [x] 400 级自愈在 3 次重试内修复成功（重复 code、缺字段等场景验证通过）
- [x] 500 + `DataIntegrityViolationException` 自愈在 3 次重试内修复成功（修复 CallerController API Key、interface_param 表缺失、Schema jsonb 类型问题）

---

## 11. 变更记录

| 日期 | 版本 | 变更内容 |
|------|------|---------|
| 2026-05-09 | v1.3 | L2 试点完成，6 模块 132 tests 全部通过；更新试点成功标准为已完成 |
| 2026-05-09 | v1.2 | 修正清理机制：cleanupTasks 改为 static + @AfterAll（JUnit5 每个 @Test 创建新实例，@AfterEach 无法访问 static 清理注册表） |
| 2026-05-09 | v1.1 | 新增第6章"测试数据清理"，包含清理策略分层、BaseTest 兜底机制、AI 生成清理代码规范 |
| 2026-05-09 | v1.0 | 初始版本，基于技术讨论编写 |
