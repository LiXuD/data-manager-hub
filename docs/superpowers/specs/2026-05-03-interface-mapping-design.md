# 接口参数映射设计方案

## 1. 背景与目标

### 1.1 问题背景

数据管理平台需要对接多个外部数据厂商，不同厂商的 API 参数格式各不相同。内部系统希望使用统一的字段名调用平台，由平台负责转换为各厂商要求的字段格式。

### 1.2 设计目标

1. **统一内部 API**：内部系统使用统一的字段名（如 `entName`）调用平台
2. **配置化适配**：通过配置实现不同厂商的参数映射，无需编写代码
3. **支持嵌套提取**：支持 JSONPath 语法提取嵌套响应字段
4. **灵活默认值**：字段不存在时可配置默认值
5. **值转换支持**：支持大小写转换、类型转换等常用转换

---

## 2. 数据模型设计

### 2.1 请求参数映射

**TypeScript 类型定义**：

```typescript
interface RequestMappingItem {
  /** 目标字段名（发送给厂商的字段） */
  targetField: string;
  /** 内部变量名（从请求中获取的值） */
  sourceVar: string;
  /** 默认值（变量不存在时使用） */
  defaultValue?: string;
  /** 是否必填，默认 true */
  required?: boolean;
  /** 值转换类型 */
  transformType?: 'none' | 'uppercase' | 'lowercase' | 'trim';
}
```

**示例配置**：

```json
{
  "requestMapping": [
    {"targetField": "keyword", "sourceVar": "entName", "required": true},
    {"targetField": "searchType", "sourceVar": "searchMode", "defaultValue": "exact", "transformType": "lowercase"},
    {"targetField": "pageSize", "sourceVar": "limit", "defaultValue": "10"}
  ]
}
```

**映射示例**：

| 内部请求 | 配置 | 厂商请求 |
|---------|------|---------|
| `{entName: "北京科技"}` | `{targetField: "keyword", sourceVar: "entName"}` | `{keyword: "北京科技"}` |
| `{searchMode: "FUZZY"}` | `{targetField: "type", sourceVar: "searchMode", defaultValue: "exact", transformType: "lowercase"}` | `{type: "fuzzy"}` |

### 2.2 响应参数映射

**TypeScript 类型定义**：

```typescript
interface ResponseMappingItem {
  /** 目标字段名（返回给内部系统的字段） */
  targetField: string;
  /** 来源路径（厂商返回的字段，支持 JSONPath） */
  sourcePath: string;
  /** 路径类型：field=普通字段, jsonPath=JSONPath表达式 */
  sourceType?: 'field' | 'jsonPath';
  /** 默认值（字段不存在时使用） */
  defaultValue?: any;
  /** 值转换类型 */
  transformType?: 'none' | 'toString' | 'toNumber';
}
```

**示例配置**：

```json
{
  "responseMapping": [
    {"targetField": "companyName", "sourcePath": "ent_name", "sourceType": "field"},
    {"targetField": "capital", "sourcePath": "reg_cap", "defaultValue": 0, "transformType": "toNumber"},
    {"targetField": "legalPerson", "sourcePath": "$.data.legalPerson", "sourceType": "jsonPath"}
  ]
}
```

**映射示例**：

| 厂商响应 | 配置 | 内部响应 |
|---------|------|---------|
| `{"ent_name": "北京科技"}` | `{targetField: "companyName", sourcePath: "ent_name"}` | `{companyName: "北京科技"}` |
| `{"reg_cap": "1000000"}` | `{targetField: "capital", sourcePath: "reg_cap", transformType: "toNumber"}` | `{capital: 1000000}` |
| `{"data": {"legalPerson": "张三"}}` | `{targetField: "legalPerson", sourcePath: "$.data.legalPerson", sourceType: "jsonPath"}` | `{legalPerson: "张三"}` |

---

## 3. 配置存储设计

### 3.1 数据库字段

在 `vendor_config` 表中使用现有字段存储映射配置：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `request_template` | JSONB | 存储请求映射配置（包含 requestMapping） |
| `response_mapping` | JSONB | 存储响应映射配置 |

### 3.2 存储格式

```json
{
  "requestMapping": [
    {"targetField": "keyword", "sourceVar": "entName", "required": true}
  ]
}
```

```json
{
  "responseMapping": [
    {"targetField": "companyName", "sourcePath": "ent_name", "sourceType": "field"},
    {"targetField": "legalPerson", "sourcePath": "$.data.legalPerson", "sourceType": "jsonPath"}
  ]
}
```

