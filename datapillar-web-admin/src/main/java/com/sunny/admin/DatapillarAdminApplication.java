package com.sunny.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Datapillar Admin 服务启动类
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@SpringBootApplication(scanBasePackages = "com.sunny")
@MapperScan("com.sunny.admin.module.*.mapper")
public class DatapillarAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(DatapillarAdminApplication.class, args);
    }
}
