package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Airness holds its own packages to the cycle rule it ships.
 *
 * <p>Read one module at a time rather than over all of them at once, because that is the question the
 * goal asks. A dependency between two modules is already acyclic by construction, since Maven cannot
 * order a reactor that holds a loop, so pooling the source roots would only add edges that no module
 * actually has and could turn two independent one-way dependencies into an invented cycle.
 *
 * <p>{@link SelfProseTest} explains why a goal of {@code airness-maven-plugin} cannot reach this
 * repository and why calling the check directly is what closes that.
 */
class SelfPackageCyclesTest {

    @Test
    void keepsItsOwnPackageDependenciesAcyclic() {
        Path repository = SelfModules.repository();
        List<Path> modules = SelfModules.withProductionJava();
        assertTrue(modules.size() > 1, "reading one module would not be reading this repository");
        List<PackageCycleCheck> checks = modules.stream()
            .map(module -> new PackageCycleCheck(repository, SelfModules.sourceRoots(module)))
            .toList();
        assertEquals(
            List.of(),
            IntStream.range(0, checks.size())
                .filter(index -> checks.get(index).scanned() == 0)
                .mapToObj(index -> modules.get(index).toString())
                .toList(),
            "a module with production Java that read nothing means its source roots moved"
        );
        assertEquals(
            List.of(), checks.stream().flatMap(check -> broken(check.findings()).stream()).toList(),
            "a cycle is broken by moving the shared concept or inverting a direction, never declared acceptable"
        );
    }

    private static List<String> broken(Collection<Findings> findings) {
        return findings.stream().filter(verdict -> !verdict.clean()).map(Findings::report).toList();
    }
}
