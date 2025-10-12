package com.miro.persistence.tooling.core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;

import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StreamUtils;

/**
 * @author Sergey Chernov
 */
public final class FlywayChecksumUtils {

    /**
     * Flyway style of file prefix
     */
    private static final String FILESYSTEM_PREFIX = "filesystem:";

    /**
     * Calculate checksum from migration files & base image name. Consists of two parts separated by dash:
     * <ul>
     *     <li>last migration file version, e.g. "300_1"</li>
     *     <li>first 6 hex digits of SHA-1 hash sum of all migration files & base image name</li>
     * </ul>
     *
     * @param baseImageName          Name of base image user to
     * @param initScriptPaths        Paths to init scripts to be used in checksum calculation
     * @param migrationResourcesPath Path to migration files
     * @return Checksum
     */
    public static String calculateChecksum(
            String baseImageName,
            @Nullable List<String> initScriptPaths,
            String migrationResourcesPath
    ) {
        Objects.requireNonNull(baseImageName, "baseImageName is null");
        List<Resource> resources = getMigrations(migrationResourcesPath);
        MigrationVersion latestVersion = getLatestVersion(migrationResourcesPath, resources);

        MessageDigest sha1 = sha1();
        sha1.update((baseImageName + "\n").getBytes(UTF_8));
        if (initScriptPaths != null) {
            ResourceLoader resourceLoader = new DefaultResourceLoader();
            initScriptPaths.forEach(path -> updateDigest(sha1, resourceLoader.getResource(adaptResourcePath(path))));
        }
        resources.stream()
                .sorted(comparing(resource -> FlywayVersionUtils.getVersion(resource.getFilename())))
                .forEach(resource -> updateDigest(sha1, resource));

        // note: "-" is used as a separator, because "~" is not allowed by docker
        return "V" + latestVersion.toString().replace('.', '_') + "-" + digestFirst6(sha1);
    }

    private static String adaptResourcePath(String resourcePath) {
        String resourcePathToUse = resourcePath;
        if (resourcePathToUse.startsWith(FILESYSTEM_PREFIX)) {
            // spring style vs flyway style
            resourcePathToUse = "file:" + resourcePathToUse.substring(FILESYSTEM_PREFIX.length());
        }
        return resourcePathToUse;
    }

    private static List<Resource> getMigrations(String resourcePath) {
        String resourcePathToUse = adaptResourcePath(resourcePath);
        ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
        try {
            return Arrays.asList(resourcePatternResolver.getResources(resourcePathToUse + "/*.sql"));
        } catch (IOException e) {
            throw new UncheckedIOException("Error while listing resources:" + resourcePath, e);
        }
    }

    private static MigrationVersion getLatestVersion(String resourcePath, Collection<Resource> resources) {
        return resources.stream()
                .map(resource -> Objects.requireNonNull(resource.getFilename(), resource + " fileName is null"))
                .map(FlywayVersionUtils::getVersion)
                .max(naturalOrder())
                .orElseThrow(() -> new IllegalStateException("Migrations list is empty for [" + resourcePath + "]"));
    }

    private static MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void updateDigest(MessageDigest digest, Resource resource) {
        digest.update((resource.getFilename() + "\n").getBytes(UTF_8));
        try (InputStream in = resource.getInputStream()) {
            byte[] content = StreamUtils.copyToByteArray(in);
            digest.update(content);
        } catch (IOException e) {
            throw new UncheckedIOException("Error while reading " + resource, e);
        }
    }

    private static String digestFirst6(MessageDigest digest) {
        return new BigInteger(1, digest.digest()).toString(16).substring(0, 6);
    }

    private FlywayChecksumUtils() {
    }
}
