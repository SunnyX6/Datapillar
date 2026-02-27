package com.sunny.datapillar.studio.dto.tenant;

import com.sunny.datapillar.studio.dto.tenant.request.SsoConfigCreateRequest;
import com.sunny.datapillar.studio.dto.tenant.request.SsoConfigUpdateRequest;
import com.sunny.datapillar.studio.dto.tenant.request.SsoIdentityBindByCodeRequest;
import com.sunny.datapillar.studio.dto.tenant.response.SsoDingtalkConfigItem;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SsoDtoValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void createRequestShouldPassValidation() {
        SsoConfigCreateRequest request = new SsoConfigCreateRequest();
        request.setProvider("dingtalk");
        request.setStatus(1);
        request.setConfig(buildConfig("client", "secret", "https://redirect"));

        Set<ConstraintViolation<SsoConfigCreateRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void createRequestShouldFailWhenConfigMissing() {
        SsoConfigCreateRequest request = new SsoConfigCreateRequest();
        request.setProvider("dingtalk");

        Set<ConstraintViolation<SsoConfigCreateRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
    }

    @Test
    void updateRequestShouldFailWhenClientSecretIsBlank() {
        SsoConfigUpdateRequest request = new SsoConfigUpdateRequest();
        request.setConfig(buildConfig("client", " ", "https://redirect"));

        Set<ConstraintViolation<SsoConfigUpdateRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
    }

    @Test
    void updateRequestShouldFailWhenStatusOutOfRange() {
        SsoConfigUpdateRequest request = new SsoConfigUpdateRequest();
        request.setStatus(2);

        Set<ConstraintViolation<SsoConfigUpdateRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
    }

    @Test
    void bindByCodeRequestShouldRejectUnsupportedProvider() {
        SsoIdentityBindByCodeRequest request = new SsoIdentityBindByCodeRequest();
        request.setUserId(1L);
        request.setProvider("wechat");
        request.setAuthCode("code-1");

        Set<ConstraintViolation<SsoIdentityBindByCodeRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
    }

    private SsoDingtalkConfigItem buildConfig(String clientId, String clientSecret, String redirectUri) {
        SsoDingtalkConfigItem config = new SsoDingtalkConfigItem();
        config.setClientId(clientId);
        config.setClientSecret(clientSecret);
        config.setRedirectUri(redirectUri);
        return config;
    }
}
