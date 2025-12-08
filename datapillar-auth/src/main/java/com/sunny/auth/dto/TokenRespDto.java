package com.sunny.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token响应DTO
 * 用于：Token验证结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenRespDto {
    private boolean valid;
    private Long userId;
    private String username;
    private String email;
    private String errorMessage;

    public static TokenRespDto success(Long userId, String username, String email) {
        TokenRespDto dto = new TokenRespDto();
        dto.setValid(true);
        dto.setUserId(userId);
        dto.setUsername(username);
        dto.setEmail(email);
        return dto;
    }

    public static TokenRespDto failure(String errorMessage) {
        TokenRespDto dto = new TokenRespDto();
        dto.setValid(false);
        dto.setErrorMessage(errorMessage);
        return dto;
    }
}
