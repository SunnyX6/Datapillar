package com.sunny.job.core.strategy.block;

/**
 * 阻塞处理结果
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public final class BlockResult {

    private final boolean proceed;
    private final String message;
    private final Long cancelInstanceId;

    private BlockResult(boolean proceed, String message, Long cancelInstanceId) {
        this.proceed = proceed;
        this.message = message;
        this.cancelInstanceId = cancelInstanceId;
    }

    /**
     * 允许执行
     */
    public static BlockResult proceed() {
        return new BlockResult(true, null, null);
    }

    /**
     * 丢弃本次调度
     */
    public static BlockResult discard(String message) {
        return new BlockResult(false, message, null);
    }

    /**
     * 覆盖之前调度 (取消指定实例)
     */
    public static BlockResult cover(Long cancelInstanceId) {
        return new BlockResult(true, null, cancelInstanceId);
    }

    /**
     * 是否允许继续执行
     */
    public boolean isProceed() {
        return proceed;
    }

    /**
     * 获取消息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 获取需要取消的实例 ID
     */
    public Long getCancelInstanceId() {
        return cancelInstanceId;
    }

    /**
     * 是否需要取消之前的实例
     */
    public boolean needCancel() {
        return cancelInstanceId != null;
    }

    @Override
    public String toString() {
        return "BlockResult{proceed=" + proceed + ", message='" + message + "', cancelInstanceId=" + cancelInstanceId + '}';
    }
}
