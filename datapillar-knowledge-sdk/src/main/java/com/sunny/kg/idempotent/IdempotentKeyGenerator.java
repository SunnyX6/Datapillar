package com.sunny.kg.idempotent;

import com.sunny.kg.model.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 幂等键生成器
 *
 * @author Sunny
 * @since 2025-12-11
 */
public class IdempotentKeyGenerator {

    /**
     * 生成表的幂等键
     */
    public static String generate(TableMeta table) {
        return hash("table:" + table.getCatalog() + "." + table.getSchema() + "." + table.getName());
    }

    /**
     * 生成血缘的幂等键
     */
    public static String generate(Lineage lineage) {
        return hash("lineage:" + lineage.getSourceTable() + "->" + lineage.getTargetTable());
    }

    /**
     * 生成目录的幂等键
     */
    public static String generate(CatalogMeta catalog) {
        return hash("catalog:" + catalog.getName());
    }

    /**
     * 生成 Schema 的幂等键
     */
    public static String generate(SchemaMeta schema) {
        return hash("schema:" + schema.getCatalog() + "." + schema.getName());
    }

    /**
     * 生成指标的幂等键
     */
    public static String generate(MetricMeta metric) {
        return hash("metric:" + metric.getName());
    }

    /**
     * 生成质量规则的幂等键
     */
    public static String generate(QualityRuleMeta rule) {
        return hash("rule:" + rule.getName() + "@" + rule.getTableName());
    }

    private static String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return input.hashCode() + "";
        }
    }

}
