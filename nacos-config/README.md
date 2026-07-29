# Nacos 配置中心

应用本地配置只保留应用名、Profile 和 Nacos 连接信息。运行期配置由
`spring.config.import` 从对应 namespace 的 `DEFAULT_GROUP` 加载。

## Data ID

- 五个业务域共同加载 `data-platform-database-{profile}.properties`。
- 每个服务加载自己的 `data-platform-{service}-{profile}.yml`。
- Gateway 不加载数据库 Data ID。

## 发布

```bash
./publish-nacos-config.sh dev
```

脚本会幂等创建 namespace 并覆盖发布对应 Profile 下的配置。开发环境还会在
`.runtime/` 生成内部认证 RSA 密钥和字段加密主密钥，渲染后再发布到 Nacos；
密钥内容不会写回版本库。

生产配置只保留环境变量占位符。发布生产配置前必须由部署环境提供数据库、
Redis、Kafka 和内部认证密钥等变量：

```bash
NACOS_SERVER_ADDR=nacos.example:8848 \
NACOS_NAMESPACE=prod \
NACOS_USERNAME=... \
NACOS_PASSWORD=... \
./publish-nacos-config.sh prod
```

可以通过 `NACOS_CONFIG_DRY_RUN=true` 仅检查待发布的 Data ID。
