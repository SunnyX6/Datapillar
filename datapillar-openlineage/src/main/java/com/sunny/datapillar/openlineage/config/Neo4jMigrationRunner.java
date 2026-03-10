package com.sunny.datapillar.openlineage.config;

import ac.simons.neo4j.migrations.core.Migrations;
import ac.simons.neo4j.migrations.core.MigrationsConfig;
import java.util.Arrays;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Execute Neo4j migrations in openlineage service startup. */
@Slf4j
@Component
@Order(0)
public class Neo4jMigrationRunner implements ApplicationRunner {

  private static final String DEFAULT_LOCATION = "classpath:db/neo4j";

  private final Driver driver;
  private final Neo4jConfig.Neo4jProperties neo4jProperties;

  public Neo4jMigrationRunner(Driver driver, Neo4jConfig.Neo4jProperties neo4jProperties) {
    this.driver = driver;
    this.neo4jProperties = neo4jProperties;
  }

  @Override
  public void run(ApplicationArguments args) {
    Neo4jConfig.Migrations migrationProperties = neo4jProperties.getMigrations();
    if (!migrationProperties.isEnabled()) {
      log.info("openlineage_neo4j_migrations disabled by configuration");
      return;
    }

    String[] locations = resolveLocations(migrationProperties.getLocations());
    MigrationsConfig.Builder configBuilder =
        MigrationsConfig.builder()
            .withDatabase(neo4jProperties.getDatabase())
            .withLocationsToScan(locations)
            .withValidateOnMigrate(migrationProperties.isValidateOnMigrate());
    if (hasText(migrationProperties.getInstalledBy())) {
      configBuilder.withInstalledBy(migrationProperties.getInstalledBy().trim());
    }

    Migrations migrations = new Migrations(configBuilder.build(), driver);
    String appliedVersion = migrations.apply().map(Object::toString).orElse("none");
    log.info(
        "openlineage_neo4j_migrations applied latestVersion={} locations={}",
        appliedVersion,
        String.join(",", locations));
  }

  private String[] resolveLocations(String rawLocations) {
    String source = hasText(rawLocations) ? rawLocations : DEFAULT_LOCATION;
    String[] locations =
        Stream.of(source.split(",")).map(String::trim).filter(this::hasText).toArray(String[]::new);
    if (locations.length == 0) {
      return new String[] {DEFAULT_LOCATION};
    }
    return Arrays.copyOf(locations, locations.length);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
