package eu.ciechanowiec.airness.errorprone;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AirnessThreadIsVirtualTest {

    private static final String CHECK = "AirnessThreadIsVirtual";

    private static final String SPRING_EXECUTOR = """
        package org.springframework.scheduling.concurrent;

        public class ThreadPoolTaskExecutor {
        }
        """;

    @ParameterizedTest
    @ValueSource(
        strings = {
            "Executors.newFixedThreadPool(4)", "Executors.newCachedThreadPool()", "Executors.newSingleThreadExecutor()",
            "Executors.newScheduledThreadPool(1)", "Executors.newSingleThreadScheduledExecutor()",
            "Executors.newWorkStealingPool()", "Executors.newFixedThreadPool(4, Thread.ofVirtual().factory())"
        }
    )
    void reportsAPlatformPoolFactory(String pool, @TempDir Path directory) {
        String source = """
            package example;

            import java.util.concurrent.Executors;

            class Pools {
                Object pool() {
                    return %s;
                }
            }
            """.formatted(pool);
        assertEquals(List.of(7L), ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void reportsAStaticallyImportedFactoryAndAReferenceToOne(@TempDir Path directory) {
        String source = """
            package example;

            import static java.util.concurrent.Executors.newCachedThreadPool;

            import java.util.concurrent.ExecutorService;
            import java.util.concurrent.Executors;
            import java.util.concurrent.ThreadFactory;
            import java.util.function.Supplier;

            class Pools {
                ExecutorService pool() {
                    return newCachedThreadPool(); /* reported */
                }

                Supplier<ThreadFactory> factory() {
                    return Executors::defaultThreadFactory; /* reported */
                }
            }
            """;
        assertEquals(ErrorProneRule.marked(source), ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void reportsAPlatformThreadInEachSpelling(@TempDir Path directory) {
        String source = """
            package example;

            import java.util.concurrent.Executors;

            class Threads {
                void start(Runnable task) {
                    new Thread(task).start(); /* reported */
                    new Thread(task, "named").start(); /* reported */
                    Thread.ofPlatform().start(task); /* reported */
                    Executors.newThreadPerTaskExecutor(Thread::new).close(); /* reported */
                    new Worker().start(); /* reported */
                    new Thread() { }.start(); /* reported */
                }

                static final class Worker %s Thread {
                }
            }
            """.formatted("extends");
        assertEquals(ErrorProneRule.marked(source), ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void reportsAPoolBuiltByItsConstructor(@TempDir Path directory) {
        String source = """
            package example;

            import java.util.Timer;
            import java.util.concurrent.ForkJoinPool;
            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.SynchronousQueue;
            import java.util.concurrent.ThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;
            import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

            class Pools {
                void build() {
                    new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new SynchronousQueue<>()).close(); /* reported */
                    new ScheduledThreadPoolExecutor(1).close(); /* reported */
                    new ForkJoinPool(2).close(); /* reported */
                    new Timer().cancel(); /* reported */
                    new ThreadPoolTaskExecutor().hashCode(); /* reported */
                }
            }
            """;
        assertEquals(
            ErrorProneRule.marked(source), ErrorProneRule.findings(directory, CHECK, source, SPRING_EXECUTOR)
        );
    }

    @Test
    void acceptsVirtualThreadsTheCommonPoolAndParallelStreams(@TempDir Path directory) {
        String source = """
            package example;

            import java.util.List;
            import java.util.concurrent.Executors;
            import java.util.concurrent.ForkJoinPool;

            class Threads {
                int run(Runnable task, List<Integer> values) {
                    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                        executor.submit(task);
                    }
                    Thread.ofVirtual().name("worker").start(task);
                    Thread.startVirtualThread(task);
                    Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory()).close();
                    ForkJoinPool.commonPool().execute(task);
                    return values.parallelStream().mapToInt(Integer::intValue).sum();
                }
            }
            """;
        assertEquals(List.of(), ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void acceptsAThreadGivenAStackOfAChosenSize(@TempDir Path directory) {
        String source = """
            package example;

            class Threads {
                private static final int STACK = 512 * 1024;

                void start(Runnable task) {
                    new Thread(null, task, "sized", STACK).start();
                    new Thread(null, task, "sized", 1024L, false).start();
                    new Thread(null, task, "unsized", 0).start();
                }
            }
            """;
        assertEquals(List.of(9L), ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void reportsTestSourcesAlike(@TempDir Path directory) {
        String source = """
            package example;

            import java.util.concurrent.Executors;

            class PoolsTest {
                void race() {
                    Executors.newFixedThreadPool(2).close();
                }
            }
            """;
        assertEquals(List.of(7L), ErrorProneRule.findingsIn(directory, CHECK, "test", source));
    }

    @Test
    void refusesASuppression(@TempDir Path directory) {
        String source = """
            package example;

            import java.util.concurrent.Executors;

            class Pools {
                @SuppressWarnings({"AirnessThreadIsVirtual", "all"})
                void pool() {
                    Executors.newFixedThreadPool(2).close();
                }
            }
            """;
        assertEquals(List.of(8L), ErrorProneRule.findings(directory, CHECK, source));
    }
}
