package com.sunny.job.core.enums;

/**
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
public class RegistryConfig {

    public static final int BEAT_TIMEOUT = 30;
    public static final int DEAD_TIMEOUT = BEAT_TIMEOUT * 3;

    public enum RegistType{ EXECUTOR, ADMIN }

}
