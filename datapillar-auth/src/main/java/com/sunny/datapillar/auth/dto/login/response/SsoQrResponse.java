package com.sunny.datapillar.auth.dto.login.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AuthSsoQrResponse")
public class SsoQrResponse {

    private String type;

    private String state;

    private Map<String, Object> payload;
}
