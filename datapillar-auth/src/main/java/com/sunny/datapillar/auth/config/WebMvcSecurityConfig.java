package com.sunny.datapillar.auth.config;

import com.sunny.datapillar.auth.security.AuthCsrfInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc security configuration for interceptor registration and setup.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Configuration
public class WebMvcSecurityConfig implements WebMvcConfigurer {

  private final AuthCsrfInterceptor authCsrfInterceptor;

  public WebMvcSecurityConfig(AuthCsrfInterceptor authCsrfInterceptor) {
    this.authCsrfInterceptor = authCsrfInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(authCsrfInterceptor).addPathPatterns("/auth/**", "/oauth2/**");
  }
}
