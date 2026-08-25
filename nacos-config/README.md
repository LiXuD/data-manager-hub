# Nacos 配置中心

应用本地配置只保留应用名、Profile 和 Nacos 连接信息。运行期配置由
`spring.config.import` 从对应 namespace 和 Group 加载：dev 的本地默认值是
`DEFAULT_GROUP`；staging/prod 的 CI/CD 发布必须使用绑定 source SHA 的不可变 Group，
并把同一个 `NACOS_GROUP` 注入应用 Pod。不要把不可变 Group 发布后再让应用回退到
`DEFAULT_GROUP`，否则启动时会被视为缺少配置。

## Data ID

- 五个业务域共同加载 `data-platform-database-{profile}.properties`。
- 每个服务加载自己的 `data-platform-{service}-{profile}.yml`。
- Gateway 不加载数据库 Data ID。

## 发布

```bash
./publish-nacos-config.sh dev
```

脚本在 `NACOS_MODE=apply` 下按环境写入 namespace 和 Group：dev 使用约定的
`DEFAULT_GROUP`，staging/prod 使用绑定 source SHA 的不可变 Group；同一 Group 已存在且
内容完全一致时幂等返回，内容漂移或部分发布会 fail-closed。`NACOS_MODE=plan` 只渲染并
输出摘要，`NACOS_MODE=verify` 只读校验所有 Data ID。开发环境还会在
`.runtime/` 生成内部认证 RSA 密钥和字段加密主密钥，渲染后再发布到 Nacos；
密钥内容不会写回版本库。服务进程通过同一组环境变量读取这些值，避免把本机绝对路径
写进配置。Kubernetes 中则必须把 `dmh-internal-auth` Secret 的 `public.pem`、
`private.pem` 以只读卷挂载到 `/run/secrets/dmh/internal-auth/`，并提供
`PLATFORM_ENCRYPTION_MASTER_KEY`。

生产配置只保留环境变量占位符。生产发布由受保护的 CI/CD Job 完成；它必须先用
`NACOS_MODE=plan` 离线渲染，再用 `NACOS_MODE=apply` 发布到
`DMH_PROD_<40-char-master-sha>`，最后用 `NACOS_MODE=verify` 只读复核。部署 Helm
Release 的 `global.nacosGroup` 必须与该值完全相同。发布前必须由部署环境提供数据库、
Redis、Kafka 和内部认证密钥等变量：

staging/production 的 Helm Chart 和 `check-helm-policy.py` 会拒绝 `DMH_*_LOCAL` 等可变 Group；
只有绑定当前 master SHA 的 Group 才能通过部署前渲染门禁。

`docs/DEPLOYMENT.md` 仍保留当前手工/本地部署说明（包括其 `DEFAULT_GROUP` 约定），不等同于
新的生产 CD 流程；不要把手工 Group 和 CI/CD 的 immutable Group 混用。

```bash
release_sha="<40-char-master-sha>"
NACOS_GROUP="DMH_PROD_${release_sha}" \
NACOS_SERVER_ADDR=nacos.example:8848 \
NACOS_NAMESPACE=prod \
NACOS_USERNAME=... \
NACOS_PASSWORD=... \
./publish-nacos-config.sh prod
```

可以通过 `NACOS_CONFIG_DRY_RUN=true` 仅检查待发布的 Data ID。
该 dry-run 即使使用 `NACOS_MODE=apply` 也不会访问 Nacos 或创建 namespace；staging/prod 不需要
提供可达的 `NACOS_SERVER_ADDR`，只输出与正式渲染相同的 Data ID 摘要。真正的 `apply` 必须关闭
dry-run，并继续经过不可变 Group、非 loopback 地址和内容漂移门禁。
