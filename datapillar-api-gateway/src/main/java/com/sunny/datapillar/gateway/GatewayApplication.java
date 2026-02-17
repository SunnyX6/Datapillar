package com.sunny.datapillar.gateway;

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
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
        System.out.println("\n" +
                "=======================================================\n" +
                "   ____        _              _ _ _            \n" +
                "  |  _ \\  __ _| |_ __ _ _ __ (_) | | __ _ _ __ \n" +
                "  | | | |/ _` | __/ _` | '_ \\| | | |/ _` | '__|\n" +
                "  | |_| | (_| | || (_| | |_) | | | | (_| | |   \n" +
                "  |____/ \\__,_|\\__\\__,_| .__/|_|_|_|\\__,_|_|   \n" +
                "                       |_|                      \n" +
                "  API Gateway 启动成功！端口：7000\n" +
                "=======================================================\n");
    }
}
