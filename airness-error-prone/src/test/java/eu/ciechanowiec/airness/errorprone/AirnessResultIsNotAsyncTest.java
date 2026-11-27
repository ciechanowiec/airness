package eu.ciechanowiec.airness.errorprone;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AirnessResultIsNotAsyncTest {

    private static final String CHECK = "AirnessResultIsNotAsync";

    private static final String PUBLISHER = """
        package org.reactivestreams;

        public interface Publisher<T> {
        }
        """;

    private static final String MONO = """
        package reactor.core.publisher;

        public abstract class Mono<T> implements org.reactivestreams.Publisher<T> {
        }
        """;

    private static final String DEFERRED = """
        package org.springframework.web.context.request.async;

        public class DeferredResult<T> {
        }
        """;

    private static final String GET_MAPPING = """
        package org.springframework.web.bind.annotation;

        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;

        @Retention(RetentionPolicy.RUNTIME)
        public @interface GetMapping {
            String[] value() default {};
        }
        """;

    @Test
    void reportsAPromiseInADeclaredReturnType(@TempDir Path directory) {
        String source = """
            package example;

            import java.util.concurrent.CompletableFuture;
            import java.util.concurrent.CompletionStage;
            import java.util.concurrent.Flow;
            import java.util.concurrent.Future;
            import org.springframework.web.context.request.async.DeferredResult;
            import reactor.core.publisher.Mono;

            interface Service {
                CompletableFuture<String> completable(); /* reported */

                CompletionStage<String> stage(); /* reported */

                Future<String> future(); /* reported */

                Flow.Publisher<String> flow(); /* reported */

                Mono<String> mono(); /* reported */

                DeferredResult<String> deferred(); /* reported */
            }
            """;
        assertEquals(
            ErrorProneRule.marked(source),
            ErrorProneRule.findings(directory, CHECK, source, PUBLISHER, MONO, DEFERRED)
        );
    }

    @Test
    void reportsAFieldAndARecordComponent(@TempDir Path directory) {
        String source = """
            package example;

            import java.util.concurrent.Future;

            class Holder {
                private Future<String> pending;

                record Pending(Future<String> value) {
                }
            }
            """;
        assertEquals(List.of(6L, 8L), ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void reportsACallableOnlyWhereAHandlerReturnsIt(@TempDir Path directory) {
        String source = """
            package example;

            import java.util.concurrent.Callable;
            import org.springframework.web.bind.annotation.GetMapping;

            class Pages {
                @GetMapping("/slow")
                Callable<String> slow() {
                    return () -> "page";
                }

                Callable<String> task() {
                    return () -> "work";
                }
            }
            """;
        assertEquals(List.of(8L), ErrorProneRule.findings(directory, CHECK, source, GET_MAPPING));
    }

    @Test
    void acceptsAnOverrideALocalAndAParameter(@TempDir Path directory) {
        String source = """
            package example;

            import java.util.concurrent.CompletableFuture;
            import java.util.concurrent.ExecutionException;
            import java.util.concurrent.Future;
            import java.util.concurrent.FutureTask;

            class Waiting {
                String waited(Future<String> pending) throws InterruptedException, ExecutionException {
                    CompletableFuture<String> local = CompletableFuture.completedFuture(pending.get());
                    return local.join();
                }

                static final class Task %s FutureTask<String> {
                    Task() {
                        super(() -> "done");
                    }

                    @Override
                    public String toString() {
                        return "task";
                    }
                }
            }
            """.formatted("extends");
        assertEquals(List.of(), ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void refusesASuppression(@TempDir Path directory) {
        String source = """
            package example;

            import java.util.concurrent.Future;

            interface Service {
                @SuppressWarnings("AirnessResultIsNotAsync")
                Future<String> future();
            }
            """;
        assertEquals(List.of(7L), ErrorProneRule.findings(directory, CHECK, source));
    }
}
