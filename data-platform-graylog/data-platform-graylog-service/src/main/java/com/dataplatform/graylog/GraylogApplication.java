package com.dataplatform.graylog;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.dataplatform")
@MapperScan("com.dataplatform.graylog.mapper")
public class GraylogApplication {
    public static void main(String[] args) {
        SpringApplication.run(GraylogApplication.class, args);
    }
}
