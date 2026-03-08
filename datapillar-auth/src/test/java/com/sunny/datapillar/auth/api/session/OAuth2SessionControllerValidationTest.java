package com.sunny.datapillar.auth.api.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OAuth2SessionControllerValidationTest {

  private static ValidatorFactory validatorFactory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    validatorFactory = Validation.buildDefaultValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    if (validatorFactory != null) {
      validatorFactory.close();
    }
  }

  @Test
  void oauth2LoginRequest_shouldAllowMissingTenantCode() {
    OAuth2SessionController.OAuth2LoginRequest request =
        new OAuth2SessionController.OAuth2LoginRequest(
            "dingtalk", "code-1", "state-1", "nonce-1", "verifier-1", null, Boolean.TRUE);

    Set<?> violations = validator.validate(request);

    assertTrue(violations.isEmpty());
  }

  @Test
  void oauth2LoginRequest_shouldRequireOauth2Fields() {
    OAuth2SessionController.OAuth2LoginRequest request =
        new OAuth2SessionController.OAuth2LoginRequest("", "", "", "", "", null, Boolean.FALSE);

    var violations = validator.validate(request);

    assertEquals(5, violations.size());
  }
}
