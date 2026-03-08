package com.sunny.datapillar.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sunny.datapillar.auth.api.session.OAuth2SessionController;
import com.sunny.datapillar.auth.api.session.OAuth2TokenController;
import com.sunny.datapillar.auth.api.session.SessionController;
import com.sunny.datapillar.auth.api.wellknown.OpenIdConfigurationController;
import com.sunny.datapillar.auth.api.wellknown.WellKnownController;
import com.sunny.datapillar.auth.config.AuthProperties;
import com.sunny.datapillar.auth.service.SessionAppService;
import com.sunny.datapillar.auth.service.TokenAppService;
import com.sunny.datapillar.auth.token.TokenEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class OAuth2EndpointConditionTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(TestConfiguration.class);

  @Test
  void simpleAuthenticator_shouldNotRegisterOauth2Endpoints() {
    contextRunner
        .withPropertyValues("auth.authenticator=simple")
        .run(
            context -> {
              assertThat(context).hasSingleBean(SessionController.class);
              assertThat(context).hasSingleBean(WellKnownController.class);
              assertThat(context).doesNotHaveBean(OAuth2SessionController.class);
              assertThat(context).doesNotHaveBean(OAuth2TokenController.class);
              assertThat(context).doesNotHaveBean(OpenIdConfigurationController.class);
            });
  }

  @Test
  void oauth2Authenticator_shouldRegisterOauth2Endpoints() {
    contextRunner
        .withPropertyValues("auth.authenticator=oauth2")
        .run(
            context -> {
              assertThat(context).hasSingleBean(SessionController.class);
              assertThat(context).hasSingleBean(WellKnownController.class);
              assertThat(context).hasSingleBean(OAuth2SessionController.class);
              assertThat(context).hasSingleBean(OAuth2TokenController.class);
              assertThat(context).hasSingleBean(OpenIdConfigurationController.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(AuthProperties.class)
  @Import({
    SessionController.class,
    OAuth2SessionController.class,
    OAuth2TokenController.class,
    WellKnownController.class,
    OpenIdConfigurationController.class
  })
  static class TestConfiguration {

    @Bean
    SessionAppService sessionAppService() {
      return mock(SessionAppService.class);
    }

    @Bean
    TokenAppService tokenAppService() {
      return mock(TokenAppService.class);
    }

    @Bean
    TokenEngine tokenEngine() {
      return mock(TokenEngine.class);
    }
  }
}
