package com.sunny.job.core.cron;

import com.sunny.job.core.common.Assert;
import com.sunny.job.core.enums.TriggerType;
import org.springframework.scheduling.support.CronExpression;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Cron 工具类
 * <p>
 * 基于 Spring CronExpression 实现
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public final class CronUtils {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private CronUtils() {
    }

    /**
     * 计算下一次触发时间
     *
     * @param triggerType 触发类型
     * @param triggerExpr 触发表达式 (Cron 或间隔秒数)
     * @param fromMs      起始时间 (毫秒)
     * @return 下一次触发时间 (毫秒)，-1 表示无法计算
     */
    public static long calculateNextTriggerTime(TriggerType triggerType, String triggerExpr, long fromMs) {
        Assert.notNull(triggerType, "触发类型不能为空");

        return switch (triggerType) {
            case CRON -> calculateCronNextTrigger(triggerExpr, fromMs);
            case FIXED_RATE, FIXED_DELAY -> calculateFixedNextTrigger(triggerExpr, fromMs);
            case MANUAL, API, RETRY -> -1;
        };
    }

    /**
     * 计算 Cron 下一次触发时间
     *
     * @param cronExpr Cron 表达式
     * @param fromMs   起始时间 (毫秒)
     * @return 下一次触发时间 (毫秒)，-1 表示无法计算
     */
    public static long calculateCronNextTrigger(String cronExpr, long fromMs) {
        Assert.notBlank(cronExpr, "Cron 表达式不能为空");

        CronExpression cron = CronExpression.parse(cronExpr);
        LocalDateTime from = LocalDateTime.ofInstant(Instant.ofEpochMilli(fromMs), ZONE_ID);
        LocalDateTime next = cron.next(from);

        if (next == null) {
            return -1;
        }
        return next.atZone(ZONE_ID).toInstant().toEpochMilli();
    }

    /**
     * 计算固定间隔下一次触发时间
     */
    private static long calculateFixedNextTrigger(String intervalExpr, long fromMs) {
        Assert.notBlank(intervalExpr, "间隔表达式不能为空");
        long intervalMs = parseInterval(intervalExpr);
        return fromMs + intervalMs;
    }

    /**
     * 解析间隔表达式
     * <p>
     * 支持格式：
     * <ul>
     *     <li>纯数字：秒数</li>
     *     <li>带单位：10s, 5m, 1h, 1d</li>
     * </ul>
     *
     * @param intervalExpr 间隔表达式
     * @return 间隔毫秒数
     */
    public static long parseInterval(String intervalExpr) {
        Assert.notBlank(intervalExpr, "间隔表达式不能为空");

        String expr = intervalExpr.trim().toLowerCase();

        if (expr.endsWith("ms")) {
            return Long.parseLong(expr.substring(0, expr.length() - 2));
        } else if (expr.endsWith("s")) {
            return Long.parseLong(expr.substring(0, expr.length() - 1)) * 1000;
        } else if (expr.endsWith("m")) {
            return Long.parseLong(expr.substring(0, expr.length() - 1)) * 60 * 1000;
        } else if (expr.endsWith("h")) {
            return Long.parseLong(expr.substring(0, expr.length() - 1)) * 60 * 60 * 1000;
        } else if (expr.endsWith("d")) {
            return Long.parseLong(expr.substring(0, expr.length() - 1)) * 24 * 60 * 60 * 1000;
        } else {
            return Long.parseLong(expr) * 1000;
        }
    }

    /**
     * 验证 Cron 表达式是否有效
     *
     * @param cronExpr Cron 表达式
     * @return true 有效，false 无效
     */
    public static boolean isValidCron(String cronExpr) {
        if (cronExpr == null || cronExpr.isBlank()) {
            return false;
        }
        try {
            CronExpression.parse(cronExpr);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 格式化时间戳为可读字符串
     *
     * @param timestamp 时间戳（毫秒）
     * @return 格式化后的字符串
     */
    public static String formatTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return "N/A";
        }
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZONE_ID);
        return dateTime.format(FORMATTER);
    }

    /**
     * 获取 Cron 表达式的人类可读描述
     *
     * @param cronExpr Cron 表达式
     * @return 描述字符串
     */
    public static String getDescription(String cronExpr) {
        if (!isValidCron(cronExpr)) {
            return "无效的 Cron 表达式";
        }
        return cronExpr;
    }
}
