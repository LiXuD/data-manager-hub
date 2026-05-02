package com.dataplatform.caller;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.dataplatform")
@EnableDiscoveryClient
@MapperScan("com.dataplatform.caller.mapper")
public class CallerApplication {
    public static void main(String[] args) {
        SpringApplication.run(CallerApplication.class, args);
    }
}
