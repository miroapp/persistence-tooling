package com.example.demo;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.Nullable;
import org.postgresql.Driver;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * @author Sergey Chernov
 */
public final class DockerDataSourceUtils {

    /**
     * Create a DataSource for a container which is up and running.
     *
     * @param name
     * @param container
     * @param schema
     * @return
     */
    public static HikariDataSource createDataSource(String name, PostgreSQLContainer<?> container, @Nullable String schema) {
        HikariDataSource hikariDataSource = new HikariDataSource();
        hikariDataSource.setUsername(container.getUsername());
        hikariDataSource.setPassword(container.getPassword());
        hikariDataSource.setMinimumIdle(0);
        hikariDataSource.setMaximumPoolSize(50);
        hikariDataSource.setIdleTimeout(10000);
        hikariDataSource.setConnectionTimeout(10000);
        hikariDataSource.setAutoCommit(true);
        hikariDataSource.setPoolName(name);
        hikariDataSource.setDriverClassName(Driver.class.getName());
        hikariDataSource.addDataSourceProperty("preferQueryMode", "simple");
        hikariDataSource.setJdbcUrl(createJdbcUrl(container, schema));
        return hikariDataSource;
    }

    private static String createJdbcUrl(PostgreSQLContainer<?> container, @Nullable String schema) {
        var url = container.getJdbcUrl();
        if (schema == null) {
            return url;
        }
        if (url.contains("?")) {
            return url + "&currentSchema=" + schema;
        } else {
            return url + "?currentSchema=" + schema;
        }
    }

    private DockerDataSourceUtils() {
    }
}
