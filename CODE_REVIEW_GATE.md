# 开发阶段提交门禁

当前项目处于开发阶段。本文件只定义提交代码时必须通过的基础检查，目标是尽早发现
编译、测试和前端构建错误；部署、制品发布、制品升级、Kubernetes、生产环境和自动回滚
不属于当前 CI 门禁。

## 必须通过的检查

1. 执行 `python3 ci/scripts/verify-dev-security-sync.py` 和 `python3 -m unittest discover -s ci/tests -p 'test_*.py'`，验证选择性安全同步、dev/生产 CI 边界和仓库合同。
2. 后端执行 `./mvnw -B -ntp verify`，排除 `data-platform-test` 下的 API、测试服务和 E2E fixture 模块，完成 Java 编译和单元测试。
3. 前端在 `data-platform-web` 执行 `npm ci`、`npm run lint`、`npm test` 和 `npm run build`。
4. PR 或 push 到 `dev`/`master` 时，`.github/workflows/ci.yml` 自动执行上述后端、前端和合同检查。
5. 分支保护只要求 `CI / required-ci` 通过；任一编译或测试 Job 失败都不得合并。

需要生成覆盖率、Checkstyle 或 SpotBugs 报告时，单独执行 `quality` Profile：

```bash
./mvnw -B -ntp -Pquality verify \
  -pl '!data-platform-test/data-platform-test-api,!data-platform-test/data-platform-test-service,!data-platform-test/test-fixtures/external-connector-plugin'
```

该 Profile 当前仍是报告模式，不属于开发阶段必需门禁。

## 当前不要求

- `data-platform-test` 的完整多服务集成验收不作为每次提交门禁；`.github/workflows/dev-mvp-e2e.yml` 仅按工作日定时或手工执行；
- Docker 镜像构建、GHCR、OCI Manifest、签名、SBOM、attestation；
- Helm、Kubernetes、Nacos、Prometheus、自动部署和 staging/production 晋级；
- 夜间安全扫描、自动合并和 AI 代码审查。

上述生产能力保留在生产部署前方案中，不得被当前开发 CI 自动触发。Dev MVP 定时 E2E 只负责
发现开发环境回归，不发布制品、不部署 staging/production，也不改变 `CI / required-ci` 的唯一必需检查地位。

## 本地辅助检查

本地 hook 只是提交前的快速提示，不能替代远端 CI。启用方式：

```bash
git config core.hooksPath .githooks
```

如果修改了核心业务逻辑，仍应补充对应单元测试；如果本地工具链不满足项目版本要求，先按
`ci/toolchain.lock.yaml` 和模块 README 切换 Java/Node/Maven 版本。
