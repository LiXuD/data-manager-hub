package com.dataplatform.access.call.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 访问域数据调用的 Kafka Config。
 * <p>配置组件，集中声明本模块运行所需的 Spring Bean 或框架参数。</p>
 */
@Configuration
public class KafkaConfig {
    
    /**
     * 调用记录Topic
     */
    public static final String CALL_RECORD_TOPIC = "call-record";
    
    @Bean
    public NewTopic callRecordTopic() {
        return TopicBuilder.name(CALL_RECORD_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}