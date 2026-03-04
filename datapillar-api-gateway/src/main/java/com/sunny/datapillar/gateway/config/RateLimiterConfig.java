package com.sunny.datapillar.gateway.config;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.gateway.security.ClientIpResolver;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Current limiter configuration Responsible for the configuration and assembly of current
 * limitersBeaninitialization
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Configuration
public class RateLimiterConfig {

  private final ClientIpResolver clientIpResolver;

  public RateLimiterConfig(ClientIpResolver clientIpResolver) {
    this.clientIpResolver = clientIpResolver;
  }

  /** Based on IP current limit Key parser */
  @Bean
  @Primary
  public KeyResolver ipKeyResolver() {
    return exchange -> Mono.just(clientIpResolver.resolve(exchange.getRequest()));
  }

  /** user based ID current limit Key parser For post-authentication requests */
  @Bean
  public KeyResolver userKeyResolver() {
    return exchange -> {
      String userId = exchange.getRequest().getHeaders().getFirst(HeaderConstants.HEADER_USER_ID);
      if (userId != null && !userId.isEmpty()) {
        return Mono.just("user:" + userId);
      }
      // Use by unauthenticated users IP
      return ipKeyResolver().resolve(exchange).map(ip -> "ip:" + ip);
    };
  }
}
