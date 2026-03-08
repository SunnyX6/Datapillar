package com.sunny.datapillar.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Auth bootstrap class for service startup and base component wiring.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@SpringBootApplication
@MapperScan("com.sunny.datapillar.auth.mapper")
public class DatapillarAuthApplication {
  public static void main(String[] args) {
    SpringApplication.run(DatapillarAuthApplication.class, args);
  }
}
