package com.dataplatform.call;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.dataplatform")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.dataplatform")
@MapperScan("com.dataplatform.call.mapper")
public class CallApplication {
    public static void main(String[] args) {
        SpringApplication.run(CallApplication.class, args);
    }
}
