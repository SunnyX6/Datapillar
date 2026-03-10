package com.sunny.datapillar.openlineage.web;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sunny.datapillar.openlineage.web.dto.request.RebuildRequest;
import com.sunny.datapillar.openlineage.web.dto.request.SearchRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class OpenLineageControllerTest {

  @Test
  void rebuild_shouldRejectAiModelIdField() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    RebuildRequest request = new RebuildRequest(99L);

    assertThrows(
        jakarta.validation.ConstraintViolationException.class,
        () -> validateOrThrow(validator, request));
  }

  @Test
  void search_shouldRejectAiModelIdField() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    SearchRequest request = new SearchRequest("orders", 10, 0.1D, 99L);

    assertThrows(
        jakarta.validation.ConstraintViolationException.class,
        () -> validateOrThrow(validator, request));
  }

  private void validateOrThrow(LocalValidatorFactoryBean validator, Object request) {
    var violations = validator.validate(request);
    if (!violations.isEmpty()) {
      throw new jakarta.validation.ConstraintViolationException(violations);
    }
  }
}
