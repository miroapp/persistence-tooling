package com.miro.persistence.tooling.core;

import static java.nio.charset.StandardCharsets.UTF_8;

import jakarta.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Sergey Chernov
 */
public final class PersistenceClasspathResources {

    /**
     * Load resource file as a single string.
     *
     * @param resource classpath resource name
     * @return resource content as string
     */
    @Nonnull
    public static String readString(String resource) throws IllegalStateException {
        return new String(readBytes(resource), UTF_8);
    }

    /**
     * Load resource file as a byte array.
     *
     * @param resource classpath resource name
     * @return resource content as byte array
     */
    @Nonnull
    static byte[] readBytes(String resource) throws IllegalStateException {
        Objects.requireNonNull(resource, "resource");
        List<URL> urls = getResourceURLs(resource);

        if (urls.isEmpty()) {
            throw new IllegalStateException(String.format("Missing resource [%s]", resource));
        }
        if (urls.size() > 1) {
            throw new IllegalStateException(String.format("Ambiguity resource [%s]: %s", resource, urls));
        }

        URL url = urls.get(0);
        try (InputStream in = url.openStream()) {
            return readAllBytes(in);
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Error while reading resource [%s]", resource), e);
        }
    }

    static List<URL> getResourceURLs(String resource) {
        ClassLoader classLoader = PersistenceClasspathResources.class.getClassLoader();
        try {
            return Collections.list(classLoader.getResources(resource));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list [" + resource + "] resources", e);
        }
    }

    private static byte[] readAllBytes(InputStream in) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        try {
            while ((len = in.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read input stream", e);
        }
        return baos.toByteArray();
    }

    private PersistenceClasspathResources() {
    }
}
