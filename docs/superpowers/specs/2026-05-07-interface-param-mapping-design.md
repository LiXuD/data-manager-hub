# 接口参数定义与映射分离设计方案

## 1. 背景与问题

### 1.1 当前问题

当前 `vendor_params_mapping` 表将**参数定义**和**参数映射**混在同一张表中：

| 字段 | 职责 | 问题 |
|------|------|------|
| `paramName`, `paramType`, `required`, `defaultValue` | 参数定义 | — |
| `sourceField`, `targetField`, `transformExpr` | 参数映射 | 与参数定义混在一起 |
| *(缺失)* | 字段说明 | 无法描述参数业务含义，如 "name 姓名" |

核心矛盾：**一张表承担了两个独立职责**，且缺少 description 字段。

### 1.2 设计目标

1. **职责分离**：参数定义与参数映射拆分为独立的概念
2. **先定义再映射**：配置流程为 先配置请求参数 → 再配置映射关系
3. **无映射可用**：未配置映射时，使用原始参数名直接请求厂商 API
4. **字段说明**：请求参数字段支持 description（如 `name` → 姓名）

---

## 2. 数据模型设计

### 2.1 新增表：interface_param（接口参数定义）

挂在接口级，定义该接口有哪些请求参数，**所有厂商共用**。

```sql
CREATE TABLE IF NOT EXISTS interface_param (
    id BIGSERIAL PRIMARY KEY,
    interface_id BIGINT NOT NULL,
    param_name VARCHAR(64) NOT NULL,
    description VARCHAR(256),
    param_type VARCHAR(32) NOT NULL DEFAULT 'string',
    required BOOLEAN NOT NULL DEFAULT false,
    default_value VARCHAR(256),
    validation_rule VARCHAR(256),
    sort INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_interface_param_interface FOREIGN KEY (interface_id) REFERENCES api_interface(id),
    UNIQUE(interface_id, param_name)
);

CREATE INDEX idx_interface_param_interface ON interface_param(interface_id);
```

**字段说明：**

| 列名 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | BIGSERIAL | ✓ | 主键 |
| `interface_id` | BIGINT | ✓ | 关联 api_interface.id |
| `param_name` | VARCHAR(64) | ✓ | 参数名，如 "name" |
| `description` | VARCHAR(256) | — | **新增！** 字段说明，如 "姓名" |
| `param_type` | VARCHAR(32) | ✓ | 类型：string/number/boolean/object/array |
| `required` | BOOLEAN | — | 是否必填，默认 false |
| `default_value` | VARCHAR(256) | — | 默认值 |
| `validation_rule` | VARCHAR(256) | — | 校验规则表达式 |
| `sort` | INTEGER | — | 排序，默认 0 |

**唯一约束：** `(interface_id, param_name)` — 同一接口下参数名唯一。

### 2.2 vendor_config 新增字段：param_mapping（JSONB）

挂在厂商配置级，存该厂商如何将接口参数映射到厂商 API 参数。

```sql
ALTER TABLE vendor_config ADD COLUMN IF NOT EXISTS param_mapping JSONB DEFAULT NULL;
COMMENT ON COLUMN vendor_config.param_mapping IS '参数映射配置(JSONB)';
```

**JSON 结构：**

```json
[
  {
    "paramName": "name",
    "targetField": "ent_name",
    "transformExpr": null
  },
  {
    "paramName": "page",
    "targetField": "pageIndex",
    "transformExpr": null
  }
]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `paramName` | string | 接口参数名（对应 interface_param.param_name） |
| `targetField` | string | 映射到的厂商字段名 |
| `transformExpr` | string/null | 可选，转换表达式，如 `"upper(#{name})"` |

### 2.3 表关系

```
api_interface（已有）
  │
  │ 1 : N
  ▼
interface_param（🆕新增）
  param_name, param_type, required, description, default_value, validation_rule, sort
  │
  │ 通过 vendor_config 间接关联（interface_id）
  ▼
vendor_config（已有 + param_mapping）
  param_mapping (JSONB) 🆕 — 仅存映射关系
```

### 2.4 与现有 vendor_params_mapping 的关系

`vendor_params_mapping` 表在本方案实施后**逐步废弃**：
- 参数定义职责 → 迁移到 `interface_param`
- 参数映射职责 → 迁移到 `vendor_config.param_mapping` JSONB

---

## 3. API 端点设计

### 3.1 接口参数定义 API

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/interface/{id}/params` | 获取接口的参数定义列表 |
| `POST` | `/interface/{id}/params` | 新增一个参数定义 |
| `PUT` | `/interface/{id}/params/batch` | 批量保存参数定义（覆盖更新） |
| `PUT` | `/interface/params/{paramId}` | 更新单个参数定义 |
| `DELETE` | `/interface/params/{paramId}` | 删除一个参数定义 |

