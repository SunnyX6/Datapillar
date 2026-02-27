package com.sunny.datapillar.auth.dto.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(name = "AuthTokenRequest")
public class TokenRequest {

    @NotBlank(message = "Token 不能为空")
    private String token;

    private String refreshToken;
}
