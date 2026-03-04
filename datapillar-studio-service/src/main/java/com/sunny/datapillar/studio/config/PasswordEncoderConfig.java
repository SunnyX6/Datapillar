package com.sunny.datapillar.studio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * PasswordEncoderConfiguration Responsible for the passwordEncoderConfigure assembly
 * withBeaninitialization
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Configuration
public class PasswordEncoderConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new Argon2PasswordEncoder(16, 32, 1, 64 * 1024, 3);
  }
}
