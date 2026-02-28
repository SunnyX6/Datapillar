package com.sunny.datapillar.openlineage.config;

import jakarta.annotation.PreDestroy;
import lombok.Data;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Neo4j 连接配置。
 */
@Configuration
public class Neo4jConfig {

    private Driver driver;

    @Bean
    @ConfigurationProperties(prefix = "openlineage.neo4j")
    public Neo4jProperties neo4jProperties() {
        return new Neo4jProperties();
    }

    @Bean
    public Driver neo4jDriver(Neo4jProperties properties) {
        this.driver = GraphDatabase.driver(
                properties.getUri(),
                AuthTokens.basic(properties.getUsername(), properties.getPassword()));
        return this.driver;
    }

    @PreDestroy
    public void destroy() {
        if (driver != null) {
            driver.close();
        }
    }

    @Data
    public static class Neo4jProperties {
        private String uri;
        private String username;
        private String password;
        private String database = "neo4j";
    }
}
