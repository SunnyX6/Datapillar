package com.sunny.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Datapillar Auth 服务启动类
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@SpringBootApplication
@MapperScan("com.sunny.auth.mapper")
public class DatapillarAuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(DatapillarAuthApplication.class, args);
    }
}
