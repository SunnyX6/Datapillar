package com.sunny.datapillar.common.exception.db;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 约束名称提取器
 * 负责从数据库异常消息中提取约束名
 *
 * @author Sunny
 * @date 2026-02-26
 */
public final class ConstraintNameExtractor {

    private static final Pattern MYSQL_KEY_PATTERN = Pattern.compile(
            "for key\\s+[`\"']?([a-zA-Z0-9_.$-]+)[`\"']?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CONSTRAINT_PATTERN = Pattern.compile(
            "constraint\\s+[`\"']?([a-zA-Z0-9_.$-]+)[`\"']?",
            Pattern.CASE_INSENSITIVE);

    private ConstraintNameExtractor() {
    }

    public static Optional<String> extract(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            Optional<String> fromMessage = extract(cursor.getMessage());
            if (fromMessage.isPresent()) {
                return fromMessage;
            }
            cursor = cursor.getCause();
        }
        return Optional.empty();
    }

    public static Optional<String> extract(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }

        Optional<String> fromMySql = match(MYSQL_KEY_PATTERN, message);
        if (fromMySql.isPresent()) {
            return fromMySql;
        }

        return match(CONSTRAINT_PATTERN, message);
    }

    private static Optional<String> match(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        if (!matcher.find()) {
            return Optional.empty();
        }

        String name = matcher.group(1);
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(name.toLowerCase(Locale.ROOT));
    }
}
