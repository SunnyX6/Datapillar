package com.sunny.datapillar.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Datapillar Platform 服务启动类
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2026-02-03
 */
@SpringBootApplication
@MapperScan("com.sunny.datapillar.platform.module.*.mapper")
public class DatapillarPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(DatapillarPlatformApplication.class, args);
    }
}
