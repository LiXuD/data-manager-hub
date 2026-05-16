# AGENTS.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

---

## 5. 项目关键约束

- **设计文档**: `2026-04-17-data-management-platform-design.md`（项目根目录）
- **待办追踪**: 每次任务完成需更新 `PENDING_TASKS.md`
- **服务间通信**: Service 不允许直接依赖其他域 service；跨域调用只能通过目标域 `api` 中的 OpenFeign 契约
- **模块结构**: 当前业务域收敛为 `masterdata / access / billing / identity / governance` 五域；每个业务域采用 `api + service` 双模块结构
- **契约边界**: `*-api` 只放 Feign 接口、DTO/VO/BO、枚举、常量和统一返回契约，不引入数据库、MyBatis、Redis、Nacos 等重型依赖

## 6. 项目概况

- **项目名称**: 数据管理平台 (Data Management Platform)
- **仓库**: `https://github.com/LiXuD/data-manager-hub.git`
- **技术栈**: Java 21 + Spring Boot 3.4 + Spring Cloud 2024.0.0 + MyBatis-Plus 3.5.7 + Vue3 + TypeScript

详细进度、模块端口、数据库表、页面路由等信息见：
- `PENDING_TASKS.md` — 开发进度、模块结构、数据库表、技术栈
- `CODE_WIKI.md` — 模块详解、架构设计、依赖关系
- `docs/API.md` — API 接口文档
- `docs/DEPLOYMENT.md` — 部署文档

## 7. Git 提交规范

### commit message 格式

```
<type>(<scope>): <summary>

<正文：描述本次变更的背景与动机>

Agent-Task: <原始任务描述或任务 ID>
Agent-Model: <使用的模型>
Agent-Decision: <关键设计决策及理由>
Agent-Limitation: <已知局限或后续 TODO>
```

### 提交类型

- `feat`: 新功能 | `fix`: 修复bug | `refactor`: 重构
- `docs`: 文档 | `test`: 测试 | `chore`: 构建/工具

### 提交节点

完成以下关键节点时执行 git commit：
- 数据模型/接口定义完成
- 核心逻辑实现完成
- 测试编写完成
- 文档更新完成

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **data-manager-hub** (5760 symbols, 12875 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/data-manager-hub/context` | Codebase overview, check index freshness |
| `gitnexus://repo/data-manager-hub/clusters` | All functional areas |
| `gitnexus://repo/data-manager-hub/processes` | All execution flows |
| `gitnexus://repo/data-manager-hub/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
