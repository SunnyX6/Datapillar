package com.sunny.kg.internal.mapper;

import java.util.Map;

/**
 * Cypher 语句
 *
 * @author Sunny
 * @since 2025-12-10
 */
public record CypherStatement(String cypher, Map<String, Object> params) {

    public CypherStatement(String cypher) {
        this(cypher, Map.of());
    }

}
