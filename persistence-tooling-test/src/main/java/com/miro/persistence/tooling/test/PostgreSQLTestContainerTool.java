package com.miro.persistence.tooling.test;

import static java.time.temporal.ChronoUnit.MINUTES;

import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ContainerConfig;
import com.miro.persistence.tooling.core.FlywayChecksumUtils;
import com.miro.persistence.tooling.core.FlywayMigrationSet;
import com.miro.persistence.tooling.core.InitScript;
import com.miro.persistence.tooling.core.PersistenceClasspathResources;
import com.miro.persistence.tooling.core.PostgresContainerAdapter;
import com.miro.persistence.tooling.core.PostgresExecutable;
import jakarta.annotation.Nullable;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

/**
 * Tooling to create PostgreSQL container with Flyway migrations and commit it to an image. The image can be reused for
 * multiple tests. The image tag is calculated based as a hash of the Flyway migration scripts and the classpath
 * resources.
 *
 * @author Nikolai Averin
 * @author Sergey Chernov
 */
public class PostgreSQLTestContainerTool {

    private static final String DEFAULT_DOCKER_IMAGE = "postgres:15.4-alpine";

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgreSQLTestContainerTool.class);

    private static final String DB_USER = "user";
    private static final String DB_PASSWORD = "password";
    private static final String DB_NAME = "database";

    @SuppressWarnings("resource")
    public PostgreSQLContainer<?> createPostgreSQLContainer(FlywayMigrationSet flywayMigrationSet) {
        ImageResult imageResult = getOrCreateImage(flywayMigrationSet);
        ContainerConfig config = imageResult.config;
        Assert.state(config != null, "Can't determine connection settings because the image's config is null");
        String[] envVars = config.getEnv();
        Assert.state(envVars != null,
                "Can't determine connection settings because the image's environment variables are null");

        String database = null;
        String user = null;
        String password = null;
        for (String var : envVars) {
            if (var.startsWith("POSTGRES_DB=")) {
                database = var.substring("POSTGRES_DB=".length());
            }
            if (var.startsWith("POSTGRES_USER=")) {
                user = var.substring("POSTGRES_USER=".length());
            }
            if (var.startsWith("POSTGRES_PASSWORD=")) {
                password = var.substring("POSTGRES_PASSWORD=".length());
            }
        }
        Assert.state(database != null, "Can't determine database name. Env variable 'POSTGRES_DB' not found inside "
                + "the image");
        Assert.state(user != null, "Can't determine user. Env variable 'POSTGRES_USER' not found inside the image");
        Assert.state(password != null, "Can't determine password. Env variable 'POSTGRES_PASSWORD' not found inside "
                + "the image");

        return new PostgreSQLContainer<>(imageResult.dockerImageName.asCompatibleSubstituteFor("postgres"))
                .withDatabaseName(database)
                .withUsername(user)
                .withPassword(password)
                .waitingFor(new LogMessageWaitStrategy()
                        .withRegEx(".*database system is ready to accept connections.*\\s")
                        .withStartupTimeout(Duration.of(1, MINUTES)));
    }

    private ImageResult getOrCreateImage(FlywayMigrationSet flywayMigrationSet) {
        String baseImageName = getBaseImageName(flywayMigrationSet);
        String imageName = resolveImageName(flywayMigrationSet);
        String imageTag = resolveImageTag(baseImageName, flywayMigrationSet);
        String imageNameWithTag = imageName + ":" + imageTag;

        InspectImageResponse image = findImage(imageNameWithTag);
        if (image == null) {
            LOGGER.info("Image [{}] does not exist, creating on demand", imageNameWithTag);
            List<InitScript> initScripts = getInitScripts(flywayMigrationSet.getInitScriptPaths());
            PostgresExecutable postgres =
                    PostgresContainerAdapter.createPostgresContainerAdapterFromBaseImage(baseImageName);
            String jdbcUrl = postgres.start(DB_NAME, DB_USER, DB_PASSWORD, initScripts);
            try {
                executeFlyway(flywayMigrationSet, jdbcUrl);
                image = findImage(imageNameWithTag);
                if (image == null) {
                    postgres.saveState(imageName, imageTag);
                    image = findImage(imageNameWithTag);
                } else {
                    // e.g. parallel IT execution
                    // we should not commit, because it will overwrite the tag and make the image dangling
                    LOGGER.info("Concurrent process generated the image [{}], skipping commit", imageNameWithTag);
                }
            } finally {
                postgres.stop();
            }
            Assert.state(image != null, "Image " + imageNameWithTag + " does not exist");
        }
        return new ImageResult(DockerImageName.parse(imageNameWithTag), image.getConfig());
    }

    protected String getBaseImageName(FlywayMigrationSet flywayMigrationSet) {
        String baseDockerImageName = flywayMigrationSet.getBaseDockerImageName();
        return baseDockerImageName == null ? DEFAULT_DOCKER_IMAGE : baseDockerImageName;
    }

    protected String resolveImageName(FlywayMigrationSet flywayMigrationSet) {
        // Optional prefix can be added to match the registry prefix
        return flywayMigrationSet.getDockerImageName();
    }

    protected String resolveImageTag(String baseImageName, FlywayMigrationSet flywayMigrationSet) {
        return FlywayChecksumUtils.calculateChecksum(baseImageName, flywayMigrationSet.getInitScriptPaths(),
                flywayMigrationSet.getMigrationResourcesPath());
    }

    private void executeFlyway(FlywayMigrationSet flywayMigrationSet, String jdbcUrl) {
        String schema = flywayMigrationSet.getSchema();
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(jdbcUrl, DB_USER, DB_PASSWORD)
                .locations("classpath:" + flywayMigrationSet.getMigrationResourcesPath())
                .table(flywayMigrationSet.getFlywayTable())
                .schemas(schema == null ? new String[0] : new String[]{schema})
                .baselineOnMigrate(true);

        customizeFlyway(configuration);

        Flyway flyway = configuration.load();
        flyway.migrate();
    }

    protected void customizeFlyway(FluentConfiguration configuration) {
        // can be overridden by subclasses
    }

    private static List<InitScript> getInitScripts(List<String> initScriptPath) {
        return initScriptPath.stream()
                .map(path -> {
                    String script = PersistenceClasspathResources.readString(path);
                    return new InitScript(path, script);
                })
                .collect(Collectors.toList());
    }

    @SuppressWarnings("resource")
    @Nullable
    private static InspectImageResponse findImage(String imageNameWithTag) {
        try {
            return DockerClientFactory.instance()
                    .client()
                    .inspectImageCmd(imageNameWithTag)
                    .exec();
        } catch (NotFoundException e) {
            return null;
        }
    }

    private static class ImageResult {

        private final DockerImageName dockerImageName;
        private final ContainerConfig config;

        private ImageResult(DockerImageName dockerImageName, ContainerConfig config) {
            this.dockerImageName = dockerImageName;
            this.config = config;
        }
    }
}
