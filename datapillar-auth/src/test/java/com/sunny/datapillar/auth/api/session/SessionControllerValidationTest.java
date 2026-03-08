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

class SessionControllerValidationTest {

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
  void simpleLoginRequest_shouldAllowMissingTenantCode() {
    SessionController.SimpleLoginRequest request =
        new SessionController.SimpleLoginRequest("sunny", "123456asd", null, Boolean.TRUE);

    Set<?> violations = validator.validate(request);

    assertTrue(violations.isEmpty());
  }

  @Test
  void simpleLoginRequest_shouldStillRequireLoginAliasAndPassword() {
    SessionController.SimpleLoginRequest request =
        new SessionController.SimpleLoginRequest("", "", null, Boolean.FALSE);

    var violations = validator.validate(request);

    assertEquals(2, violations.size());
  }
}
