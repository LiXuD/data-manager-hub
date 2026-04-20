package com.dataplatform.role;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.dataplatform.role.mapper")
public class RoleApplication {
    public static void main(String[] args) {
        SpringApplication.run(RoleApplication.class, args);
    }
}
