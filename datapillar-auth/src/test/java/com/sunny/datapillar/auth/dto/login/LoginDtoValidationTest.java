package com.sunny.datapillar.auth.dto.login;

import com.sunny.datapillar.auth.dto.login.request.LoginRequest;
import com.sunny.datapillar.auth.validation.ValidLoginRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.executable.ExecutableValidator;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginDtoValidationTest {

    private Validator validator;
    private ExecutableValidator executableValidator;
    private LoginEndpoint endpoint;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        executableValidator = validator.forExecutables();
        endpoint = new LoginEndpoint();
    }

    @Test
    void passwordAuthRequestShouldPassValidation() throws NoSuchMethodException {
        LoginRequest request = new LoginRequest();
        request.setStage("auth");
        request.setLoginAlias("sunny");
        request.setPassword("123456");

        Set<ConstraintViolation<LoginEndpoint>> violations = validate("password", request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void passwordAuthRequestShouldFailWhenPasswordMissing() throws NoSuchMethodException {
        LoginRequest request = new LoginRequest();
        request.setStage("AUTH");
        request.setLoginAlias("sunny");

        Set<ConstraintViolation<LoginEndpoint>> violations = validate("password", request);

        assertFalse(violations.isEmpty());
    }

    @Test
    void tenantSelectRequestShouldPassForPasswordFlow() throws NoSuchMethodException {
        LoginRequest request = new LoginRequest();
        request.setStage("TENANT_SELECT");
        request.setTenantId(10L);

        Set<ConstraintViolation<LoginEndpoint>> violations = validate("password", request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void ssoAuthRequestShouldFailWhenPasswordPayloadExists() throws NoSuchMethodException {
        LoginRequest request = new LoginRequest();
        request.setStage("AUTH");
        request.setProvider("dingtalk");
        request.setCode("code-1");
        request.setState("state-1");
        request.setLoginAlias("sunny");

        Set<ConstraintViolation<LoginEndpoint>> violations = validate("sso", request);

        assertFalse(violations.isEmpty());
    }

    @Test
    void ssoAuthRequestShouldPassValidation() throws NoSuchMethodException {
        LoginRequest request = new LoginRequest();
        request.setStage("AUTH");
        request.setProvider("dingtalk");
        request.setCode("code-1");
        request.setState("state-1");

        Set<ConstraintViolation<LoginEndpoint>> violations = validate("sso", request);

        assertTrue(violations.isEmpty());
    }

    private Set<ConstraintViolation<LoginEndpoint>> validate(String methodName, LoginRequest request)
            throws NoSuchMethodException {
        Method method = LoginEndpoint.class.getDeclaredMethod(methodName, LoginRequest.class);
        return executableValidator.validateParameters(endpoint, method, new Object[]{request});
    }

    private static class LoginEndpoint {

        void password(@Valid @ValidLoginRequest(mode = ValidLoginRequest.LoginMode.PASSWORD) LoginRequest request) {
        }

        void sso(@Valid @ValidLoginRequest(mode = ValidLoginRequest.LoginMode.SSO) LoginRequest request) {
        }
    }
}
