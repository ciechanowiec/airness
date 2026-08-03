package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Javadoc-link check reports a neighbouring type named in prose and accepts the same name once it is
 * linked, so what decides the verdict is resolvability rather than the spelling of the name.
 */
class JavadocLinkCheckTest {

    private static final List<Path> MAIN = List.of(Path.of("src/main/java"));
    private static final String NEIGHBOUR = "src/main/java/sample/Neighbour.java";
    private static final String SUBJECT = "src/main/java/sample/Subject.java";

    private static final String NEIGHBOUR_SOURCE = """
        package sample;

        class Neighbour {
        }
        """;

    private static final String NAMED_IN_PROSE = """
        package sample;

        /**
         * Hands its work to Neighbour.
         */
        class Subject {
        }
        """;

    private static final String LINKED = """
        package sample;

        /**
         * Hands its work to {@link Neighbour}.
         */
        class Subject {
        }
        """;

    @Test
    void reportsANeighbouringTypeNamedInProse() {
        Path root = new GitFixture("javadoc-link-broken")
            .write(NEIGHBOUR, NEIGHBOUR_SOURCE)
            .write(SUBJECT, NAMED_IN_PROSE)
            .root();
        List<String> offences = Verdicts.offences(new JavadocLinkCheck(root, MAIN).findings(), "Javadoc names");
        assertEquals(1, offences.size(), "one file names one resolvable type unlinked");
        assertTrue(offences.getFirst().contains("Neighbour"), "and the offence names it: " + offences);
    }

    @Test
    void acceptsTheSameNameOnceItIsLinked() {
        Path root = new GitFixture("javadoc-link-clean")
            .write(NEIGHBOUR, NEIGHBOUR_SOURCE)
            .write(SUBJECT, LINKED)
            .root();
        assertTrue(
            Verdicts.clean(new JavadocLinkCheck(root, MAIN).findings()), "a linked name is what the rule asks for"
        );
    }

    @Test
    void reportsAnEmptyScopeRatherThanACleanTree() {
        Path root = new GitFixture("javadoc-link-empty")
            .write(NEIGHBOUR, NEIGHBOUR_SOURCE)
            .write(SUBJECT, NAMED_IN_PROSE)
            .root();
        JavadocLinkCheck check = new JavadocLinkCheck(root, List.of(Path.of("src/main/kotlin")));
        assertEquals(0, check.scanned(), "a root that names nothing read nothing");
        assertTrue(
            Verdicts.clean(check.findings()), "so the caller refuses a zero scope rather than trusting the verdict"
        );
    }
}
