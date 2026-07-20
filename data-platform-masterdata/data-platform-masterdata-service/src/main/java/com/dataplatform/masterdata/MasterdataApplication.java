package com.dataplatform.masterdata;

import com.dataplatform.access.call.api.feign.CallStatsInternalFeignClient;
import com.dataplatform.governance.log.api.LogClient;
import com.dataplatform.identity.api.feign.EncryptionInternalFeignClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 主数据域的 Masterdata Application。
 * <p>Spring Boot 启动入口，限定本服务的组件扫描、Mapper 扫描和 Feign 客户端边界。</p>
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(clients = {
        CallStatsInternalFeignClient.class,
        EncryptionInternalFeignClient.class,
        LogClient.class
})
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
