package com.sunny.datapillar.workbench;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Datapillar Workbench 服务启动类
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@SpringBootApplication
@MapperScan("com.sunny.datapillar.workbench.module.*.mapper")
public class DatapillarWorkbenchApplication {
    public static void main(String[] args) {
        SpringApplication.run(DatapillarWorkbenchApplication.class, args);
    }
}
