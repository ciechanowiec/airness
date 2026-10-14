package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaticMutableStateCheckstyleTest {

    private static final String RULE = "AirnessStaticStateIsBounded";

    @Test
    void reportsAStaticMap(@TempDir Path directory) {
        String source = """
            final class Cache {
                private static final Map<String, String> ENTRIES = new ConcurrentHashMap<>();
                private static final Map<String, String> SORTED = new java.util.TreeMap<>();
            }
            """;

        assertEquals(2, findings(directory, source), "a map is mutable however its type is spelled");
    }

    @Test
    void reportsAStaticList(@TempDir Path directory) {
        String source = """
            final class Registry {
                private static final List<String> NAMES = new ArrayList<>();
                static List<String> linked = new LinkedList<>();
            }
            """;

        assertEquals(2, findings(directory, source), "a list is mutable whether or not the field is final");
    }

    @Test
    void reportsAStaticSet(@TempDir Path directory) {
        String source = """
            final class Seen {
                private static final Set<String> KEYS = new HashSet<>();
            }
            """;

        assertEquals(1, findings(directory, source), "a set is mutable");
    }

    @Test
    void reportsAStaticQueueAndDeque(@TempDir Path directory) {
        String source = """
            final class Work {
                private static final Queue<Runnable> PENDING = new LinkedBlockingQueue<>();
                private static final Deque<Runnable> STACK = new ArrayDeque<>();
            }
            """;

        assertEquals(2, findings(directory, source), "a queue and a deque are mutable");
    }

    @Test
    void reportsAStaticAtomic(@TempDir Path directory) {
        String source = """
            final class Counter {
                private static final AtomicInteger COUNT = new AtomicInteger();
                private static final AtomicReference<String> LAST = new AtomicReference<>();
            }
            """;

        assertEquals(2, findings(directory, source), "an atomic holder is mutable state");
    }

    @Test
    void reportsAStaticThreadLocal(@TempDir Path directory) {
        String source = """
            final class Context {
                private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
            }
            """;

        assertEquals(1, findings(directory, source), "a thread local holds a value per thread for its whole life");
    }

    @Test
    void reportsAStaticStringBuilder(@TempDir Path directory) {
        String source = """
            final class Log {
                private static final StringBuilder BUFFER = new StringBuilder();
            }
            """;

        assertEquals(1, findings(directory, source), "a string builder is mutable");
    }

    @Test
    void reportsAStaticArray(@TempDir Path directory) {
        String source = """
            final class Table {
                private static final int[] SLOTS = new int[4];
                private static final String[] NAMES = new String[] {"a", "b"};
            }
            """;

        assertEquals(2, findings(directory, source), "an array is mutable whether sized or initialised");
    }

    @Test
    void passesOverImmutableFactories(@TempDir Path directory) {
        String source = """
            final class Fixed {
                private static final List<String> NAMES = List.of("a");
                private static final Set<String> KEYS = Set.of("b");
                private static final Map<String, String> PAIRS = Map.of("c", "d");
                private static final List<String> COPY = List.copyOf(NAMES);
            }
            """;

        assertEquals(0, findings(directory, source), "an immutable factory holds no mutable state");
    }

    @Test
    void passesOverConstants(@TempDir Path directory) {
        String source = """
            final class Constants {
                private static final String NAME = "airness";
                private static final int LIMIT = 7;
                private static final Duration TIMEOUT = Duration.ofSeconds(1);
                private static final Path ROOT = new File("x").toPath();
                private static final ObjectMapper MAPPER = new ObjectMapper();
            }
            """;

        assertEquals(0, findings(directory, source), "a constant or a non-container value is not reported");
    }

    @Test
    void passesOverAnInstanceField(@TempDir Path directory) {
        String source = """
            final class Holder {
                private final Map<String, String> entries = new HashMap<>();
                private final StringBuilder buffer = new StringBuilder();
                private final int[] slots = new int[4];
            }
            """;

        assertEquals(0, findings(directory, source), "an instance field lives only as long as its instance");
    }

    @Test
    void passesOverALocalVariableInAStaticMethod(@TempDir Path directory) {
        String source = """
            final class Builder {
                static String join() {
                    List<String> parts = new ArrayList<>();
                    return String.join(",", parts);
                }
            }
            """;

        assertEquals(0, findings(directory, source), "a local variable is scoped to its call");
    }

    @Test
    void acceptsAJustifiedSuppression(@TempDir Path directory) {
        String source = """
            final class Cache {
                @SuppressWarnings("AirnessStaticStateIsBounded")
                private static final Map<Kind, String> BY_KIND = new EnumMap<>(Kind.class);
            }
            """;

        assertEquals(0, findings(directory, source), "a deliberate cache is answered by a suppression");
    }

    private static int findings(Path directory, String source) {
        return CheckstyleRule.findings(directory, source, RULE);
    }
}
