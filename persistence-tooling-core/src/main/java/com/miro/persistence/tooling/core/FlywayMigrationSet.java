package com.miro.persistence.tooling.core;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Flyway SQL migration set data, which can be discovered on the classpath or the filesystem directory.
 *
 * @author Sergey Chernov
 */
public class FlywayMigrationSet {

    @Nullable
    private final String schema;
    private final String migrationResourcesPath;
    @Nullable
    private final String baseDockerImageName;
    private final String dockerImageName;
    private final List<String> initScriptPaths;
    private final String flywayTable;

    private FlywayMigrationSet(Builder builder) {
        this.schema = builder.schema;
        this.migrationResourcesPath = Objects.requireNonNull(builder.migrationResourcesPath, "migrationResourcesPath is null");
        this.baseDockerImageName = builder.baseDockerImageName;
        this.dockerImageName = Objects.requireNonNull(builder.dockerImageName, "dockerImageName is null");
        this.initScriptPaths = builder.initScriptPaths;
        this.flywayTable = Objects.requireNonNull(builder.flywayTable, "flywayTable is null");
    }

    public static Builder builder() {
        return new Builder();
    }

    @Nullable
    public String getSchema() {
        return schema;
    }

    public String getMigrationResourcesPath() {
        return migrationResourcesPath;
    }

    @Nullable
    public String getBaseDockerImageName() {
        return baseDockerImageName;
    }

    public String getDockerImageName() {
        return dockerImageName;
    }

    public List<String> getInitScriptPaths() {
        return initScriptPaths;
    }

    public String getFlywayTable() {
        return flywayTable;
    }

    public static class Builder {

        private final List<String> initScriptPaths = new ArrayList<>();

        @Nullable
        private String schema;
        private String migrationResourcesPath = "db/migration";
        private String baseDockerImageName;
        private String dockerImageName;
        private String flywayTable = "schema_version";

        public Builder schema(String schema) {
            this.schema = schema;
            return this;
        }

        public Builder migrationResourcesPath(String migrationResourcesPath) {
            this.migrationResourcesPath = migrationResourcesPath;
            return this;
        }

        public Builder baseDockerImageName(String baseDockerImageName) {
            this.baseDockerImageName = baseDockerImageName;
            return this;
        }

        public Builder dockerImageName(String dockerImageName) {
            this.dockerImageName = dockerImageName;
            return this;
        }

        public Builder initScriptPaths(String... initScriptPaths) {
            Collections.addAll(this.initScriptPaths, initScriptPaths);
            return this;
        }

        public Builder flywayTable(String flywayTable) {
            this.flywayTable = flywayTable;
            return this;
        }

        public FlywayMigrationSet build() {
            return new FlywayMigrationSet(this);
        }
    }

    @Override
    public String toString() {
        return "FlywayMigrationSet{" +
            "schema='" + schema + '\'' +
            ", migrationResourcesPath='" + migrationResourcesPath + '\'' +
            ", dockerImageName='" + dockerImageName + '\'' +
            ", initScriptPaths='" + initScriptPaths + '\'' +
            ", flywayTable='" + flywayTable + '\'' +
            '}';
    }
}
