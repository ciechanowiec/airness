package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaticMutableStateFactoryCheckstyleTest {

    private static final String RULE = "AirnessStaticStateIsBounded";

    @Test
    void reportsASynchronizedWrapperAroundANewContainerOnce(@TempDir Path directory) {
        String source = """
            final class Cache {
                private static final Map<String, String> ENTRIES = Collections.synchronizedMap(new HashMap<>());
                private static final Set<String> KEYS = Collections.newSetFromMap(new ConcurrentHashMap<>());
            }
            """;

        assertEquals(2, findings(directory, source), "a wrapper and the new inside it are one field, reported once");
    }

    @Test
    void reportsAJdkFactoryOfMutableState(@TempDir Path directory) {
        String source = """
            final class Shared {
                private static final List<String> NAMES = Collections.synchronizedList(names());
                private static final Set<String> KEYS = ConcurrentHashMap.newKeySet();
                private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
            }
            """;

        assertEquals(3, findings(directory, source), "a JDK factory returns mutable state without a visible new");
    }

    @Test
    void reportsAThreadLocalFactoryWhateverItsSupplier(@TempDir Path directory) {
        String source = """
            final class Context {
                private static final ThreadLocal<Map<String, String>> BY_LAMBDA
                    = ThreadLocal.withInitial(() -> new HashMap<>());
                private static final ThreadLocal<Map<String, String>> BY_REFERENCE
                    = ThreadLocal.withInitial(HashMap::new);
            }
            """;

        assertEquals(2, findings(directory, source), "a thread local is reported through its factory");
    }

    @Test
    void reportsAGuavaCollectionFactory(@TempDir Path directory) {
        String source = """
            final class Registry {
                private static final List<String> NAMES = Lists.newArrayList();
                private static final Set<String> KEYS = Sets.newHashSet();
                private static final Map<String, String> PAIRS = Maps.newHashMap();
                private static final Queue<String> PENDING = Queues.newArrayDeque();
            }
            """;

        assertEquals(4, findings(directory, source), "a Guava new* factory returns a mutable collection");
    }

    @Test
    void reportsACacheBuilderChain(@TempDir Path directory) {
        String source = """
            final class Caches {
                private static final Cache<String, String> GUAVA = CacheBuilder.newBuilder().build();
                private static final Cache<String, String> CAFFEINE = Caffeine.newBuilder().maximumSize(10).build();
            }
            """;

        assertEquals(2, findings(directory, source), "a cache built from a builder chain is mutable state");
    }

    @Test
    void reportsAFullyQualifiedFactory(@TempDir Path directory) {
        String source = """
            final class Cache {
                private static final Map<String, String> ENTRIES
                    = java.util.Collections.synchronizedMap(new java.util.HashMap<>());
                private static final Set<String> KEYS = java.util.concurrent.ConcurrentHashMap.newKeySet();
            }
            """;

        assertEquals(2, findings(directory, source), "a factory is read by its simple type name");
    }

    @Test
    void reportsANewNestedInAnotherCall(@TempDir Path directory) {
        String source = """
            final class Fixed {
                private static final List<String> NAMES = Arrays.asList(new String[] {"a"});
                private static final Map<String, String> PAIRS = new HashMap<>() {{ put("a", "b"); }};
            }
            """;

        assertEquals(2, findings(directory, source), "a nested new and a double-brace map are still mutable");
    }

    @Test
    void reportsAStaticFieldDeclaredInsideALambdaOrAnAnonymousClass(@TempDir Path directory) {
        String source = """
            final class Outer {
                private final Runnable task = () -> {
                    Object holder = new Object() {
                        private static final Map<String, String> ENTRIES = new HashMap<>();
                    };
                };
            }
            """;

        assertEquals(1, findings(directory, source), "only the scope inside the field's own initializer is exempt");
    }

    @Test
    void passesOverANewInsideALambdaOrAMethodReference(@TempDir Path directory) {
        String source = """
            final class Factories {
                private static final Supplier<Map<String, String>> MAPS = () -> new HashMap<>();
                private static final Supplier<List<String>> LISTS = ArrayList::new;
                private static final Function<Integer, int[]> ARRAYS = int[]::new;
            }
            """;

        assertEquals(0, findings(directory, source), "a new in a lambda or method reference runs once per call");
    }

    @Test
    void passesOverANewInsideAnAnonymousClassBody(@TempDir Path directory) {
        String source = """
            final class Factories {
                private static final Supplier<List<String>> LISTS = new Supplier<>() {
                    private final List<String> made = new ArrayList<>();
                    public List<String> get() { return new ArrayList<>(made); }
                };
            }
            """;

        assertEquals(0, findings(directory, source), "an anonymous class body belongs to its instance");
    }

    @Test
    void passesOverAnUnmodifiableViewOrCopy(@TempDir Path directory) {
        String source = """
            final class Fixed {
                private static final List<String> VIEW = Collections.unmodifiableList(new ArrayList<>(List.of("a")));
                private static final Map<String, String> SYNCED
                    = Collections.unmodifiableMap(Collections.synchronizedMap(new HashMap<>()));
                private static final List<String> LIST = List.copyOf(new ArrayList<>());
                private static final Set<String> SET = Set.copyOf(new HashSet<>());
                private static final Map<String, String> MAP = Map.copyOf(new TreeMap<>());
                private static final List<String> GUAVA = ImmutableList.copyOf(new ArrayList<>());
                private static final Map<String, String> QUALIFIED
                    = java.util.Collections.unmodifiableMap(new java.util.HashMap<>());
            }
            """;

        assertEquals(0, findings(directory, source), "an unmodifiable view or copy cannot be changed");
    }

    @Test
    void passesOverAFactoryOfImmutableOrNonContainerValues(@TempDir Path directory) {
        String source = """
            final class Fixed {
                private static final List<String> EMPTY = Collections.emptyList();
                private static final Callable<Object> TASK = Executors.callable(() -> { });
                private static final Set<String> SINGLE = Collections.singleton("a");
            }
            """;

        assertEquals(0, findings(directory, source), "a factory that returns no mutable state is not reported");
    }

    @Test
    void passesOverAnInstanceFieldBuiltByAFactory(@TempDir Path directory) {
        String source = """
            final class Holder {
                private final Map<String, String> entries = Collections.synchronizedMap(new HashMap<>());
                private final Set<String> keys = ConcurrentHashMap.newKeySet();
            }
            """;

        assertEquals(0, findings(directory, source), "an instance field lives only as long as its instance");
    }

    @Test
    void acceptsAJustifiedSuppressionOfAFactory(@TempDir Path directory) {
        String source = """
            final class Cache {
                @SuppressWarnings("AirnessStaticStateIsBounded")
                private static final Cache<Kind, String> BY_KIND = Caffeine.newBuilder().maximumSize(8).build();
            }
            """;

        assertEquals(0, findings(directory, source), "a bounded cache is answered by a suppression");
    }

    private static int findings(Path directory, String source) {
        return CheckstyleRule.findings(directory, source, RULE);
    }
}
