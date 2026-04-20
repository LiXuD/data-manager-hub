package com.dataplatform.vendor;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.dataplatform")
@EnableDiscoveryClient
@MapperScan("com.dataplatform.vendor.mapper")
public class VendorApplication {
    public static void main(String[] args) {
        SpringApplication.run(VendorApplication.class, args);
    }
}
