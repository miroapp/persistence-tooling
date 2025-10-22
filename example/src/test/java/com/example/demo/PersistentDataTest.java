package com.example.demo;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@ActiveProfiles("test")
@SpringBootTest
@ContextConfiguration(classes = PersistenceTestConfiguration.class)
class PersistentDataTest {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void shouldCreateUser() {
        jdbc.update("INSERT INTO users (name) VALUES (:name)",
                Map.of("name", "Sergey"));
    }
}
