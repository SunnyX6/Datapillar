package com.sunny.datapillar.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Datapillar API 网关 - 统一入口
 *
 * <p>功能：
 * <ul>
 *   <li>统一路由转发</li>
 *   <li>跨域处理</li>
 *   <li>限流熔断</li>
 *   <li>统一日志</li>
 * </ul>
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
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
                "  API Gateway 启动成功！端口：6000\n" +
                "=======================================================\n");
    }
}
