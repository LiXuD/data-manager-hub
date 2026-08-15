# Code Review Gate（agent 必守）

> 本文件由 Code Review Expert 制定，是 `data-manager-hub` 所有编码 agent（GitNexus 等）提交 / 提 PR 前的统一自检门禁。
> 配套：`代码审查标准与流程.md`（完整检查清单）、`代码审查实施计划.md`（落地路线）。

任何编码 agent 在 commit / 提 PR 前必须：

1. **逐条核对 🔴 项**：对照 `代码审查标准与流程.md` 第 1 节的所有 🔴（阻断）项，未清不得 `declare done`。
2. **本地验证**：
   - 改动后端：对应模块 `mvn -pl <模块> test` 通过；
   - 改动前端：`npm run lint && npm run test` 通过。
3. **自审结论入提交信息**：提交信息须含「自审结论：🔴 已清 / 残留 🟡 清单」。
4. **高风险变更显式标注**：跨域调用 / DB 脚本 / Flowable 流程 / Nacos 配置变更须显式标注，交由 CI review gate 裁决。
5. **`data-platform-test` 不进 PR 门禁**：该模块是集成测试（需起 Gateway + 5 微服务 + PG/Redis/Kafka/Nacos 在线），其质量由夜间集成流水线（`nightly-integration.yml`）保障。改动业务模块时**只跑该模块的 `mvn test`（单测）**，请勿对其补集成测试作为 PR 验收。

## 拦截链路（纵深防御）

- **A. 源头自检**：本文件即 agent 的 Definition of Done，🔴 未清不得结束任务。
- **B. 本地 hook（辅助）**：`.githooks/pre-commit` 在本地提交时跑 lint + 编译（云端 / CI 直推不触发，仅辅助，见实施计划 1.2 N1）。
- **C. 远端硬门禁**：`.github/workflows/pr-review-gate.yml` 对每次 PR 跑编译 + 测试 + 前端 lint/单测；分支保护要求该 check 通过且禁止直推 `dev`/`master`；`auto-merge.yml` 在 check 全绿后自动合入。

## 启用本地 hook（agent 本地 clone 时）

```bash
git config core.hooksPath .githooks
```

> 仅对本地提交生效；云端 / CI 直推的 agent 由 C 端硬门禁兜底，不可依赖 hook。
