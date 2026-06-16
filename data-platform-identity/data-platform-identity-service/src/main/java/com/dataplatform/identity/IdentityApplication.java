package com.dataplatform.identity;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

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
