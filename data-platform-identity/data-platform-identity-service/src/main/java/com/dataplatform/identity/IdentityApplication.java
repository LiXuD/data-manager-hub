package com.dataplatform.identity;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 身份租户域的 Identity Application。
 * <p>Spring Boot 启动入口，限定本服务的组件扫描、Mapper 扫描和 Feign 客户端边界。</p>
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@EnableFeignClients(basePackages = "com.dataplatform.governance.log.api")
@MapperScan({
        "com.dataplatform.identity.tenant.mapper",
        "com.dataplatform.identity.iam.mapper"
})
@ComponentScan(basePackages = {
        "com.dataplatform.identity",
        "com.dataplatform.identity.tenant",
        "com.dataplatform.identity.iam",
        "com.dataplatform.identity.security"
})
public class IdentityApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityApplication.class, args);
    }
}
