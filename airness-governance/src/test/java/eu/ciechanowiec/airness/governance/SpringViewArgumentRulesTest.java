package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Explicit argument lists in fragment view names, read against the fragment declaration they reach.
 */
class SpringViewArgumentRulesTest {

    private static final List<Path> SOURCES = List.of(Path.of("src/main/java"));

    private static final List<Path> RESOURCES = List.of(Path.of("src/main/resources"));

    private static final String CONTROLLER = "src/main/java/sample/Rooms.java";

    private static final String VIEW = "src/main/resources/templates/room/list.html";

    private static final String MISCOUNTED = "Fragment views handed an argument list the declaration does not take";

    private static final String PAGE = """
        <!DOCTYPE html>
        <html lang="en" xmlns:th="http://www.thymeleaf.org">
        <body><div th:fragment="rows(value)">Rows</div></body>
        </html>
        """;

    @Test
    void leavesAnExplicitListMatchingTheDeclarationAlone() {
        assertEquals(List.of(), offences("view-arguments-match", "rows(${value})"));
    }

    @Test
    void reportsAnExplicitEmptyListWhenTheDeclarationTakesOneArgument() {
        assertEquals(1, offences("view-arguments-empty", "rows()").size());
    }

    @Test
    void reportsAnExplicitListCarryingTooManyArguments() {
        assertEquals(1, offences("view-arguments-many", "rows(${one}, ${two})").size());
    }

    @Test
    void leavesABareSelectorAlone() {
        assertEquals(List.of(), offences("view-arguments-bare", "rows"));
    }

    @Test
    void leavesAMissingFragmentToTheUnresolvedViewRule() {
        assertEquals(List.of(), offences("view-arguments-missing", "missing(${value})"));
    }

    @Test
    void namesTheSuppliedAndDeclaredCountsAndTheSource() {
        String offence = offences("view-arguments-named", "rows(${one}, ${two})").getFirst();
        assertTrue(
            offence.contains("Rooms.java")
                && offence.contains("hands 2 argument(s)")
                && offence.contains("declared to take 1"),
            "the finding says where the mismatched counts were written and what they are"
        );
    }

    private static List<String> offences(String name, String selected) {
        Path root = new GitFixture(name)
            .write(CONTROLLER, controller(selected))
            .write(VIEW, PAGE)
            .root();
        return Verdicts.offences(
            new SpringModuleCheck(root, SOURCES, RESOURCES).findings(), MISCOUNTED
        );
    }

    private static String controller(String selected) {
        return """
            package sample;

            @Controller
            class Rooms {

                String rows() {
                    return "room/list :: %s";
                }
            }
            """.formatted(selected);
    }
}
