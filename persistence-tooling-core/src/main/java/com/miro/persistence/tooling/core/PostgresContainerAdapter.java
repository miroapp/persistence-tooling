package com.miro.persistence.tooling.core;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CommitCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Image;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.script.ScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.delegate.DatabaseDelegate;
import org.testcontainers.ext.ScriptUtils;
import org.testcontainers.ext.ScriptUtils.UncategorizedScriptException;
import org.testcontainers.utility.DockerImageName;

/**
 * An adapter from {@link PostgreSQLContainer} to {@link PostgresExecutable}.
 * <br>
 * Additionally, it can create a new image with a state of a container.
 *
 * @author Nikolai Averin
 * @author Ignat Nikitenko
 * @author Sergey Chernov
 * @author Konstantin Subbotin
 * @implNote The implementation synchronizes on {@code this}, the current object
 */
public class PostgresContainerAdapter implements PostgresExecutable {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresContainerAdapter.class);

    private final DockerImageName postgresBaseImage;

    private PostgresContainerAdapter(String postgresBaseImageName) {
        postgresBaseImage = DockerImageName.parse(postgresBaseImageName).asCompatibleSubstituteFor("postgres");
    }

    private PostgreSQLContainer<?> container;

    public static PostgresContainerAdapter createPostgresContainerAdapterFromBaseImage(String postgresBaseImageName) {
        return new PostgresContainerAdapter(postgresBaseImageName);
    }

    @Override
    public synchronized String start(String dbName, String user, String password, List<InitScript> initScripts) {
        Assert.state(container == null, "postgres is already running");

        PostgreSQLContainer<?> newContainer = new InitScriptedPostgreSQLContainer(postgresBaseImage, initScripts)
                .withDatabaseName(dbName)
                .withUsername(user)
                .withPassword(password);
        /*
        we wanna use DOCKER COMMIT to create a new image with a current state of the container (after migrations)
        by default postgres saves data in PGDATA that is mounted to a volume "/var/lib/postgresql/data" but DOCKER COMMIT doesn't save volumes
        so, we use a custom location for PGDATA outside the volume
        an alternative option is to create own custom postgres image from scratch without volumes, but it looks complicated to maintain
        example - https://github.com/docker-library/postgres/blob/master/11/alpine/Dockerfile
        */
        newContainer.addEnv("PGDATA", "/var/lib/postgresql/data-no-mounted");

        newContainer.start();
        container = newContainer;
        return container.getJdbcUrl();
    }

    @Override
    public synchronized void stop() {
        Assert.state(container != null, "postgres isn't started yet");

        container.stop();
        container = null;
    }

    @Override
    public String getBaseImageName() {
        return postgresBaseImage.toString();
    }

    @Override
    public synchronized void saveState(String imageName, String tag) {
        try {
            Assert.state(container != null, "postgres isn't started yet");

            // flush all data
            doCheckpoint(container);

            commitContainer(container, imageName, tag);
        } catch (RuntimeException e) {
            stop();
            throw new RuntimeException("Saving postgres container state failed", e);
        }
    }

    public static void removeOldExistedImages(String imageName) {
        DockerClient dockerClient = DockerClientFactory.instance().client();
        List<Image> images = dockerClient.listImagesCmd().withImageNameFilter(imageName).exec();
        logCurrentImagesList(images, imageName);
        images.stream()
                .sorted(Comparator.comparing(Image::getCreated).reversed())
                .forEach(image -> removeExistedImage(imageName, dockerClient, image));
    }

    private static void logCurrentImagesList(Iterable<Image> images, String imageName) {
        StringBuilder sb = new StringBuilder().append("Current images list for image name '").append(imageName).append("':\n");
        images.forEach(image -> sb.append("Image ").append(image.getId())
                .append(" for '").append(imageName).append("'")
                .append(" date: ").append(image.getCreated())
                .append(" tags: [").append(String.join(",", image.getRepoTags())).append("]")
                .append("\n"));
        LOGGER.info(sb.toString());
    }

    private static void removeExistedImage(String imageName, DockerClient dockerClient, Image image) {
        try {
            LOGGER.info("Try to remove old image {} for '{}' date: {} tags: {}", image.getId(), imageName, image.getCreated(), image.getRepoTags());
            dockerClient.removeImageCmd(image.getId()).exec();
        } catch (Exception ex) {
            LOGGER.error("Remove old image {} for '{}' date: {} tags: {} failed", image.getId(), imageName, image.getCreated(), image.getRepoTags(), ex);
        }
    }

    private static void doCheckpoint(@SuppressWarnings("TypeMayBeWeakened") PostgreSQLContainer<?> container) {
        try {
            container.execInContainer("psql", "-c", "checkpoint");
            LOGGER.info("Postgres checkpoint finished");
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void commitContainer(PostgreSQLContainer<?> container, String imageName, String tag) {
        CommitCmd cmd = container.getDockerClient().commitCmd(container.getContainerId())
                .withMessage("Container for integration tests. It uses non default location for PGDATA which is not mounted to a volume")
                .withRepository(imageName)
                .withTag(tag)
                // set this label to skip image deletion by ryuk
                // https://stackoverflow.com/a/71613399
                .withLabels(Collections.singletonMap("org.testcontainers", "false"));
        String imageId = cmd.exec();
        LOGGER.info("Container commit finished. New image '{}:{}' with id {} has been created for containerId {}",
                imageName, tag, imageId, container.getContainerId());
    }

    /**
     * The container executes SQL scripts from {@code initScriptPath} after start
     */
    private static class InitScriptedPostgreSQLContainer extends PostgreSQLContainer<InitScriptedPostgreSQLContainer> {

        private final List<InitScript> initScripts;

        InitScriptedPostgreSQLContainer(DockerImageName imageName, List<InitScript> initScripts) {
            super(imageName);
            this.initScripts = initScripts;
        }

        @Override
        protected void containerIsStarted(InspectContainerResponse containerInfo) {
            super.containerIsStarted(containerInfo);
            runInitScripts(getDatabaseDelegate(), initScripts);
        }

        /**
         * JdbcDatabaseContainer supports loading initScript only from classpath, so was need custom runInitScript method load from filesystem
         */
        private static void runInitScripts(DatabaseDelegate databaseDelegate, List<InitScript> initScripts) {
            for (InitScript script : initScripts) {
                try {
                    ScriptUtils.executeDatabaseScript(databaseDelegate, script.scriptPath(), script.script());
                } catch (ScriptException e) {
                    throw new UncategorizedScriptException("Error while executing init script: " + script.scriptPath(), e);
                }
            }
        }
    }
}
