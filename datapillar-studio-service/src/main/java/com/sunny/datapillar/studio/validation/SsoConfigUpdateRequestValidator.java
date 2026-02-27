package com.sunny.datapillar.studio.validation;

import com.sunny.datapillar.studio.dto.tenant.request.SsoConfigUpdateRequest;
import com.sunny.datapillar.studio.dto.tenant.response.SsoDingtalkConfigItem;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

public class SsoConfigUpdateRequestValidator implements ConstraintValidator<ValidSsoConfigUpdateRequest, SsoConfigUpdateRequest> {

    @Override
    public boolean isValid(SsoConfigUpdateRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        SsoDingtalkConfigItem config = request.getConfig();
        if (config == null) {
            return true;
        }

        if (!StringUtils.hasText(config.getClientId()) || !StringUtils.hasText(config.getRedirectUri())) {
            return false;
        }
        return config.getClientSecret() == null || StringUtils.hasText(config.getClientSecret());
    }
}
