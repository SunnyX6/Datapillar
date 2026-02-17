package com.sunny.datapillar.common.security;

/**
 * 会话状态Keys组件
 * 负责会话状态Keys核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
public final class SessionStateKeys {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_REVOKED = "revoked";

    private static final String SESSION_PREFIX = "auth:session:";
    private static final String TOKEN_PREFIX = "auth:token:jti:";

    private SessionStateKeys() {
    }

    public static String sessionStatusKey(String sid) {
        return SESSION_PREFIX + sid + ":status";
    }

    public static String sessionUserKey(String sid) {
        return SESSION_PREFIX + sid + ":user";
    }

    public static String sessionTenantKey(String sid) {
        return SESSION_PREFIX + sid + ":tenant";
    }

    public static String sessionAccessJtiKey(String sid) {
        return SESSION_PREFIX + sid + ":access:jti";
    }

    public static String sessionRefreshJtiKey(String sid) {
        return SESSION_PREFIX + sid + ":refresh:jti";
    }

    public static String tokenStatusKey(String jti) {
        return TOKEN_PREFIX + jti + ":status";
    }

    public static String tokenSessionKey(String jti) {
        return TOKEN_PREFIX + jti + ":sid";
    }
}