---

## 4. 数据流设计

### 4.1 完整处理流程

```
内部系统请求
    │
    ▼
┌─────────────────────────────────────────┐
│  1. 请求参数映射引擎                     │
│     - 解析 requestMapping 配置           │
│     - 从请求中提取变量值                │
│     - 应用默认值和转换                  │
│     - 组装厂商请求参数                  │
└─────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────┐
│  2. 调用厂商 API                         │
│     - 发送组装好的请求到厂商            │
│     - 接收厂商响应                      │
└─────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────┐
│  3. 响应映射引擎                        │
│     - 解析 responseMapping 配置         │
│     - 从厂商响应中提取字段值            │
│     - 应用 JSONPath 提取嵌套字段        │
│     - 应用默认值和转换                  │
│     - 组装内部系统响应                  │
└─────────────────────────────────────────┘
    │
    ▼
返回给内部系统
```

---

## 5. 核心处理逻辑

### 5.1 请求映射处理

```java
public class RequestMappingProcessor {

    public Map<String, Object> mapRequest(Object request, List<RequestMappingItem> mappings) {
        Map<String, Object> requestMap = convertToMap(request);
        Map<String, Object> result = new HashMap<>();

        for (RequestMappingItem item : mappings) {
            Object value = requestMap.get(item.getSourceVar());

            if (value == null) {
                if (Boolean.TRUE.equals(item.getRequired())) {
                    throw new MappingException("缺少必填参数: " + item.getSourceVar());
                }
                value = item.getDefaultValue();
            }

            if (value != null) {
                value = transformValue(value, item.getTransformType());
            }

            result.put(item.getTargetField(), value);
        }

        return result;
    }

    private Object transformValue(Object value, String transformType) {
        if (value == null || "none".equals(transformType)) {
            return value;
        }
        switch (transformType) {
            case "uppercase":
                return value.toString().toUpperCase();
            case "lowercase":
                return value.toString().toLowerCase();
            case "trim":
                return value.toString().trim();
            default:
                return value;
        }
    }
}
```

### 5.2 响应映射处理

```java
public class ResponseMappingProcessor {

    private final JsonPath jsonPath = new JsonPath();

    public Map<String, Object> mapResponse(Object vendorResponse, List<ResponseMappingItem> mappings) {
        Map<String, Object> responseMap = convertToMap(vendorResponse);
        Map<String, Object> result = new HashMap<>();

        for (ResponseMappingItem item : mappings) {
            Object value;

            if ("jsonPath".equals(item.getSourceType())) {
                value = extractByJsonPath(responseMap, item.getSourcePath());
            } else {
                value = responseMap.get(item.getSourcePath());
            }

            if (value == null) {
                value = item.getDefaultValue();
            }

            if (value != null) {
                value = transformValue(value, item.getTransformType());
            }

            result.put(item.getTargetField(), value);
        }

        return result;
    }

    private Object extractByJsonPath(Map<String, Object> data, String jsonPathExpr) {
        try {
            return JsonPath.read(data, jsonPathExpr);
        } catch (Exception e) {
            return null;
        }
    }

    private Object transformValue(Object value, String transformType) {
        if (value == null || "none".equals(transformType)) {
            return value;
        }
        switch (transformType) {
            case "toString":
                return value.toString();
            case "toNumber":
                if (value instanceof Number) {
                    return value;
                }
                try {
                    return new BigDecimal(value.toString().replace(",", ""));
                } catch (NumberFormatException e) {
                    return value;
                }
            default:
                return value;
        }
    }
}
```

---

## 6. 前端界面设计

### 6.1 请求参数映射编辑器

| 内部变量名 | → | 目标字段名 | 默认值 | 转换类型 | 操作 |
|-----------|---|-----------|--------|---------|------|
| entName | → | keyword | - | lowercase | 删除 |
| searchMode | → | searchType | exact | - | 删除 |
| limit | → | pageSize | 10 | - | 删除 |

**功能**：
- 添加/删除映射行
- 验证字段名是否为空
- 支持导入/导出 JSON 配置

### 6.2 响应参数映射编辑器

| 来源路径 | → | 目标字段名 | 默认值 | 类型 | 操作 |
|---------|---|-----------|--------|------|------|
| ent_name | → | companyName | - | 普通字段 | 删除 |
| $.data.name | → | name | - | JSONPath | 删除 |
| reg_cap | → | capital | 0 | 普通字段 | 删除 |

