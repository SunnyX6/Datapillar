package com.sunny.datapillar.auth.sso.model;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SsoToken {
    private String accessToken;
    private Long expiresIn;
    private Map<String, Object> raw;
}
