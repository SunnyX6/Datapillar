package com.sunny.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 信息响应
 * 用于前端 Token 管理器查询当前 Token 的状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenInfoRespDto {

    /**
     * Token 是否有效
     */
    private Boolean valid;

    /**
     * Token 剩余有效时间（秒）
     */
    private Long remainingSeconds;

    /**
     * Token 过期时间戳（毫秒）
     */
    private Long expirationTime;

    /**
     * Token 颁发时间戳（毫秒）
     */
    private Long issuedAt;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;
}
