package com.sunny.datapillar.auth.security;

import java.time.Duration;

import com.sunny.datapillar.auth.config.AuthSecurityProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import lombok.extern.slf4j.Slf4j;
import com.sunny.datapillar.common.exception.TooManyRequestsException;
/**
 * 登录AttemptTracker组件
 * 负责登录AttemptTracker核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */

@Component
@Slf4j
public class LoginAttemptTracker {

    private static final String FAIL_PREFIX = "auth:login:fail:";
    private static final String LOCK_PREFIX = "auth:login:lock:";

    private final StringRedisTemplate stringRedisTemplate;
    private final AuthSecurityProperties securityProperties;

    public LoginAttemptTracker(StringRedisTemplate stringRedisTemplate, AuthSecurityProperties securityProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.securityProperties = securityProperties;
    }

    public void assertLoginAllowed(String tenantCode, String username, String clientIp) {
        if (!securityProperties.getLogin().isEnabled()) {
            return;
        }
        String lockKey = buildLockKey(tenantCode, username, clientIp);
        String locked = stringRedisTemplate.opsForValue().get(lockKey);
        if (locked != null) {
            log.warn("security_event event=login_locked tenantCode={} username={} clientIp={}",
                    tenantCode, username, clientIp);
            throw new TooManyRequestsException("登录失败次数过多，请稍后重试");
        }
    }

    public void recordFailure(String tenantCode, String username, String clientIp) {
        if (!securityProperties.getLogin().isEnabled()) {
            return;
        }
        String failKey = buildFailKey(tenantCode, username, clientIp);
        Long count = stringRedisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(failKey, Duration.ofSeconds(securityProperties.getLogin().getWindowSeconds()));
        }
        if (count != null && count >= securityProperties.getLogin().getMaxAttempts()) {
            String lockKey = buildLockKey(tenantCode, username, clientIp);
            stringRedisTemplate.opsForValue().set(lockKey, "1", Duration.ofSeconds(securityProperties.getLogin().getLockSeconds()));
            log.warn("security_event event=login_lock_applied tenantCode={} username={} clientIp={} failCount={}",
                    tenantCode, username, clientIp, count);
        }
    }

    public void clearFailures(String tenantCode, String username, String clientIp) {
        if (!securityProperties.getLogin().isEnabled()) {
            return;
        }
        stringRedisTemplate.delete(buildFailKey(tenantCode, username, clientIp));
        stringRedisTemplate.delete(buildLockKey(tenantCode, username, clientIp));
    }

    private String buildFailKey(String tenantCode, String username, String clientIp) {
        return FAIL_PREFIX + normalize(tenantCode) + ":" + normalize(username) + ":" + normalize(clientIp);
    }

    private String buildLockKey(String tenantCode, String username, String clientIp) {
        return LOCK_PREFIX + normalize(tenantCode) + ":" + normalize(username) + ":" + normalize(clientIp);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase();
    }
}
