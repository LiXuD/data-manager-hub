package com.dataplatform.access.call.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

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
    public static final String CALL_RECORD_DLT_TOPIC = "call-record.DLT";
    
    @Bean
    public NewTopic callRecordTopic() {
        return TopicBuilder.name(CALL_RECORD_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic callRecordDeadLetterTopic() {
        return TopicBuilder.name(CALL_RECORD_DLT_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    }
}
