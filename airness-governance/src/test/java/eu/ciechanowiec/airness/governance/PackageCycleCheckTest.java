package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The package-cycle check builds the graph the sources declare, ignores an import that leaves the
 * scanned set, and reports one rendered loop per group of packages that lie on one.
 */
class PackageCycleCheckTest {

    private static final List<Path> MAIN = List.of(Path.of("src/main/java"));
    private static final String ALPHA = "src/main/java/sample/alpha/Alpha.java";
    private static final String BETA = "src/main/java/sample/beta/Beta.java";
    private static final String LOOP = "in a loop";

    private static final String ALPHA_ON_BETA = """
        package sample.alpha;

        import sample.beta.Beta;

        class Alpha {

            Beta beta() {
                return new Beta();
            }
        }
        """;

    private static final String BETA_ON_ALPHA = """
        package sample.beta;

        import sample.alpha.Alpha;

        class Beta {

            Alpha alpha() {
                return new Alpha();
            }
        }
        """;

    private static final String BETA_ALONE = """
        package sample.beta;

        import java.util.List;

        class Beta {

            List<String> names() {
                return List.of();
            }
        }
        """;

    @Test
    void passesOverPackagesThatDependInOneDirection() {
        Path root = new GitFixture("cycles-clean")
            .write(ALPHA, ALPHA_ON_BETA)
            .write(BETA, BETA_ALONE)
            .root();
        assertTrue(
            Verdicts.clean(new PackageCycleCheck(root, MAIN).findings()),
            "one direction of dependency closes no loop"
        );
    }

    @Test
    void reportsTheLoopAsThePathBackToWhereItStarted() {
        Path root = new GitFixture("cycles-broken")
            .write(ALPHA, ALPHA_ON_BETA)
            .write(BETA, BETA_ON_ALPHA)
            .root();
        List<String> offences = Verdicts.offences(new PackageCycleCheck(root, MAIN).findings(), LOOP);
        assertEquals(1, offences.size(), "one loop joins the two packages, so it is reported once");
        assertEquals(
            "sample.alpha -> sample.beta -> sample.alpha",
            offences.getFirst(),
            "and it is rendered as the path a reader has to break"
        );
    }

    @Test
    void ignoresAnImportThatLeavesTheScannedSet() {
        Path root = new GitFixture("cycles-external").write(BETA, BETA_ALONE).root();
        PackageCycleCheck check = new PackageCycleCheck(root, MAIN);
        assertEquals(1, check.packages(), "only the package the sources declare is a node");
        assertTrue(Verdicts.clean(check.findings()), "a dependency on a library is not one inside the design");
    }

    @Test
    void countsTheSourcesItRead() {
        Path root = new GitFixture("cycles-scope").write(ALPHA, ALPHA_ON_BETA).write(BETA, BETA_ALONE).root();
        assertEquals(2, new PackageCycleCheck(root, MAIN).scanned(), "both sources lie under the root given");
    }

    @Test
    void reportsAnEmptyScopeRatherThanACleanGraph() {
        Path root = new GitFixture("cycles-empty").write(ALPHA, ALPHA_ON_BETA).write(BETA, BETA_ON_ALPHA).root();
        PackageCycleCheck check = new PackageCycleCheck(root, List.of(Path.of("src/main/kotlin")));
        assertEquals(0, check.scanned(), "a root that names nothing read nothing");
        assertTrue(
            Verdicts.clean(check.findings()),
            "and so reports the same verdict as an acyclic graph, which is why the caller refuses a zero scope"
        );
    }
}