**功能**：
- 添加/删除映射行
- 路径类型切换（普通字段 / JSONPath）
- JSONPath 语法提示
- 支持导入/导出 JSON 配置

### 6.3 编辑器组件设计

**组件结构**：

```
ParamsMappingEditor/
├── index.vue                    # 主组件
├── RequestMappingTab.vue        # 请求映射 Tab
├── ResponseMappingTab.vue       # 响应映射 Tab
└── MappingRow.vue               # 映射行组件
```

---

## 7. 错误处理

### 7.1 映射错误类型

| 错误类型 | 说明 | 处理方式 |
|---------|------|---------|
| `MISSING_REQUIRED_PARAM` | 缺少必填参数 | 返回 400 错误，提示具体参数名 |
| `INVALID_JSON_PATH` | JSONPath 语法错误 | 记录日志，使用默认值 |
| `TRANSFORM_ERROR` | 值转换失败 | 使用原始值，记录警告日志 |

### 7.2 错误响应格式

```json
{
  "code": 4001,
  "message": "参数映射失败",
  "details": {
    "field": "entName",
    "reason": "缺少必填参数"
  }
}
```

---

## 8. 测试用例

### 8.1 请求映射测试用例

| 输入 | 配置 | 期望输出 |
|------|------|---------|
| `{entName: "北京科技"}` | `{targetField: "keyword", sourceVar: "entName"}` | `{keyword: "北京科技"}` |
| `{entName: "BEIJING"}` | `{targetField: "name", sourceVar: "entName", transformType: "lowercase"}` | `{name: "beijing"}` |
| `{}` | `{targetField: "type", sourceVar: "mode", defaultValue: "default"}` | `{type: "default"}` |
| `{}` | `{targetField: "type", sourceVar: "mode", required: true}` | 抛出异常 |

### 8.2 响应映射测试用例

| 输入 | 配置 | 期望输出 |
|------|------|---------|
| `{ent_name: "北京科技"}` | `{targetField: "companyName", sourcePath: "ent_name"}` | `{companyName: "北京科技"}` |
| `{reg_cap: "1000000"}` | `{targetField: "capital", sourcePath: "reg_cap", transformType: "toNumber"}` | `{capital: 1000000}` |
| `{data: {name: "测试"}}` | `{targetField: "name", sourcePath: "$.data.name", sourceType: "jsonPath"}` | `{name: "测试"}` |
| `{data: {}}` | `{targetField: "name", sourcePath: "$.data.name", defaultValue: "未知"}` | `{name: "未知"}` |

---

## 9. 后续扩展计划

### 9.2 暂不实现的功能

1. **多字段组合映射**：把多个字段组合，如 `$lastName + $firstName → name`
2. **条件映射**：根据字段值决定使用哪个映射规则
3. **Groovy 脚本支持**：使用脚本实现复杂转换逻辑

---

## 10. 涉及文件清单

### 10.1 后端文件

| 文件路径 | 操作 | 说明 |
|---------|------|------|
| `data-platform-common/src/main/java/.../mapping/RequestMappingItem.java` | 新增 | 请求映射实体 |
| `data-platform-common/src/main/java/.../mapping/ResponseMappingItem.java` | 新增 | 响应映射实体 |
| `data-platform-common/src/main/java/.../mapping/RequestMappingProcessor.java` | 新增 | 请求映射处理器 |
| `data-platform-common/src/main/java/.../mapping/ResponseMappingProcessor.java` | 新增 | 响应映射处理器 |
| `data-platform-common/src/main/java/.../mapping/MappingException.java` | 新增 | 映射异常类 |

### 10.2 前端文件

| 文件路径 | 操作 | 说明 |
|---------|------|------|
| `data-platform-web/src/types/index.ts` | 修改 | 添加 MappingItem 类型定义 |
| `data-platform-web/src/views/interface/components/config/ParamsMappingEditor.vue` | 重构 | 使用新结构化配置 |
| `data-platform-web/src/api/vendor-config.ts` | 修改 | 更新 API 类型定义 |

---

## 11. 验收标准

1. ✅ 请求参数支持 `$variableName` 语法动态替换
2. ✅ 响应支持普通字段映射
3. ✅ 响应支持 JSONPath 嵌套字段提取
4. ✅ 支持配置默认值
5. ✅ 支持值转换（大小写、类型转换）
6. ✅ 前端界面支持可视化配置
7. ✅ 提供导入/导出 JSON 配置功能
8. ✅ 错误信息清晰，便于排查问题
