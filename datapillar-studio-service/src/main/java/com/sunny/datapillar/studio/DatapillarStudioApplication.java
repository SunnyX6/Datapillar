package com.sunny.datapillar.studio;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * StudioStartup class Responsible for service startup and basic component assembly
 *
 * @author Sunny
 * @date 2026-01-01
 */
@SpringBootApplication
@EnableDubbo
public class DatapillarStudioApplication {
  public static void main(String[] args) {
    SpringApplication.run(DatapillarStudioApplication.class, args);
  }
}
