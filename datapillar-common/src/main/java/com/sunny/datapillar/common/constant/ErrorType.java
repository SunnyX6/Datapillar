package com.sunny.datapillar.common.constant;

/**
 * 统一错误类型常量
 * 业务语义统一通过 type 字段传递
 *
 * @author Sunny
 * @date 2026-02-23
 */
public final class ErrorType {

    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String ALREADY_EXISTS = "ALREADY_EXISTS";
    public static final String CONFLICT = "CONFLICT";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String TOO_MANY_REQUESTS = "TOO_MANY_REQUESTS";
    public static final String BAD_GATEWAY = "BAD_GATEWAY";
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String REQUIRED = "REQUIRED";
    public static final String DB_UNIQUE_CONSTRAINT_VIOLATION = "DB_UNIQUE_CONSTRAINT_VIOLATION";
    public static final String DB_FOREIGN_KEY_VIOLATION = "DB_FOREIGN_KEY_VIOLATION";
    public static final String DB_NOT_NULL_VIOLATION = "DB_NOT_NULL_VIOLATION";
    public static final String DB_CHECK_CONSTRAINT_VIOLATION = "DB_CHECK_CONSTRAINT_VIOLATION";
    public static final String DB_DATA_TOO_LONG = "DB_DATA_TOO_LONG";
    public static final String DB_DEADLOCK = "DB_DEADLOCK";
    public static final String DB_LOCK_TIMEOUT = "DB_LOCK_TIMEOUT";
    public static final String DB_CONNECTION_FAILED = "DB_CONNECTION_FAILED";
    public static final String DB_INTERNAL_ERROR = "DB_INTERNAL_ERROR";

    public static final String TENANT_KEY_NOT_FOUND = "TENANT_KEY_NOT_FOUND";
    public static final String TENANT_KEY_INVALID = "TENANT_KEY_INVALID";
    public static final String PURPOSE_NOT_ALLOWED = "PURPOSE_NOT_ALLOWED";
    public static final String CIPHERTEXT_INVALID = "CIPHERTEXT_INVALID";
    public static final String KEY_STORAGE_UNAVAILABLE = "KEY_STORAGE_UNAVAILABLE";
    public static final String TENANT_PRIVATE_KEY_ALREADY_EXISTS = "TENANT_PRIVATE_KEY_ALREADY_EXISTS";
    public static final String TENANT_PUBLIC_KEY_MISSING = "TENANT_PUBLIC_KEY_MISSING";
    public static final String TENANT_PRIVATE_KEY_MISSING = "TENANT_PRIVATE_KEY_MISSING";

    public static final String AUTH_USERNAME_ALREADY_EXISTS = "AUTH_USERNAME_ALREADY_EXISTS";
    public static final String AUTH_EMAIL_ALREADY_EXISTS = "AUTH_EMAIL_ALREADY_EXISTS";
    public static final String SSO_STATE_INVALID = "SSO_STATE_INVALID";
    public static final String SSO_PROVIDER_UNSUPPORTED = "SSO_PROVIDER_UNSUPPORTED";
    public static final String SSO_PROVIDER_UNAVAILABLE = "SSO_PROVIDER_UNAVAILABLE";
    public static final String SSO_PROVIDER_RESPONSE_INVALID = "SSO_PROVIDER_RESPONSE_INVALID";
    public static final String SSO_CONFIG_REQUEST_INVALID = "SSO_CONFIG_REQUEST_INVALID";
    public static final String SSO_CONFIG_NOT_FOUND = "SSO_CONFIG_NOT_FOUND";
    public static final String SSO_CONFIG_DISABLED = "SSO_CONFIG_DISABLED";
    public static final String SSO_CONFIG_INVALID = "SSO_CONFIG_INVALID";
    public static final String SSO_IDENTITY_REQUEST_INVALID = "SSO_IDENTITY_REQUEST_INVALID";
    public static final String SSO_IDENTITY_NOT_FOUND = "SSO_IDENTITY_NOT_FOUND";
    public static final String SSO_IDENTITY_ACCESS_DENIED = "SSO_IDENTITY_ACCESS_DENIED";
    public static final String SSO_UNAUTHORIZED = "SSO_UNAUTHORIZED";
    public static final String STUDIO_DB_DUPLICATE = "STUDIO_DB_DUPLICATE";
    public static final String STUDIO_DB_INTERNAL = "STUDIO_DB_INTERNAL";
    public static final String AUTH_DB_DUPLICATE = "AUTH_DB_DUPLICATE";
    public static final String AUTH_DB_INTERNAL = "AUTH_DB_INTERNAL";

