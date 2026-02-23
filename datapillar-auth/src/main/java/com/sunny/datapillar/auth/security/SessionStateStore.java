package com.sunny.datapillar.auth.security;

import com.sunny.datapillar.common.security.SessionStateKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 会话状态存储
 * 负责会话状态状态存储与生命周期管理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
public class SessionStateStore {

    private static final String ROTATE_SESSION_SCRIPT = "local status=redis.call('GET', KEYS[1]); "
            + "if status~=ARGV[1] then return '__SESSION_INACTIVE__'; end; "
            + "local refreshJti=redis.call('GET', KEYS[2]); "
            + "if (not refreshJti) or refreshJti~=ARGV[2] then return '__REFRESH_REUSED__'; end; "
            + "local oldAccess=redis.call('GET', KEYS[3]); "
            + "redis.call('SET', KEYS[2], ARGV[3], 'EX', ARGV[5]); "
            + "redis.call('SET', KEYS[3], ARGV[4], 'EX', ARGV[5]); "
            + "redis.call('EXPIRE', KEYS[1], ARGV[5]); "
            + "redis.call('EXPIRE', KEYS[4], ARGV[5]); "
            + "redis.call('EXPIRE', KEYS[5], ARGV[5]); "
            + "if oldAccess then return oldAccess; else return ''; end;";

    private static final long REVOKED_MARK_SECONDS = 604800L;

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<String> rotateScript;

