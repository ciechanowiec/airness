package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The reactor check reports a second application class and says nothing about the first. One module
 * holding one is the ordinary arrangement, so only the build as a whole can tell the two cases apart.
 */
class SpringReactorCheckTest {

    private static final List<Path> ROOTS = List.of(
        Path.of("first/src/main/java"), Path.of("second/src/main/java")
    );
    private static final String FIRST = "first/src/main/java/sample/First.java";
    private static final String SECOND = "second/src/main/java/sample/Second.java";
    private static final String MORE_THAN_ONCE = "declared more than once";

    private static final String APPLICATION = """
        package sample;

        @SpringBootApplication(proxyBeanMethods = false)
        class First {
        }
        """;

    private static final String SECOND_APPLICATION = """
        package sample;

        @SpringBootApplication(proxyBeanMethods = false)
        class Second {
        }
        """;

    private static final String PLAIN = """
        package sample;

        @Service
        class Second {
        }
        """;

    @Test
    void reportsEveryApplicationClassOnceASecondExists() {
        Path root = new GitFixture("reactor-two")
            .write(FIRST, APPLICATION)
            .write(SECOND, SECOND_APPLICATION)
            .root();

        List<String> offences = Verdicts.offences(
            new SpringReactorCheck(root, ROOTS).findings(), MORE_THAN_ONCE
        );

        assertEquals(2, offences.size(), "a reader has to be shown both to decide which one to keep");
        assertTrue(
            offences.getFirst().contains("whichever the search finds first"),
            "the offence says why two of them is not a choice anyone made"
        );
    }

    @Test
    void leavesASingleApplicationClassAlone() {
        Path root = new GitFixture("reactor-one")
            .write(FIRST, APPLICATION)
            .write(SECOND, PLAIN)
            .root();

        assertTrue(
            Verdicts.clean(new SpringReactorCheck(root, ROOTS).findings()),
            "one application class in a build is the arrangement the rule exists to protect"
        );
    }

    @Test
    void countsTheSourcesItReadAcrossEveryModule() {
        Path root = new GitFixture("reactor-scope")
            .write(FIRST, APPLICATION)
            .write(SECOND, PLAIN)
            .root();

        assertEquals(
            2, new SpringReactorCheck(root, ROOTS).scanned(), "both modules are inside the scope given"
        );
    }
}
