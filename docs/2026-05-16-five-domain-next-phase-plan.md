# 五域收敛后续推进计划

**日期**: 2026-05-16  
**当前基线**: `dev` 分支已完成五域收敛、远程 dev 新提交合并、后端编译验证、前端构建验证和入口文档校准。  
**当前目标**: 从“结构重构完成”进入“业务能力稳定迁移、测试闭环、团队协作常态化”阶段。

---

## 1. 当前状态

### 已完成

- 业务服务已收敛为 5 个核心域：
  - `masterdata`: 厂商、数据类型、接口定义、厂商配置、灰度规则
  - `access`: 调用方、API Key、数据调用、调用记录
  - `billing`: 计费规则、账单、结算/对账
  - `identity`: 租户、用户、角色、认证/安全能力
  - `governance`: 监控告警、操作日志、质量规则、数据血缘
- 每个业务域已采用 `api + service` 双模块结构。
- 旧小服务目录已从主构建中退役，不再作为部署和依赖单元。
- `sdk` 已定位为普通 Jar，不再作为 Spring Boot 服务独立部署。
- 后端验证已通过：
  - `mvn -q validate`
  - `mvn -q -DskipTests compile`
  - `mvn -q -DskipTests test-compile`
- 前端验证已通过：
  - `npm ci`
  - `npm run build`
- 远程 `dev` 已同步到最新五域基线。

### 仍需注意

- 部分历史文档仍保留旧小服务章节，当前已在入口文档标注“五域基线优先”。后续应逐步重写，而不是一次性大删。
- 前端页面路由仍沿用原业务页面命名，例如 `/vendor`、`/caller`、`/graylog`。这是用户入口语义，不等同于后端服务拆分。
- 当前测试以编译和构建为主，仍需要补齐真实业务链路回归和启动烟雾验证。

---

## 2. 总体策略

后续不再做大规模目录搬迁，而是进入三个方向的稳定化工作：

1. **契约稳定**
   - 所有跨域调用只允许依赖目标域 `*-api`。
   - Controller 实现本域 api 契约。
   - DTO/VO 不暴露数据库实体。

2. **行为回归**
   - 以业务链路为单位补测试，而不是以旧服务为单位补测试。
   - 先覆盖主链路，再覆盖边界与异常。

3. **团队协作**
   - `dev` 作为五域基线继续承接新功能。
   - 新功能必须按五域归属落位。
   - 不再新增旧式单服务模块。

---

## 3. 推荐执行顺序

### 阶段 A: 基线守护与协作规则落地

**目标**: 防止团队在五域基线之后继续引入旧结构。

任务：

- 在 `AGENTS.md`、`README.md`、`PENDING_TASKS.md` 中继续保持五域架构说明优先。
- 增加架构扫描脚本或测试，至少覆盖：
  - 禁止 `*-service` 依赖其他域 `*-service`
  - 禁止 `*-api` 引入 MyBatis、数据库、Redis、Nacos、Spring Boot Starter
  - 禁止 `@SpringBootApplication(scanBasePackages = "com.dataplatform")`
  - 禁止跨域 import 他域 `entity/mapper/service/controller`
- 将 `mvn validate`、后端 compile、前端 build 加入 CI 或团队合并前检查。

验收：

- 本地和 CI 都能一键发现架构违规。
- 新增功能 PR 不再出现旧服务 artifactId 或旧包名。

---

### 阶段 B: 启动烟雾测试

**目标**: 确认五个业务服务和网关可以按新拓扑独立启动。

任务：

- 使用 `start-services.sh` 或等价命令启动：
  - `data-platform-masterdata`
  - `data-platform-access`
  - `data-platform-billing`
  - `data-platform-governance`
  - `data-platform-identity`
  - `data-platform-gateway`
- 检查每个服务：
  - 端口监听正常
  - Spring 上下文启动正常
  - Feign Client 注入正常
  - Mapper 扫描正常
  - 配置文件应用名正确
- 补充健康检查脚本或最小 smoke test。

验收：

- 五域服务和 gateway 均可在本地独立启动。
- 日志中无 Bean 冲突、Mapper 冲突、Feign 自调用误注册、全包扫描副作用。

---

### 阶段 C: 业务链路回归

**目标**: 验证重构没有破坏业务行为。

优先级 1 链路：

- 主数据：
  - 厂商创建、查询、更新
  - 数据类型创建、查询
  - 接口定义创建、查询、参数配置
  - 厂商配置、灰度规则
- 访问：
  - 调用方创建
  - API Key 生成、授权、状态变更
  - 数据查询调用
  - 调用记录写入
- 计费：
  - 计费规则配置
  - 调用后计费计算
  - 账单查询

优先级 2 链路：

- 身份租户：
  - 租户、用户、角色
  - 登录认证
  - 用户角色绑定
- 观测治理：
  - 操作日志记录
  - 告警规则
  - 数据质量检查
  - 数据血缘查询

验收：

- 每条主链路至少有一条自动化测试或可重复执行的 Postman/curl 脚本。
- 所有测试以五域服务为对象，不再以旧小服务为对象。

---

### 阶段 D: 前端接口归并

**目标**: 前端页面语义可以保留，但 API 调用层要逐步贴合五域后端边界。

任务：

- 维持页面路由：
  - `/vendor`
  - `/caller`
  - `/billing`
  - `/tenant`
  - `/user`
  - `/role`
  - `/monitor`
  - `/audit`
  - `/config`
  - `/graylog`
