package com.miro.persistence.tooling.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class FlywayVersionUtilsTest {

    @Test
    public void getVersion_whenVersionContainsMajorVersionOnly_shouldSucceed() {
        assertThat(FlywayVersionUtils.getVersion("V47__widgets_type_id_not_null.sql").toString()).isEqualTo("47");
    }

    @Test
    public void getVersion_whenVersionContainsAMinorVersionWithNoTrailingZero_shouldSucceed() {
        assertThat(FlywayVersionUtils.getVersion("V47_1__widgets_type_id_not_null.sql").toString()).isEqualTo("47.1");
    }

    @Test
    public void getVersion_whenVersionContainsAMinorVersionWithTrailingZero_shouldSucceed() {
        assertThat(FlywayVersionUtils.getVersion("V250_10__widgets_type_id_not_null.sql").toString()).isEqualTo("250"
                + ".10");
    }

    @Test
    public void getVersion_whenFilenameContainsSpaces_shouldThrowAnIllegalArgumentException() {
        var filename = "V264__add_tools_restriction_to workshop_mode_table.sql";
        assertThatThrownBy(() -> FlywayVersionUtils.getVersion(filename))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContainingAll("Illegal resource name", filename);
    }
}
