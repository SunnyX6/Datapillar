package com.sunny.datapillar.studio.validation;

import com.sunny.datapillar.studio.dto.tenant.request.SsoConfigCreateRequest;
import com.sunny.datapillar.studio.dto.tenant.response.SsoDingtalkConfigItem;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

public class SsoConfigCreateRequestValidator implements ConstraintValidator<ValidSsoConfigCreateRequest, SsoConfigCreateRequest> {

    @Override
    public boolean isValid(SsoConfigCreateRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return false;
        }
        SsoDingtalkConfigItem config = request.getConfig();
        if (config == null) {
            return false;
        }
        return StringUtils.hasText(config.getClientId())
                && StringUtils.hasText(config.getClientSecret())
                && StringUtils.hasText(config.getRedirectUri());
    }
}