### 3.2 厂商参数映射 API

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/vendor/config/{id}/mapping` | 获取该厂商配置的参数映射 |
| `PUT` | `/vendor/config/{id}/mapping` | 更新参数映射 JSON |

---

## 4. 配置交互流程（两步式）

### 步骤 1：配置请求参数（接口级）

在接口详情页管理该接口的请求参数定义，包含 `description` 字段说明。

示例界面：

| 参数名 | 说明 | 类型 | 必填 | 默认值 | 操作 |
|--------|------|------|------|--------|------|
| `name` | 企业名称 | string | ✓ | — | ✏️ 🗑 |
| `page` | 页码 | number | — | 1 | ✏️ 🗑 |

### 步骤 2：配置参数映射（厂商级）

在厂商配置页管理该厂商的参数映射关系。左侧展示接口定义的参数名及说明，右侧配置映射到的厂商字段。

| 参数名 | → 映射到 | 转换 |
|--------|---------|------|
| `name` (企业名称) | `ent_name` | — |
| `page` (页码) | — | 不映射=直接传 page |

---

## 5. 运行时请求组装逻辑

```
1. 根据 interface_id 查出所有 interface_param 参数定义
2. 根据 vendor_config_id 查出 param_mapping 映射配置
3. 遍历参数：
   - 有映射 → 使用 targetField 作为请求字段名，有 transformExpr 执行转换
   - 无映射 → 使用原始 param_name 直接请求
4. 组装最终请求体发送给厂商
```

**示例：**

```
参数定义:  [{name: "name", ...}, {name: "page", ...}]
映射配置:  [{"paramName": "name", "targetField": "ent_name"}]
输入请求:  {name: "阿里巴巴", page: 1}

→ 有映射(name→ent_name)：{ent_name: "阿里巴巴"}
→ 无映射(page)：{page: 1}
→ 最终请求体: {"ent_name": "阿里巴巴", "page": 1}
```

---

## 6. 涉及文件清单

### 6.1 数据库

| 文件 | 操作 | 说明 |
|------|------|------|
| `sql/migrations/V007__create_interface_param.sql` | 新增 | 创建 interface_param 表 + vendor_config 加 param_mapping |

### 6.2 后端（Java）

| 模块 | 文件 | 操作 | 说明 |
|------|------|------|------|
| interface-api | `entity/InterfaceParam.java` | 新增 | 参数定义实体 |
| interface-api | `dto/InterfaceParamDTO.java` | 新增 | 参数定义 DTO |
| interface-service | `mapper/InterfaceParamMapper.java` | 新增 | MyBatis Mapper |
| interface-service | `service/InterfaceParamService.java` | 新增 | 服务接口 |
| interface-service | `service/impl/InterfaceParamServiceImpl.java` | 新增 | 服务实现 |
| interface-service | `controller/ApiInterfaceController.java` | 修改 | 添加参数管理端点 |
| vendor-service | `controller/VendorConfigController.java` | 修改 | 添加映射管理端点 |
| common/call | `service/ParamMappingProcessor.java` | 新增 | 请求参数组装处理器 |

### 6.3 前端

| 文件 | 操作 | 说明 |
|------|------|------|
| `src/views/interface/InterfaceParamsEditor.vue` | 新增 | 步骤1：参数定义编辑器 |
| `src/views/vendor/VendorParamMapping.vue` | 新增 | 步骤2：映射配置编辑器 |
| `src/api/interface-param.ts` | 新增 | 参数 API 类型定义 |
| `src/api/vendor-config.ts` | 修改 | 添加映射 API |

---

## 7. 验收标准

1. ✅ 参数定义与参数映射分离为独立的配置
2. ✅ 配置流程为 先定义参数 → 再配置映射
3. ✅ 请求参数字段支持 `description` 字段说明
4. ✅ 配置了映射时使用 targetField 请求厂商 API
5. ✅ 未配置映射时使用原始 param_name 直接请求
6. ✅ 前端支持两步式配置界面（参数定义编辑器 + 映射配置编辑器）

---

## 8. 暂不实现

1. 复杂的 transformExpr 表达式引擎（初版仅支持字段名替换）
2. vendor_params_mapping 表的数据自动迁移（手动迁移）
3. 参数定义版本管理
