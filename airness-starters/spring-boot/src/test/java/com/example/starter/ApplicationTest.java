package com.example.starter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;
import java.util.stream.Stream;
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
        // does: the database it reads, the first administrator it writes into an empty one, and where it
        // stores files and has their pictures rendered.
        List<String> given = List.of(
            "--server.port=0",
            "--spring.datasource.url=%s".formatted(this.database.getJdbcUrl()),
            "--spring.datasource.username=%s".formatted(this.database.getUsername()),
            "--spring.datasource.password=%s".formatted(this.database.getPassword()),
            "--app.administrator.email=%s".formatted(Caller.ADMINISTRATOR),
            "--app.administrator.password=%s".formatted(Caller.SECRET)
        );
        String[] arguments = Stream.concat(
            given.stream(),
            Containers.mediaSettings().entrySet().stream().map(
                setting -> "--%s=%s".formatted(setting.getKey(), setting.getValue())
            )
        ).toArray(String[]::new);
        assertDoesNotThrow(() -> Application.main(arguments));
    }
}
