package com.sunny.datapillar.auth.sso.model;

import java.util.Map;

import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SsoProviderConfig {
    private String provider;
    private String baseUrl;
    private Map<String, Object> config;

    public String getRequiredString(String key) {
        Object value = config != null ? config.get(key) : null;
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException(ErrorCode.SSO_CONFIG_INVALID, key);
        }
        return String.valueOf(value).trim();
    }

    public String getOptionalString(String key) {
        Object value = config != null ? config.get(key) : null;
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }
}
