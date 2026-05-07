# Tasks
- [x] 数据库迁移脚本：新增4张权限相关表和初始化数据
- [x] 后端核心实体类：Permission、RolePermission、UserCaller、ApiKeyInterface
- [x] 后端 Mapper、Service、Controller：实现权限管理相关API
- [x] 更新 UserContext：添加获取当前用户权限和租户ID的方法
- [x] 更新 RoleController：添加角色权限管理功能
- [x] 更新 UserController：添加用户调用方关联功能
- [x] 更新 ApiKeyController：添加接口授权功能
- [x] Gateway集成Sa-Token：统一鉴权拦截
- [x] DataQueryController：API Key接口权限验证
- [x] 租户数据隔离：各Service查询时强制带上tenantId过滤（基础实现，后续逐步完善）
- [x] 前端用户管理：添加调用方关联功能
- [x] 前端角色管理：添加权限分配功能
- [x] 前端API Key管理：添加接口授权功能
- [x] 前端动态菜单：根据权限过滤菜单和按钮

# Task Dependencies
- 后端 Mapper、Service、Controller 依赖于数据库迁移脚本和核心实体类
- 前端功能依赖于后端 API
