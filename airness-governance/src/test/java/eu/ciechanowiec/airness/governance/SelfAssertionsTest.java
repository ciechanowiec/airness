package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Airness holds its own tests to the assertion rules it ships.
 *
 * <p>The {@code test-assertions} goal reads one module at a time, and the goals of
 * {@code airness-maven-plugin} cannot run in the reactor that builds them, so no module of this
 * repository ever reaches that goal. {@link AssertionCheck} is an ordinary class over a root and a set
 * of paths, though, and a test is a caller like any other. {@link SelfProseTest} closes the same gap for
 * the comment rules and explains the shape.
 *
 * <p>What this proves is narrow and worth stating: every test here reaches an assertion, and no
 * assertion here compares one written-out value with another. Whether an assertion would notice the
 * behaviour going missing is the half that binds in prose, not the half a check settles.
 */
class SelfAssertionsTest {

    @Test
    void reachesAnAssertionInEveryTestItShipsThisRuleWith() {
        Path repository = SelfModules.repository();
        List<Path> roots = SelfModules.withProductionJava().stream()
            .flatMap(module -> SelfModules.testRoots(module).stream())
            .toList();
        AssertionCheck check = new AssertionCheck(repository, roots);
        assertTrue(check.scanned() > 0, "a check that read nothing proves nothing about this repository");
        assertEquals(
            List.of(), broken(check.findings()),
            "Airness publishes these rules, so its own tests answer to them first"
        );
    }

    private static List<String> broken(Collection<Findings> findings) {
        return findings.stream().filter(verdict -> !verdict.clean()).map(Findings::report).toList();
    }
}
