package com.sunny.job.core.enums;

/**
 * 任务类型常量
 * <p>
 * 内置任务类型定义，用户可通过 @DatapillarJob 注册自定义类型
 * <p>
 * 数据库存储为 VARCHAR，支持任意扩展
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public final class JobType {

    private JobType() {
    }

    // ============ 内置任务类型 ============

    public static final String SHELL = "SHELL";
    public static final String PYTHON = "PYTHON";
    public static final String SPARK = "SPARK";
    public static final String FLINK = "FLINK";
    public static final String HIVE_SQL = "HIVE_SQL";
    public static final String DATAX = "DATAX";
    public static final String HTTP = "HTTP";

    // ============ 工具方法 ============

    /**
     * 是否为脚本类型
     */
    public static boolean isScript(String jobType) {
        return SHELL.equals(jobType) || PYTHON.equals(jobType);
    }

    /**
     * 是否为大数据计算类型
     */
    public static boolean isBigData(String jobType) {
        return SPARK.equals(jobType) || FLINK.equals(jobType)
                || HIVE_SQL.equals(jobType) || DATAX.equals(jobType);
    }
}
