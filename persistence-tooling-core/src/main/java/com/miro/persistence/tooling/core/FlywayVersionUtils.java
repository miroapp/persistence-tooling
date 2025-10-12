package com.miro.persistence.tooling.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.api.MigrationVersion;

/**
 * @author Sergey Chernov
 */
public final class FlywayVersionUtils {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+(_(\\d+))?)__\\w+\\.sql$");

    /**
     * Returns flyway version, sub-version supported (but only 1 level: 1.2 is ok, 1.2.3 is not).
     *
     * @param fileName resource last part of the path, e.g. "V47_10__widgets_type_id_not_null.sql"
     * @return parsed version value, e.g. "47.10"
     */
    public static MigrationVersion getVersion(String fileName) {
        Matcher matcher = VERSION_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Illegal resource name [" + fileName + "], "
                    + "should match " + VERSION_PATTERN);
        }
        return MigrationVersion.fromVersion(matcher.group(1));
    }

    private FlywayVersionUtils() {
    }
}
