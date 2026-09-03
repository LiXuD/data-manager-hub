package com.dataplatform.access;

import com.dataplatform.billing.api.feign.BillingInternalFeignClient;
import com.dataplatform.access.caller.api.feign.CallerInternalFeignClient;
import com.dataplatform.governance.log.api.LogClient;
import com.dataplatform.identity.api.feign.IdentityAccessInternalFeignClient;
import com.dataplatform.masterdata.graylog.api.feign.GraylogInternalFeignClient;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import com.dataplatform.masterdata.connector.api.feign.ConnectorPluginInternalFeignClient;
import com.dataplatform.masterdata.connector.api.feign.VendorConnectorInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorSecurityInternalFeignClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 访问域的 Access Application。
 * <p>Spring Boot 启动入口，限定本服务的组件扫描、Mapper 扫描和 Feign 客户端边界。</p>
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@EnableFeignClients(clients = {
        ApiInterfaceFeignClient.class,
        VendorConfigInternalFeignClient.class,
        VendorInternalFeignClient.class,
        VendorSecurityInternalFeignClient.class,
        ConnectorPluginInternalFeignClient.class,
        VendorConnectorInternalFeignClient.class,
        GraylogInternalFeignClient.class,
        BillingInternalFeignClient.class,
        CallerInternalFeignClient.class,
        IdentityAccessInternalFeignClient.class,
        LogClient.class
})
@MapperScan({
        "com.dataplatform.access.caller.mapper",
        "com.dataplatform.access.call.mapper",
        "com.dataplatform.access.approval.mapper",
        "com.dataplatform.access.connector.mapper"
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
