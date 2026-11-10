package com.example.starter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.core.NestedExceptionUtils;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DatabaseTest
class ApplicationTest {

    // A database of its own, created by the one test that starts against it, because the database the
    // suite shares already holds the first administrator and so never asks for a password again.
    private static final String EMPTY = "first_start";

    private final PostgreSQLContainer database;

    ApplicationTest(PostgreSQLContainer database) {
        this.database = database;
    }

    // Every setting the application refuses to start without, which is what a deployment passes: the
    // database it reads and the first administrator it writes into an empty one.
    private String[] arguments(String url, String password) {
        return new String[] {
            "--server.port=0",
            "--spring.datasource.url=%s".formatted(url),
            "--spring.datasource.username=%s".formatted(this.database.getUsername()),
            "--spring.datasource.password=%s".formatted(this.database.getPassword()),
            "--app.administrator.email=%s".formatted(Caller.ADMINISTRATOR),
            "--app.administrator.password=%s".formatted(password)
        };
    }

    // Creates the empty database inside the container that serves the suite, and answers its address.
    @SneakyThrows
    private String empty() {
        this.database.execInContainer("createdb", "--username", this.database.getUsername(), EMPTY);
        return "jdbc:postgresql://%s:%d/%s".formatted(
            this.database.getHost(), this.database.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT), EMPTY
        );
    }

    @Test
    void startsTheApplicationFromTheCommandLine() {
        String[] arguments = this.arguments(this.database.getJdbcUrl(), Caller.SECRET);
        assertDoesNotThrow(() -> Application.main(arguments));
    }

    @Test
    void refusesToStartOnAnEmptyDatabaseWhenTheFirstAdministratorsPasswordIsBlank() {
        String[] arguments = this.arguments(this.empty(), "");
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> Application.main(arguments));
        String cause = String.valueOf(NestedExceptionUtils.getMostSpecificCause(refused).getMessage());
        assertTrue(cause.startsWith("The first administrator was not written"), cause);
    }
}
