package com.sunny.datapillar.auth.dto.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(name = "AuthTokenRequest")
public class TokenRequest {

  @NotBlank(message = "Token must not be blank")
  private String token;

  private String refreshToken;
}
