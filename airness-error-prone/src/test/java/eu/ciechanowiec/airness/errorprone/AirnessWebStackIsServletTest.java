package eu.ciechanowiec.airness.errorprone;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AirnessWebStackIsServletTest {

    private static final String CHECK = "AirnessWebStackIsServlet";

    private static final String WEB_CLIENT = """
        package org.springframework.web.reactive.function.client;

        public interface WebClient {
            static <T> T create() {
                return null;
            }
        }
        """;

    private static final String EXCHANGE = """
        package org.springframework.web.server;

        public interface ServerWebExchange {
        }
        """;

    private static final String STATUS = """
        package org.springframework.web.server;

        public class ResponseStatusException extends RuntimeException {
        }
        """;

    private static final String CONNECTION = """
        package io.r2dbc.spi;

        public interface ConnectionFactory {
        }
        """;

    @Test
    void reportsAReactiveTypeWhereverItIsReferenced(@TempDir Path directory) {
        String source = """
            package example;

            import io.r2dbc.spi.ConnectionFactory;
            import org.springframework.web.reactive.function.client.WebClient;
            import org.springframework.web.server.ServerWebExchange;

            class Clients {
                private final ConnectionFactory connections = null; /* reported */

                Object client(ServerWebExchange exchange) { /* reported */
                    WebClient client = WebClient.create(); /* reported */ /* reported */
                    return org.springframework.web.reactive.function.client.WebClient.create(); /* reported */
                }
            }
            """;
        assertEquals(
            ErrorProneRule.marked(source),
            ErrorProneRule.findings(directory, CHECK, source, WEB_CLIENT, EXCHANGE, CONNECTION)
        );
    }

    @Test
    void acceptsTheServletTypesOfASharedPackage(@TempDir Path directory) {
        String source = """
            package example;

            import org.springframework.web.server.ResponseStatusException;

            class Pages {
                void refuse() {
                    throw new ResponseStatusException();
                }
            }
            """;
        assertEquals(List.of(), ErrorProneRule.findings(directory, CHECK, source, STATUS));
    }

    @Test
    void refusesASuppression(@TempDir Path directory) {
        String source = """
            package example;

            import org.springframework.web.reactive.function.client.WebClient;

            class Clients {
                @SuppressWarnings("AirnessWebStackIsServlet")
                private final WebClient client = null;
            }
            """;
        assertEquals(List.of(7L), ErrorProneRule.findings(directory, CHECK, source, WEB_CLIENT));
    }
}
