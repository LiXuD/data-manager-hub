# 前端项目进度记录

## 日期: 2026-04-26

### 完成的工作

#### 1. 类型安全优化

**问题**: 代码中存在 `any` 和 `unknown` 类型，缺乏类型安全

**解决方案**:
- 移除所有 `any` 类型，替换为具体类型
- `catch` 块中未使用的错误参数改为空 `catch {}`
- 添加 `ListResponse<T>` 泛型类型统一列表响应
- 使用字面量联合类型替代 `string` 类型（如 `'active' | 'inactive'`）

**修改文件**:
- `src/api/caller.ts` - 状态参数类型化
- `src/api/tenant.ts` - 状态参数类型化
- `src/api/vendor.ts` - 状态参数类型化
- `src/api/monitor.ts` - 移除 `any` 参数类型
- `src/types/index.ts` - 添加 `ListResponse<T>`, `AlertRule`, `AlertRecord` 等类型
- `src/utils/request.ts` - 移除默认 `any` 泛型，改用 `unknown`

#### 2. 创建共享工具函数

**新增文件**:

##### `src/utils/status.ts`
集中管理状态类型映射和标签:
```typescript
// 状态类型映射（用于 el-tag 的 type 属性）
export const statusTypeMap = {
  billing: { pending: 'warning', settled: 'success', overdue: 'danger' },
  call: { success: 'success', failed: 'danger', timeout: 'warning' },
  active: { active: 'success', inactive: 'info', expired: 'warning' },
  health: { healthy: 'success', unhealthy: 'danger', unknown: 'warning' },
  enabled: { success: 'success', failed: 'danger' },
  level: { info: 'info', warning: 'warning', error: 'danger', critical: 'danger' }
}

// 状态文本标签
export const statusLabels = {
  billing: { pending: '待结算', settled: '已结算', overdue: '逾期' },
  call: { success: '成功', failed: '失败', timeout: '超时', rate_limited: '限流' },
  // ...
}

// 工具函数
export function getStatusType(domain: StatusDomain, status: string): TagType
export function getStatusText(domain: string, status: string): string
```

##### `src/utils/pagination.ts`
统一处理多种 API 响应格式:
```typescript
export function extractPageData<T>(response: unknown): { list: T[]; total: number }
```
支持的格式:
- `{ list: T[], total: number }` - PageResult 格式
- `{ records: T[], total: number }` - records 格式
- `{ data: T[] }` 或 `{ data: { records: T[], total: number } }` - 嵌套格式

#### 3. 视图文件优化

**修改的视图文件**:

| 文件 | 修改内容 |
|------|----------|
| `views/billing/index.vue` | 使用共享状态工具，移除重复的 statusLabels |
| `views/call/index.vue` | 使用共享状态工具 |
| `views/config/index.vue` | 使用共享状态工具 |
| `views/graylog/index.vue` | 移除未使用的 `handleToggleStatus` 函数 |
| `views/monitor/index.vue` | 移除未使用的 `pagination` 和 `getStatusType`，改用并行 API 调用 |
| `views/tenant/index.vue` | 更新响应处理 |
| `views/vendor/index.vue` | 更新状态类型 |
| `views/caller/index.vue` | 修复 API 响应处理 |
| `views/user/index.vue` | 更新响应处理 |
| `views/datatype/index.vue` | 添加类型定义 |
| `views/role/index.vue` | 添加类型定义 |

#### 4. 效率优化

**并行 API 调用**:
```typescript
// 修改前
onMounted(() => { fetchHealth(); fetchAlerts() })

// 修改后
onMounted(() => { Promise.all([fetchHealth(), fetchAlerts()]) })
```

#### 5. 代码清理

- 移除未使用的导入和变量
- 移除重复的状态标签定义
- 移除未使用的函数
- 统一错误处理模式

### 提交记录

```
aed6839 refactor(web): cleanup unused code and use shared utilities
```

### 遗留问题

1. **TS6133 警告**: 部分文件存在未使用的导入/变量警告（非阻塞）
2. **TS6196 警告**: `views/profile/index.vue` 中 `ThemeMode` 类型声明但未使用

### 后续建议

1. 考虑将更多视图文件中的 `*ListResponse` 本地接口替换为 `ListResponse<T>`
2. 统一所有列表视图使用 `extractPageData` 工具函数
3. 为更多 API 函数添加返回类型定义
