package com.dataplatform.access.call.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

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