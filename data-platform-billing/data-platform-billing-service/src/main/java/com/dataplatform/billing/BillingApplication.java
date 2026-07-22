package com.dataplatform.billing;

import com.dataplatform.access.call.api.feign.CallStatsInternalFeignClient;
import com.dataplatform.governance.api.feign.GovernanceInternalFeignClient;
import com.dataplatform.governance.log.api.LogClient;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorInternalFeignClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 计费域计费计算的 Billing Application。
 * <p>Spring Boot 启动入口，限定本服务的组件扫描、Mapper 扫描和 Feign 客户端边界。</p>
 */
@SpringBootApplication
@EnableFeignClients(clients = {
        CallStatsInternalFeignClient.class,
        GovernanceInternalFeignClient.class,
        LogClient.class,
        ApiInterfaceFeignClient.class,
        VendorInternalFeignClient.class
})
@EnableDiscoveryClient
@MapperScan("com.dataplatform.billing.mapper")
public class BillingApplication {
    public static void main(String[] args) {
        SpringApplication.run(BillingApplication.class, args);
    }
}
