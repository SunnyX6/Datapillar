package com.sunny.datapillar.openlineage.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 启动时初始化 Neo4j 约束。
 */
@Slf4j
@Component
public class Neo4jConstraintInitializer {

    private final Driver driver;
    private final Neo4jConfig.Neo4jProperties properties;

    public Neo4jConstraintInitializer(Driver driver, Neo4jConfig.Neo4jProperties properties) {
        this.driver = driver;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("db/neo4j/V1__constraints.cypher");
            String script = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String[] statements = script.split(";");
            try (Session session = driver.session(SessionConfig.forDatabase(properties.getDatabase()))) {
                for (String statement : statements) {
                    String trimmed = statement.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    session.executeWrite(tx -> {
                        tx.run(trimmed);
                        return null;
                    });
                }
            }
            log.info("Neo4j constraints initialized");
        } catch (Exception ex) {
            log.error("Failed to initialize Neo4j constraints", ex);
        }
    }
}
