package eu.ciechanowiec.airness.errorprone;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AirnessContextIsAScopedValueTest {

    private static final String CHECK = "AirnessContextIsAScopedValue";

    @Test
    void reportsAThreadLocalInEachSpelling(@TempDir Path directory) {
        String source = """
            package example;

            import java.util.function.Supplier;

            class Context {
                private final ThreadLocal<String> plain = new ThreadLocal<>();
                private final ThreadLocal<String> inherited = new InheritableThreadLocal<>();
                private final ThreadLocal<String> initial = ThreadLocal.withInitial(() -> "none");
                private final Supplier<ThreadLocal<String>> made = ThreadLocal::new;
                private final ThreadLocal<String> anonymous = new ThreadLocal<>() { };
            }
            """;
        assertEquals(List.of(6L, 7L, 8L, 9L, 10L), ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void reportsAClassExtendingAThreadLocal(@TempDir Path directory) {
        String source = """
            package example;

            class Tenant %s InheritableThreadLocal<String> {
            }
            """.formatted("extends");
        assertEquals(List.of(3L), ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void reportsAReferenceToTheInitialFactory(@TempDir Path directory) {
        String source = """
            package example;

            import java.util.function.Function;
            import java.util.function.Supplier;

            class Context {
                private final Function<Supplier<String>, ThreadLocal<String>> made = ThreadLocal::withInitial;
            }
            """;
        assertEquals(List.of(7L), ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void acceptsAnotherConstructorAndAnotherInitialFactory(@TempDir Path directory) {
        String source = """
            package example;

            import java.util.ArrayList;
            import java.util.List;
            import java.util.function.Function;
            import java.util.function.Supplier;

            class Context {
                private final Supplier<List<String>> list = ArrayList::new;
                private final Function<String, String> initial = Context::withInitial;
                private final String value = withInitial("none");

                static String withInitial(String value) {
                    return value;
                }
            }
            """;
        assertEquals(List.of(), ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void acceptsAScopedValueAndThreadLocalRandom(@TempDir Path directory) {
        String source = """
            package example;

            import java.util.concurrent.ThreadLocalRandom;
            import java.util.function.Supplier;

            class Context {
                private static final ScopedValue<String> TENANT = ScopedValue.newInstance();

                Supplier<ThreadLocalRandom> run(Runnable task) {
                    ScopedValue.where(TENANT, "acme").run(task);
                    return ThreadLocalRandom::current;
                }
            }
            """;
        assertEquals(List.of(), ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void refusesASuppression(@TempDir Path directory) {
        String source = """
            package example;

            class Context {
                @SuppressWarnings("AirnessContextIsAScopedValue")
                private final ThreadLocal<String> plain = new ThreadLocal<>();
            }
            """;
        assertEquals(List.of(5L), ErrorProneRule.findings(directory, CHECK, source));
    }
}
