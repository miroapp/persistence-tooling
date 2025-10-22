package com.example.demo;

import com.miro.persistence.tooling.core.FlywayMigrationSet;
import com.miro.persistence.tooling.test.PostgreSQLTestContainerTool;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

@TestConfiguration
public class PersistenceTestConfiguration {

    private static final FlywayMigrationSet FLYWAY_MIGRATION_SET = FlywayMigrationSet.builder()
            .migrationResourcesPath("db/migration")
            .dockerImageName("postgres-it-example")
            .build();

    @Bean(initMethod = "start")
    public PostgreSQLContainer<?> postgreSQLContainer() {
        return getPostgreSQLContainer(FLYWAY_MIGRATION_SET);
    }

    @Bean
    public DataSource dataSource(PostgreSQLContainer<?> postgreSQLContainer) {
        return DockerDataSourceUtils.createDataSource("main", postgreSQLContainer, null);
    }

    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    private static PostgreSQLContainer<?> getPostgreSQLContainer(FlywayMigrationSet flywayMigrationSet) {
        // Creates a reusable Docker image with flyway migrations applied if it does not exist.
        // Typical docker image name is "postgres-it-example:V2-814c38", the docker image tag name is
        // dynamically calculated as hash sum of all flyway migration scripts from db/migration directory.
        // Returns the docker container of this image.
        return new PostgreSQLTestContainerTool()
                .createPostgreSQLContainer(flywayMigrationSet);
    }
}