    public SessionStateStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.rotateScript = new DefaultRedisScript<>(ROTATE_SESSION_SCRIPT, String.class);
    }

    public void openSession(String sid,
                            Long tenantId,
                            Long userId,
                            String accessJti,
                            String refreshJti,
                            long sessionTtlSeconds,
                            long accessTtlSeconds) {
        Duration sessionTtl = Duration.ofSeconds(Math.max(1L, sessionTtlSeconds));
        stringRedisTemplate.opsForValue().set(SessionStateKeys.sessionStatusKey(sid), SessionStateKeys.STATUS_ACTIVE, sessionTtl);
        stringRedisTemplate.opsForValue().set(SessionStateKeys.sessionTenantKey(sid), String.valueOf(tenantId), sessionTtl);
        stringRedisTemplate.opsForValue().set(SessionStateKeys.sessionUserKey(sid), String.valueOf(userId), sessionTtl);
        stringRedisTemplate.opsForValue().set(SessionStateKeys.sessionAccessJtiKey(sid), accessJti, sessionTtl);
        stringRedisTemplate.opsForValue().set(SessionStateKeys.sessionRefreshJtiKey(sid), refreshJti, sessionTtl);
        activateAccessToken(sid, accessJti, accessTtlSeconds);
    }

    public RotateResult rotateForRefresh(String sid,
                                         String presentedRefreshJti,
                                         String newRefreshJti,
                                         String newAccessJti,
                                         long sessionTtlSeconds,
                                         long accessTtlSeconds) {
        String oldAccessJti = stringRedisTemplate.execute(
                rotateScript,
                List.of(
                        SessionStateKeys.sessionStatusKey(sid),
                        SessionStateKeys.sessionRefreshJtiKey(sid),
                        SessionStateKeys.sessionAccessJtiKey(sid),
                        SessionStateKeys.sessionTenantKey(sid),
                        SessionStateKeys.sessionUserKey(sid)
                ),
                SessionStateKeys.STATUS_ACTIVE,
                presentedRefreshJti,
                newRefreshJti,
                newAccessJti,
                String.valueOf(Math.max(1L, sessionTtlSeconds))
        );

        if ("__SESSION_INACTIVE__".equals(oldAccessJti)) {
            return RotateResult.withSessionInactive();
        }
        if ("__REFRESH_REUSED__".equals(oldAccessJti)) {
            return RotateResult.withRefreshReused();
        }

        activateAccessToken(sid, newAccessJti, accessTtlSeconds);
        String previous = oldAccessJti == null || oldAccessJti.isBlank() ? null : oldAccessJti;
        if (previous != null && !previous.equals(newAccessJti)) {
            revokeAccessToken(previous);
        }
        return RotateResult.success(previous);
    }

    public boolean isSessionActive(String sid) {
        String status = stringRedisTemplate.opsForValue().get(SessionStateKeys.sessionStatusKey(sid));
        return SessionStateKeys.STATUS_ACTIVE.equals(status);
    }

    public boolean isAccessTokenActive(String sid, String accessJti) {
        if (!isSessionActive(sid)) {
            return false;
        }
        String tokenStatus = stringRedisTemplate.opsForValue().get(SessionStateKeys.tokenStatusKey(accessJti));
        if (!SessionStateKeys.STATUS_ACTIVE.equals(tokenStatus)) {
            return false;
        }
        String tokenSid = stringRedisTemplate.opsForValue().get(SessionStateKeys.tokenSessionKey(accessJti));
        return sid.equals(tokenSid);
    }

    public void revokeSession(String sid) {
        String statusKey = SessionStateKeys.sessionStatusKey(sid);
        String currentAccessJti = stringRedisTemplate.opsForValue().get(SessionStateKeys.sessionAccessJtiKey(sid));
        long ttlSeconds = resolveRemainingTtlSeconds(statusKey);
        stringRedisTemplate.opsForValue().set(statusKey, SessionStateKeys.STATUS_REVOKED, Duration.ofSeconds(ttlSeconds));
        if (currentAccessJti != null && !currentAccessJti.isBlank()) {
            revokeAccessToken(currentAccessJti);
        }
    }

    public boolean replaceAccessToken(String sid, String expectedAccessJti, String newAccessJti, long accessTtlSeconds) {
        if (!isSessionActive(sid)) {
            return false;
        }
        String currentAccessJti = stringRedisTemplate.opsForValue().get(SessionStateKeys.sessionAccessJtiKey(sid));
        if (expectedAccessJti != null && !expectedAccessJti.equals(currentAccessJti)) {
            return false;
        }

        long sessionTtlSeconds = resolveRemainingTtlSeconds(SessionStateKeys.sessionStatusKey(sid));
        stringRedisTemplate.opsForValue().set(
                SessionStateKeys.sessionAccessJtiKey(sid),
                newAccessJti,
                Duration.ofSeconds(Math.max(1L, sessionTtlSeconds))
        );
        activateAccessToken(sid, newAccessJti, accessTtlSeconds);
        if (currentAccessJti != null && !currentAccessJti.isBlank() && !currentAccessJti.equals(newAccessJti)) {
            revokeAccessToken(currentAccessJti);
        }
        return true;
    }

    public void revokeAccessToken(String accessJti) {
        String tokenStatusKey = SessionStateKeys.tokenStatusKey(accessJti);
        long ttlSeconds = resolveRemainingTtlSeconds(tokenStatusKey);
        stringRedisTemplate.opsForValue().set(tokenStatusKey, SessionStateKeys.STATUS_REVOKED, Duration.ofSeconds(ttlSeconds));
    }

    private void activateAccessToken(String sid, String accessJti, long accessTtlSeconds) {
        Duration accessTtl = Duration.ofSeconds(Math.max(1L, accessTtlSeconds));
        stringRedisTemplate.opsForValue().set(SessionStateKeys.tokenStatusKey(accessJti), SessionStateKeys.STATUS_ACTIVE, accessTtl);
        stringRedisTemplate.opsForValue().set(SessionStateKeys.tokenSessionKey(accessJti), sid, accessTtl);
    }

    private long resolveRemainingTtlSeconds(String key) {
        Long ttlSeconds = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttlSeconds == null || ttlSeconds <= 0L) {
            return REVOKED_MARK_SECONDS;
        }
        return ttlSeconds;
    }

    public record RotateResult(boolean success, boolean sessionInactive, boolean refreshReused, String previousAccessJti) {

        public static RotateResult success(String previousAccessJti) {
            return new RotateResult(true, false, false, previousAccessJti);
        }

        public static RotateResult withSessionInactive() {
            return new RotateResult(false, true, false, null);
        }

        public static RotateResult withRefreshReused() {
            return new RotateResult(false, false, true, null);
        }
    }
}
