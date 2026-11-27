package eu.ciechanowiec.airness.errorprone;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AirnessAsyncIsBridgedTest {

    private static final String CHECK = "AirnessAsyncIsBridged";

    private static final String FLUX = """
        package reactor.core.publisher;

        import java.util.function.Function;
        import java.util.stream.Stream;

        public abstract class Flux<T> {
            public static <T> Flux<T> just(T value) {
                throw new UnsupportedOperationException();
            }

            public abstract <R> Flux<R> map(Function<T, R> mapper);

            public abstract Iterable<T> toIterable();

            public abstract Stream<T> toStream();

            public abstract T blockLast();

            public abstract void subscribe();
        }
        """;

    private static final String WORDS = """
        package example;

        import reactor.core.publisher.Flux;

        final class Words {
            static Flux<String> stream() {
                throw new UnsupportedOperationException();
            }
        }
        """;

    @Test
    void reportsStartingAsynchronousWork(@TempDir Path directory) {
        String source = """
            package example;

            import java.util.concurrent.CompletableFuture;
            import java.util.concurrent.SubmissionPublisher;

            class Work {
                void start(Runnable task, CompletableFuture<String> received) {
                    CompletableFuture.runAsync(task).join(); /* reported */
                    CompletableFuture.supplyAsync(() -> "value").join(); /* reported */
                    received.thenApplyAsync(String::length).join(); /* reported */
                    new SubmissionPublisher<String>().close(); /* reported */
                }
            }
            """;
        assertEquals(ErrorProneRule.marked(source), ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void reportsAReactiveOperatorAndAcceptsTheBridges(@TempDir Path directory) {
        String source = """
            package example;

            import java.util.function.Function;
            import reactor.core.publisher.Flux;

            class Reader {
                void read() {
                    Words.stream().map(String::length).subscribe();
                    Function<String, Flux<String>> lift = Flux::just;
                    for (String word : Words.stream().toIterable()) {
                        word.length();
                    }
                    Words.stream().toStream().count();
                    Words.stream().blockLast();
                }
            }
            """;
        assertEquals(List.of(8L, 8L, 9L), ErrorProneRule.findings(directory, CHECK, source, FLUX, WORDS));
    }

    @Test
    void acceptsWaitingOnAReceivedFuture(@TempDir Path directory) {
        String source = """
            package example;

            import java.util.List;
            import java.util.concurrent.CompletableFuture;

            class Waiting {
                String waited(List<CompletableFuture<String>> sent) {
                    CompletableFuture.allOf(sent.toArray(CompletableFuture[]::new)).join();
                    return sent.getFirst().thenApply(String::trim).join();
                }
            }
            """;
        assertEquals(List.of(), ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void refusesASuppression(@TempDir Path directory) {
        String source = """
            package example;

            import java.util.concurrent.CompletableFuture;

            class Work {
                @SuppressWarnings("AirnessAsyncIsBridged")
                void start(Runnable task) {
                    CompletableFuture.runAsync(task).join();
                }
            }
            """;
        assertEquals(List.of(8L), ErrorProneRule.findings(directory, CHECK, source));
    }
}
