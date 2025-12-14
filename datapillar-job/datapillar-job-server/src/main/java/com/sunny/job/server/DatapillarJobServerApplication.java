package com.sunny.job.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Datapillar Job Server 启动类
 * @author SunnyX6
 * @date 2025-12-13
 */
@EnableAsync
@SpringBootApplication
public class DatapillarJobServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DatapillarJobServerApplication.class, args);
    }
}
