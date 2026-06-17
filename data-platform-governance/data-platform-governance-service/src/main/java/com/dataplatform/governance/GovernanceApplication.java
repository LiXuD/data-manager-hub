package com.dataplatform.governance;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * 观测治理域的 Governance Application。
 * <p>Spring Boot 启动入口，限定本服务的组件扫描、Mapper 扫描和 Feign 客户端边界。</p>
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan({
        "com.dataplatform.governance.monitor.mapper",
        "com.dataplatform.governance.log.mapper",
        "com.dataplatform.governance.quality.mapper",
        "com.dataplatform.governance.trace.mapper"
})
@ComponentScan(basePackages = {
        "com.dataplatform.governance",
        "com.dataplatform.governance.monitor",
        "com.dataplatform.governance.log",
        "com.dataplatform.governance.quality",
        "com.dataplatform.governance.trace"
})
public class GovernanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GovernanceApplication.class, args);
    }
}
