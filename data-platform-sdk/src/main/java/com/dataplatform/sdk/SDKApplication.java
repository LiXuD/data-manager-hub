package com.dataplatform.sdk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class SDKApplication {
    public static void main(String[] args) {
        SpringApplication.run(SDKApplication.class, args);
    }
}