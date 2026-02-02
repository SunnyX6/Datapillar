package com.sunny.datapillar.auth.sso.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SsoUserInfo {
    private String externalUserId;
    private String unionId;
    private String openId;
    private String mobile;
    private String email;
    private String nick;
    private String corpId;
    private String rawJson;
}
