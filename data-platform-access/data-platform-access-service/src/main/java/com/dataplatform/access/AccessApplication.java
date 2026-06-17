package com.dataplatform.access;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 访问域的 Access Application。
 * <p>Spring Boot 启动入口，限定本服务的组件扫描、Mapper 扫描和 Feign 客户端边界。</p>
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {
        "com.dataplatform.masterdata",
        "com.dataplatform.billing.api",
        "com.dataplatform.governance.log.api"
})
@MapperScan({
        "com.dataplatform.access.caller.mapper",
        "com.dataplatform.access.call.mapper"
})
@ComponentScan(basePackages = {
        "com.dataplatform.access",
        "com.dataplatform.access.caller",
        "com.dataplatform.access.call"
})
public class AccessApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccessApplication.class, args);
    }
}
