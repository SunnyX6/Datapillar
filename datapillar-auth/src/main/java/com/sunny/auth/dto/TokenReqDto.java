package com.sunny.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Token请求DTO
 * 用于：刷新Token、验证Token
 */
@Data
public class TokenReqDto {
    @NotBlank(message = "Token 不能为空")
    private String token;

    private String refreshToken;
}
