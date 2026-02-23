package com.sunny.datapillar.gateway;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关启动类
 * 负责服务启动与基础组件装配
 *
 * @author Sunny
 * @date 2026-01-01
 */
@SpringBootApplication
@EnableDubbo
public class DatapillarGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(DatapillarGatewayApplication.class, args);
    }
}
