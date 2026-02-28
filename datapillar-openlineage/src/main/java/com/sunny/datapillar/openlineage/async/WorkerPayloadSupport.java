package com.sunny.datapillar.openlineage.async;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Worker 负载构造工具。
 */
public final class WorkerPayloadSupport {

    private static final Pattern SQL_TOKEN_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private WorkerPayloadSupport() {
    }

    public static String extractProvider(String modelFingerprint) {
        if (modelFingerprint == null || modelFingerprint.isBlank()) {
            return "default";
        }
        int index = modelFingerprint.indexOf(':');
        if (index <= 0) {
            return modelFingerprint;
        }
        return modelFingerprint.substring(0, index);
    }

    public static double[] buildEmbeddingVector(String content) {
        byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
        int dimensions = 16;
        double[] vector = new double[dimensions];
        if (bytes.length == 0) {
            return vector;
        }

        for (int i = 0; i < bytes.length; i++) {
            int idx = i % dimensions;
            vector[idx] += (bytes[i] & 0xFF) / 255.0;
        }
        for (int i = 0; i < dimensions; i++) {
            vector[i] = vector[i] / Math.max(1, bytes.length / dimensions);
        }
        return vector;
    }

    public static String buildSqlSummary(String sql) {
        if (sql == null) {
            return "";
        }
        String compact = sql.replaceAll("\\s+", " ").trim();
        if (compact.isEmpty()) {
            return "";
        }
        int maxLength = 220;
        if (compact.length() <= maxLength) {
            return compact;
        }
        return compact.substring(0, maxLength) + "...";
    }

    public static String buildSqlTags(String sql) {
        if (sql == null || sql.isBlank()) {
            return "";
        }

        String lowered = sql.toLowerCase(Locale.ROOT);
        Set<String> keywords = Set.of("select", "insert", "update", "delete", "join", "group", "order", "where");
        List<String> tags = new ArrayList<>();
        for (String keyword : keywords) {
            if (lowered.contains(keyword)) {
                tags.add(keyword);
            }
        }

        Matcher matcher = SQL_TOKEN_PATTERN.matcher(sql);
        int count = 0;
        while (matcher.find() && count < 3) {
            String token = matcher.group().toLowerCase(Locale.ROOT);
            if (token.length() > 2 && !keywords.contains(token)) {
                tags.add(token);
                count++;
            }
        }

        return String.join(",", tags.stream().distinct().toList());
    }
}
