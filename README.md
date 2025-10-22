# Miro Persistence Tooling

Provides utility to create reusable docker images with flyway migrations applied.
The Docker image tag name is dynamically calculated as a hash sum of all Flyway migration scripts,
so it's a reproducible key and a strong guarantee to use the same Docker image for the same set of migrations.
The docker image contains two parts separated by dash (`-`):
* last (highest) Flyway migration number
* hash sum of all Flyway migration scripts (first 6 digits)

This format is easy to read, understand, order and compare.
Sample docker image name: `postgres-it-example:V2-33f7f7` which means that last migration is V2 and the hash sum
of all migration scripts is `33f7f7` (first 6 hex-digits).

These images can be pushed to the internal registry to speed up the test execution in CI environments.

## Usage in Spring Integration Tests
On the first execution the temporary docker container with empty PostgreSQL database is created,
all Flyway migrations are applied, and then this temporary container is committed to a **reusable** Docker image,
container is terminated.
Any subsequent test executions will use this Docker image created in the previous step, which may significantly
reduce the test execution time - it will bootstrap with all migrations immediately (especially when there are hundreds
of migrations).

Sample Spring configuration (see [example](example) project) that defines docker container, wrapping DataSource
and NamedParameterJdbcTemplate for it.
These beans will be under the spring lifecycle management (terminated on context shutdown).
```java
@TestConfiguration
public class PersistenceTestConfiguration {

    private static final FlywayMigrationSet FLYWAY_MIGRATION_SET = FlywayMigrationSet.builder()
            // path to the flyway migration resources
            .migrationResourcesPath("db/migration")
            // docker image name, the final docker image name will be like postgres-it-example:V2-33f7f7
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
        return new PostgreSQLTestContainerTool()
                .createPostgreSQLContainer(flywayMigrationSet);
    }
}
```

## Supported databases
So far only the PostgreSQL is supported, other databases can be added on demand.

