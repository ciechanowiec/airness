package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Airness holds its own sources to the comment rules it ships, and to the Javadoc link rule beside them.
 *
 * <p>The goals that enforce these on a consumer cannot reach this repository. A Maven plugin is resolved
 * from a repository rather than from the reactor that builds it, so a module built before
 * {@code airness-maven-plugin} can never run a goal of it, and the only module built afterwards carries
 * no Java. Calling the rules directly is what closes that, since they are ordinary classes that take a
 * path and read text, and a test is a caller like any other.
 *
 * <p>{@link ManagedVersionsPolicyTest} reads the real poms from a test the same way and for the same
 * reason. Left to the goals alone, a rule this project publishes would be one it had never once run over
 * itself, which is the reading of a clean report that nobody can tell from a clean tree.
 *
 * <p>The consumer fixtures are deliberately outside the scan. They live in {@code airness-it}, which is
 * outside the reactor, and every one of them breaks a rule on purpose.
 */
class SelfProseTest {

    private static final List<String> MODULES = List.of(
        "airness-annotations", "airness-governance", "airness-maven-plugin"
    );
    private static final List<String> SOURCES = List.of("src/main/java", "src/test/java");

    @Test
    void keepsItsOwnCommentProseWithinTheRulesItShips() {
        CommentProseCheck check = new CommentProseCheck(repository(), roots());
        assertTrue(check.scanned() > 0, "a check that read nothing proves nothing about this repository");
        assertEquals(
            List.of(), broken(check.findings()),
            "Airness publishes these two rules, so its own comments answer to them first"
        );
    }

    @Test
    void linksEveryTypeItsOwnJavadocNames() {
        JavadocLinkCheck check = new JavadocLinkCheck(repository(), roots());
        assertTrue(check.scanned() > 0, "a check that read nothing proves nothing about this repository");
        assertEquals(
            List.of(), broken(check.findings()),
            "a resolvable name left as prose is the defect nothing else in this build would catch"
        );
    }

    private static List<String> broken(List<Findings> findings) {
        return findings.stream().filter(verdict -> !verdict.clean()).map(Findings::report).toList();
    }

    private static List<Path> roots() {
        Path repository = repository();
        return MODULES.stream()
            .flatMap(module -> SOURCES.stream().map(source -> repository.resolve(module).resolve(source)))
            .filter(Files::isDirectory)
            .toList();
    }

    private static Path repository() {
        Path current = Path.of("").toAbsolutePath();
        return Stream.of(current, current.getParent())
            .filter(path -> Files.exists(path.resolve("airness-parent/pom.xml")))
            .findFirst()
            .orElseThrow();
    }
}
