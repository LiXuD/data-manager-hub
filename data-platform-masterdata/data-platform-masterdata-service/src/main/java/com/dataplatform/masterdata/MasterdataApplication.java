package com.dataplatform.masterdata;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.dataplatform.governance.log.api")
@MapperScan({
        "com.dataplatform.masterdata.vendor.mapper",
        "com.dataplatform.masterdata.interface_.mapper",
        "com.dataplatform.masterdata.graylog.mapper"
})
@ComponentScan(basePackages = {
        "com.dataplatform.masterdata",
        "com.dataplatform.masterdata.vendor",
        "com.dataplatform.masterdata.interface_",
        "com.dataplatform.masterdata.graylog"
})
public class MasterdataApplication {

    public static void main(String[] args) {
        SpringApplication.run(MasterdataApplication.class, args);
    }
}