- 整理 `src/api/*`：
  - 页面命名 API 可以保留
  - 底层请求路径统一对齐 gateway 路由
  - 类型统一从 `src/types` 导出
  - 避免同一 DTO 多处重复声明
- 对构建警告中的大 chunk 做后续优化：
  - 按路由拆分
  - 配置 `manualChunks`
  - 延迟加载图表类依赖

验收：

- `npm run build` 持续通过。
- 前端 API 类型无 `unknown`、重复 DTO、缺失导出等问题。

---

### 阶段 E: 历史文档和旧引用清理

**目标**: 降低团队认知成本，但不破坏历史记录。

任务：

- 将 `CODE_WIKI.md` 中旧小服务章节改写为五域章节。
- 将 `PENDING_TASKS.md` 中历史任务保留为“历史迁移记录”，新增任务统一写到“五域基线后续任务”下。
- 检查并更新：
  - `docs/DEPLOYMENT.md`
  - `docs/API.md`
  - `CODE_WIKI.md`
  - `README.md`
- 删除或归档明显误导当前启动方式的旧说明。

验收：

- 新成员只看 README、AGENTS、PENDING_TASKS 就能理解当前架构。
- 文档中旧服务名只出现在“历史记录 / 迁移说明”语境中。

---

### 阶段 F: 部署与发布路径

**目标**: 让五域结构可以稳定交付。

任务：

- 更新 Docker / docker-compose / 部署脚本：
  - 去掉旧小服务镜像
  - 新增五域服务镜像
  - `sdk` 不生成服务镜像
- 统一 `spring.application.name`：
  - `data-platform-masterdata`
  - `data-platform-access`
  - `data-platform-billing`
  - `data-platform-identity`
  - `data-platform-governance`
  - `data-platform-gateway`
- 更新 gateway 路由：
  - 外部路径可以保持原业务语义
  - 内部转发目标改为五域服务
- 明确发布顺序：
  1. 基础设施
  2. identity
  3. masterdata
  4. billing
  5. access
  6. governance
  7. gateway
  8. web

验收：

- 本地 docker-compose 或测试环境能完整拉起五域服务。
- gateway 能正确转发原有前端请求。

---

## 4. 新功能开发规则

### 是否需要停止新功能开发

不建议完全停止，但必须切换协作方式：

- 可以继续开发新功能。
- 新功能必须基于五域边界落位。
- 不允许再基于旧小服务新增模块或依赖。
- 涉及跨域能力时，先改目标域 `api` 契约，再由调用方通过 Feign 使用。

### 推荐分支规则

- `dev`: 五域集成基线。
- 功能分支: 从最新 `dev` 拉出。
- 每日或每个小阶段 rebase/merge 最新 `dev`，避免长期漂移。
- 合入前必须通过：
  - `mvn -q validate`
  - `mvn -q -DskipTests compile`
  - `mvn -q -DskipTests test-compile`
  - `npm run build`

### 代码落位规则

| 功能类型 | 归属域 |
|----------|--------|
| 厂商、数据类型、接口定义、配置、灰度 | masterdata |
| 调用方、API Key、调用代理、调用记录 | access |
| 计费规则、账单、结算、对账 | billing |
| 租户、用户、角色、认证、安全 | identity |
| 日志、监控、告警、质量、血缘 | governance |

---

## 5. 风险与控制

### 风险 1: 旧服务名继续回流

控制：

- 架构扫描禁止旧 service artifactId 重新进入 POM。
- 文档新增内容必须使用五域名称。
- 启动脚本只维护五域服务。

### 风险 2: api 模块变重

控制：

- api 模块只允许轻量依赖。
- DTO 与 Entity 分离。
- 数据库注解、Mapper、Service、Controller 只允许出现在 service 模块。

### 风险 3: 跨域直接调用回潮

控制：

- 禁止 service 依赖其他域 service。
- 跨域只依赖目标域 api。
- 本地域内部保持 Controller -> Service -> Mapper 的本地调用。

### 风险 4: 前端接口路径和后端服务边界混淆

控制：

- 前端页面路由保持用户业务语义。
- gateway 负责兼容旧路径和新五域服务映射。
- 前端 API 文件可按页面维护，但类型和请求封装必须统一。

---

## 6. 下一步推荐任务清单

建议按以下顺序执行：

1. 增加架构扫描测试，先把五域边界自动化守住。
2. 执行五域服务本地启动烟雾测试，记录启动日志和问题。
3. 更新 `docs/DEPLOYMENT.md`，让部署文档和五域服务一致。
4. 梳理 gateway 路由，确认旧前端路径能转发到新五域服务。
5. 将新增业务链路测试从旧服务语义调整为五域语义。
6. 重写 `CODE_WIKI.md` 的模块详解章节，从旧小服务章节改为五域章节。
7. 建立合并前检查命令清单或 CI。

---

## 7. 阶段完成标准

下一阶段可认为完成的标准：

- 五域服务和 gateway 均可独立启动。
- 后端、前端、架构扫描全部通过。
- 主数据、访问、计费三条核心链路有自动化回归。
- 部署文档、README、PENDING_TASKS、CODE_WIKI 不再误导团队使用旧服务拓扑。
- 团队新增功能可以稳定落到五域结构中，不再产生循环依赖和冗余依赖。
