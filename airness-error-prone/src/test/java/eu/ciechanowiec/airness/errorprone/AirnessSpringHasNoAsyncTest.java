package eu.ciechanowiec.airness.errorprone;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AirnessSpringHasNoAsyncTest {

    private static final String CHECK = "AirnessSpringHasNoAsync";

    private static final String ASYNC = """
        package org.springframework.scheduling.annotation;

        public @interface Async {
            String value() default "";
        }
        """;

    private static final String ENABLE = """
        package org.springframework.scheduling.annotation;

        public @interface EnableAsync {
        }
        """;

    @Test
    void reportsAsyncAndItsEnablingInEachSpelling(@TempDir Path directory) {
        String source = """
            package example;

            import org.springframework.scheduling.annotation.Async;
            import org.springframework.scheduling.annotation.EnableAsync;

            @EnableAsync /* reported */
            class Mailer {
                @Async /* reported */
                public void send() {
                }

                @org.springframework.scheduling.annotation.Async("mail") /* reported */
                public void sendLater() {
                }
            }
            """;
        assertEquals(ErrorProneRule.marked(source), ErrorProneRule.findings(directory, CHECK, source, ASYNC, ENABLE));
    }

    @Test
    void acceptsAnotherAnnotationNamedAsync(@TempDir Path directory) {
        String source = """
            package example;

            class Mailer {
                @interface Async {
                }

                @Async
                public void send() {
                }
            }
            """;
        assertEquals(List.of(), ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void refusesASuppression(@TempDir Path directory) {
        String source = """
            package example;

            import org.springframework.scheduling.annotation.Async;

            class Mailer {
                @SuppressWarnings("AirnessSpringHasNoAsync")
                @Async
                public void send() {
                }
            }
            """;
        assertEquals(List.of(7L), ErrorProneRule.findings(directory, CHECK, source, ASYNC));
    }
}
