# CallRecord 异步落库观测

Access 服务的 `CallRecord` 仍通过 Kafka 异步落库。成功响应后短时间内查询不到记录是最终一致性窗口，不应通过同步写入掩盖消费者故障。

## 指标

- `call_record_consumer_events_total{outcome="stored|duplicate|failed|malformed"}`：消费者处理结果。
- `call_record_consumer_processing_latency_seconds{outcome="..."}`：单条消息从进入消费者到处理结束的耗时。
- `call_record_persistence_latency_seconds{outcome="stored|failed"}`：数据库写入耗时。
- `call_record_end_to_end_persistence_latency_seconds{outcome="stored|duplicate"}`：事件 `callTime` 到消费者处理结束的耗时；缺少或未来时间戳不会伪造样本。
- `call_record_consumer_dlt_published_total`：错误处理器确认进入 DLT 发布流程的次数。

Spring Kafka 的 consumer client metrics 继续提供 topic/partition 级 lag；部署时须确认 `call-record` 的 lag 指标已被 Prometheus 抓取。端到端延迟和 lag 的生产告警阈值必须基于 staging/production 观察数据与 SLO 设定，本仓库不把 Dev 的 30 秒轮询门禁直接当作生产阈值。

## 失败处置

消费者失败会重新抛出异常，沿用三次重试后投递 `call-record.DLT` 的错误处理链；Malformed payload、数据库失败和 DLT 均有独立信号。告警处理应保留 requestId/traceId 的脱敏检索入口，不把明文 API Key 或请求参数写入指标标签。
