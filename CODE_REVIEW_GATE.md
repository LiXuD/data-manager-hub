# 开发阶段提交门禁

当前项目处于开发阶段。本文件只定义提交代码时必须通过的基础检查，目标是尽早发现
编译、测试和前端构建错误；部署、制品发布、制品升级、Kubernetes、生产环境和自动回滚
不属于当前 CI 门禁。

## 必须通过的检查

1. 后端执行 `./mvnw -B -ntp verify`，排除 `data-platform-test` 下的 API、测试服务和 E2E fixture 模块，完成 Java 编译和单元测试。
2. 前端在 `data-platform-web` 执行 `npm ci`、`npm run lint`、`npm test` 和 `npm run build`。
3. PR 或 push 到 `dev`/`master` 时，`.github/workflows/ci.yml` 自动执行后端和前端检查。
4. 分支保护只要求 `CI / required-ci` 通过；任一编译或测试 Job 失败都不得合并。

## 当前不要求

- `data-platform-test` 的完整多服务集成验收；需要时手工启动环境后执行；
- Docker 镜像构建、GHCR、OCI Manifest、签名、SBOM、attestation；
- Helm、Kubernetes、Nacos、Prometheus、自动部署和 staging/production 晋级；
- 夜间安全扫描、自动合并和 AI 代码审查。

上述能力保留在生产部署前方案中，当前只允许作为手工或未来阶段使用，不得被开发 CI 自动触发。

## 本地辅助检查

本地 hook 只是提交前的快速提示，不能替代远端 CI。启用方式：

```bash
git config core.hooksPath .githooks
```

如果修改了核心业务逻辑，仍应补充对应单元测试；如果本地工具链不满足项目版本要求，先按
`ci/toolchain.lock.yaml` 和模块 README 切换 Java/Node/Maven 版本。
