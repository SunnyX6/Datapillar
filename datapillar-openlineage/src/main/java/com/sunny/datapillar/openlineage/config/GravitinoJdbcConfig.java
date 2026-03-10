package com.sunny.datapillar.openlineage.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** JDBC configuration for Gravitino metadata database snapshot reads. */
@Configuration
public class GravitinoJdbcConfig {

  @Bean(name = "gravitinoDataSourceProperties")
  @ConfigurationProperties(prefix = "openlineage.gravitino-db")
  public DataSourceProperties gravitinoDataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean(name = "gravitinoDataSource")
  public DataSource gravitinoDataSource(
      @Qualifier("gravitinoDataSourceProperties") DataSourceProperties properties) {
    return properties.initializeDataSourceBuilder().build();
  }

  @Bean(name = "gravitinoJdbcTemplate")
  public JdbcTemplate gravitinoJdbcTemplate(
      @Qualifier("gravitinoDataSource") DataSource dataSource) {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.setFetchSize(1000);
    return jdbcTemplate;
  }
}
