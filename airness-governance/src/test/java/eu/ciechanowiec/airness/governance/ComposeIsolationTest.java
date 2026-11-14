package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A compose file fixes a host port when it writes the port or a default for it, and fixes a container name when a
 * service names its container.
 */
class ComposeIsolationTest {

    private static final String DATABASE = "5432";
    private static final int DEFAULTED_LINE = 12;
    private static final int FLOW_LINE = 17;

    @Test
    void readsTheHostPortOfEveryShortSyntax() {
        assertAll(
            () -> assertEquals("", ComposeIsolation.hostPort("8080")),
            () -> assertEquals("8080", ComposeIsolation.hostPort("8080:80")),
            () -> assertEquals("8080", ComposeIsolation.hostPort("127.0.0.1:8080:80/tcp")),
            () -> assertEquals("", ComposeIsolation.hostPort("127.0.0.1::80")),
            () -> assertEquals("8080", ComposeIsolation.hostPort("[::1]:8080:80")),
            () -> assertEquals("", ComposeIsolation.hostPort("[::1]::80")),
            () -> assertEquals("${APP_PORT}", ComposeIsolation.hostPort("127.0.0.1:${APP_PORT}:80")),
            () -> assertEquals("${PORT:-5432}", ComposeIsolation.hostPort("${PORT:-5432}:5432")),
            () -> assertEquals("${PORT:?choose a port}", ComposeIsolation.hostPort("${PORT:?choose a port}:5432"))
        );
    }

    @Test
    void holdsALiteralOrADefaultedPortFixedAndAVariableOrNothingChosen() {
        assertAll(
            () -> assertTrue(ComposeIsolation.fixed(DATABASE)),
            () -> assertTrue(ComposeIsolation.fixed("${PORT:-5432}")),
            () -> assertTrue(ComposeIsolation.fixed("${PORT-5432}")),
            () -> assertTrue(ComposeIsolation.fixed("${PORT}0")),
            () -> assertFalse(ComposeIsolation.fixed("${PORT}")),
            () -> assertFalse(ComposeIsolation.fixed("${PORT:?choose a port}")),
            () -> assertFalse(ComposeIsolation.fixed(""))
        );
    }

    @Test
    void namesEveryFixedPortOfEverySyntaxWithItsServiceAndLine() {
        String compose = """
            services:
              web:
                image: nginx:1.29.1
                ports:
                  - "127.0.0.1:8080:80" # the site
                  - 127.0.0.1::443
                  - "${WEB_PORT}:8443"
              db:
                image: postgres:18-alpine
                ports:
                  - target: 5432
                    published: ${DB_PORT:-5432}
                  - target: 5433
                    published: ${DB_PORT}
              cache:
                image: valkey/valkey:8.1.3
                ports: ["6379:6379", "127.0.0.1::6380"]
            volumes:
              ports:
            """;
        assertEquals(
            List.of(
                new Located<>(5, "web publishes host port 8080"),
                new Located<>(DEFAULTED_LINE, "db publishes host port ${DB_PORT:-5432}"),
                new Located<>(FLOW_LINE, "cache publishes host port 6379")
            ),
            ComposeIsolation.fixedPorts(compose)
        );
    }

    @Test
    void namesEveryContainerNameAServiceFixes() {
        String compose = """
            name: shop
            services:
              db:
                container_name: shop-db
                image: postgres:18-alpine
              web:
                image: nginx:1.29.1
                labels:
                  container_name: not-a-service-key
            """;
        assertEquals(List.of(new Located<>(4, "db is named shop-db")), ComposeIsolation.containerNames(compose));
    }

    @Test
    void readsNothingOutsideTheServices() {
        String compose = """
            # ports: ["1:1"]
            ---
            x-defaults:
              ports:
                - "8080:80"
            """;
        assertAll(
            () -> assertEquals(List.of(), ComposeIsolation.fixedPorts(compose)),
            () -> assertEquals(List.of(), ComposeIsolation.containerNames(compose))
        );
    }
}
