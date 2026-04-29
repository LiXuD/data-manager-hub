package com.dataplatform.datatype;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.dataplatform")
@MapperScan("com.dataplatform.datatype.mapper")
public class DataTypeApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataTypeApplication.class, args);
    }
}