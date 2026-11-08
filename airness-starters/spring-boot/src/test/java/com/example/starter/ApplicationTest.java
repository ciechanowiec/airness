package com.example.starter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DatabaseTest
class ApplicationTest {

    private final PostgreSQLContainer database;

    ApplicationTest(PostgreSQLContainer database) {
        this.database = database;
    }

    @Test
    void startsTheApplicationFromTheCommandLine() {
        // Every setting the application refuses to start without is passed, which is what a deployment
        // does: the database it reads and the first administrator it writes into an empty one.
        String[] arguments = {
            "--server.port=0",
            "--spring.datasource.url=%s".formatted(this.database.getJdbcUrl()),
            "--spring.datasource.username=%s".formatted(this.database.getUsername()),
            "--spring.datasource.password=%s".formatted(this.database.getPassword()),
            "--app.administrator.email=%s".formatted(Caller.ADMINISTRATOR),
            "--app.administrator.password=%s".formatted(Caller.SECRET)
        };
        assertDoesNotThrow(() -> Application.main(arguments));
    }
}
