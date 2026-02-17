package com.sunny.datapillar.auth;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 认证启动类
 * 负责服务启动与基础组件装配
 *
 * @author Sunny
 * @date 2026-01-01
 */
@SpringBootApplication
@EnableDubbo
@MapperScan("com.sunny.datapillar.auth.mapper")
public class DatapillarAuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(DatapillarAuthApplication.class, args);
    }
}
