package com.sunny.datapillar.auth.validation;

import com.sunny.datapillar.auth.dto.login.request.LoginRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Locale;
import org.springframework.util.StringUtils;

public class LoginRequestValidator implements ConstraintValidator<ValidLoginRequest, LoginRequest> {

    private static final String STAGE_AUTH = "AUTH";
    private static final String STAGE_TENANT_SELECT = "TENANT_SELECT";

    private ValidLoginRequest.LoginMode mode;

    @Override
    public void initialize(ValidLoginRequest constraintAnnotation) {
        this.mode = constraintAnnotation.mode();
    }

    @Override
    public boolean isValid(LoginRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return false;
        }

        String stage = normalizeStage(request.getStage());
        if (STAGE_AUTH.equals(stage)) {
            return validateAuthStage(request);
        }
        if (STAGE_TENANT_SELECT.equals(stage)) {
            return request.getTenantId() != null && request.getTenantId() > 0;
        }
        return false;
    }

    private boolean validateAuthStage(LoginRequest request) {
        if (mode == ValidLoginRequest.LoginMode.PASSWORD) {
            return StringUtils.hasText(request.getLoginAlias())
                    && StringUtils.hasText(request.getPassword());
        }
        return !StringUtils.hasText(request.getLoginAlias())
                && !StringUtils.hasText(request.getPassword())
                && StringUtils.hasText(request.getProvider())
                && StringUtils.hasText(request.getCode())
                && StringUtils.hasText(request.getState());
    }

    private String normalizeStage(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
