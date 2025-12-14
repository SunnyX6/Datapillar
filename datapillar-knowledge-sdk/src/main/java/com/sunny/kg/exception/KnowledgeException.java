package com.sunny.kg.exception;

/**
 * 知识库 SDK 异常
 *
 * @author Sunny
 * @since 2025-12-10
 */
public class KnowledgeException extends RuntimeException {

    private final KnowledgeErrorCode errorCode;

    public KnowledgeException(KnowledgeErrorCode errorCode, Object... args) {
        super(errorCode.formatMessage(args));
        this.errorCode = errorCode;
    }

    public KnowledgeException(KnowledgeErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.formatMessage(args), cause);
        this.errorCode = errorCode;
    }

    public KnowledgeErrorCode getErrorCode() {
        return errorCode;
    }

    public String getCode() {
        return errorCode.getCode();
    }

}
