package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringStartupRunnerCheckstyleTest {

    private static final String RULE = "AirnessSpringRunnerStatesItsOrder";
    private static final String MARKER = "CommandLineRunner";

    @Test
    void reportsAnApplicationRunnerThatStatesNoOrder(@TempDir Path directory) {
        String source = """
            class Bootstrap implements ApplicationRunner {
                public void run(ApplicationArguments arguments) {}
            }
            """;

        assertEquals(1, findings(directory, source), "an unordered runner runs wherever registration put it");
    }

    @Test
    void reportsACommandLineRunnerThatStatesNoOrder(@TempDir Path directory) {
        String source = """
            class Bootstrap implements CommandLineRunner {
                public void run(String... arguments) {}
            }
            """;

        assertEquals(1, findings(directory, source), "a command line runner is sorted the same way");
    }

    @Test
    void readsAQualifiedAndAGenericImplementsClause(@TempDir Path directory) {
        String source = """
            class Bootstrap implements Converter<String, String>, org.springframework.boot.ApplicationRunner {
                public String convert(String source) { return source; }
                public void run(ApplicationArguments arguments) {}
            }
            """;

        assertEquals(1, findings(directory, source), "the runner interface is read however it is spelled");
    }

    @Test
    void acceptsARunnerAnnotatedWithItsOrder(@TempDir Path directory) {
        String source = """
            @Order(0)
            class Seeding implements ApplicationRunner {
                public void run(ApplicationArguments arguments) {}
            }
            @org.springframework.core.annotation.Order(1)
            class Admitting implements CommandLineRunner {
                public void run(String... arguments) {}
            }
            """;

        assertEquals(0, findings(directory, source), "an annotated order is an order however it is spelled");
    }

    @Test
    void acceptsARunnerThatImplementsOrdered(@TempDir Path directory) {
        String source = """
            class Seeding implements CommandLineRunner, Ordered {
                public void run(String... arguments) {}
                public int getOrder() { return 0; }
            }
            class Admitting implements ApplicationRunner, PriorityOrdered {
                public void run(ApplicationArguments arguments) {}
                public int getOrder() { return 1; }
            }
            """;

        assertEquals(0, findings(directory, source), "an implemented order is stated as well as an annotated one");
    }

    @Test
    void passesOverAnAbstractBase(@TempDir Path directory) {
        String source = """
            abstract class Base implements ApplicationRunner {
                public void run(ApplicationArguments arguments) {}
            }
            """;

        assertEquals(0, findings(directory, source), "the container registers the concrete subclass, not the base");
    }

    @Test
    void passesOverAClassThatIsNoRunner(@TempDir Path directory) {
        String source = """
            class Plain implements Comparable<Plain> {
                public void run(ApplicationArguments arguments) {}
                public int compareTo(Plain other) { return 0; }
            }
            """;

        assertEquals(0, findings(directory, source), "a run method alone makes no runner");
    }

    private static int findings(Path directory, String source) {
        return CheckstyleRule.findings(directory, source, RULE, MARKER);
    }
}