    public static final String INVITATION_EMAIL_ALREADY_EXISTS = "INVITATION_EMAIL_ALREADY_EXISTS";
    public static final String INVITATION_USERNAME_ALREADY_EXISTS = "INVITATION_USERNAME_ALREADY_EXISTS";
    public static final String INVITATION_REQUEST_INVALID = "INVITATION_REQUEST_INVALID";
    public static final String INVITATION_NOT_FOUND = "INVITATION_NOT_FOUND";
    public static final String INVITATION_INVITER_NOT_FOUND = "INVITATION_INVITER_NOT_FOUND";
    public static final String INVITATION_TENANT_NOT_FOUND = "INVITATION_TENANT_NOT_FOUND";
    public static final String INVITATION_EXPIRED = "INVITATION_EXPIRED";
    public static final String INVITATION_ALREADY_USED = "INVITATION_ALREADY_USED";
    public static final String INVITATION_INACTIVE = "INVITATION_INACTIVE";
    public static final String INVITATION_INTERNAL_ERROR = "INVITATION_INTERNAL_ERROR";
    public static final String INVITATION_UNAUTHORIZED = "INVITATION_UNAUTHORIZED";
    public static final String TENANT_CODE_ALREADY_EXISTS = "TENANT_CODE_ALREADY_EXISTS";
    public static final String SSO_CONFIG_ALREADY_EXISTS = "SSO_CONFIG_ALREADY_EXISTS";
    public static final String SSO_IDENTITY_ALREADY_EXISTS = "SSO_IDENTITY_ALREADY_EXISTS";
    public static final String AI_PROVIDER_ALREADY_EXISTS = "AI_PROVIDER_ALREADY_EXISTS";
    public static final String AI_MODEL_ALREADY_EXISTS = "AI_MODEL_ALREADY_EXISTS";
    public static final String AI_MODEL_GRANT_CONFLICT = "AI_MODEL_GRANT_CONFLICT";
    public static final String LLM_REQUEST_INVALID = "LLM_REQUEST_INVALID";
    public static final String LLM_RESOURCE_NOT_FOUND = "LLM_RESOURCE_NOT_FOUND";
    public static final String LLM_FORBIDDEN = "LLM_FORBIDDEN";
    public static final String LLM_UNAUTHORIZED = "LLM_UNAUTHORIZED";
    public static final String LLM_INTERNAL_ERROR = "LLM_INTERNAL_ERROR";
    public static final String LLM_CONNECTION_FAILED = "LLM_CONNECTION_FAILED";
    public static final String GRAVITINO_RPC_REQUEST_INVALID = "GRAVITINO_RPC_REQUEST_INVALID";
    public static final String GRAVITINO_RPC_PRIVILEGE_INVALID = "GRAVITINO_RPC_PRIVILEGE_INVALID";
    public static final String GRAVITINO_RPC_OBJECT_INVALID = "GRAVITINO_RPC_OBJECT_INVALID";
    public static final String GRAVITINO_RPC_UNAVAILABLE = "GRAVITINO_RPC_UNAVAILABLE";
    public static final String GRAVITINO_RPC_INTERNAL_ERROR = "GRAVITINO_RPC_INTERNAL_ERROR";

    private ErrorType() {
    }
}
