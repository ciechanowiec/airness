package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A view chosen at runtime is not a fact about the source, so the view rules cannot read it. It is named
 * as statically unassessed rather than passed over in silence, and it fails nothing.
 */
class SpringViewUnassessedTest {

    private static final List<Path> SOURCES = List.of(Path.of("src/main/java"));

    private static final List<Path> RESOURCES = List.of(Path.of("src/main/resources"));

    private static final String CONTROLLER = "src/main/java/sample/Rooms.java";

    private static final String LISTED = "src/main/resources/templates/room/list.html";

    private static final String PAGE = """
        <!DOCTYPE html>
        <html lang="en" xmlns:th="http://www.thymeleaf.org">
        <body><p>Rooms</p></body>
        </html>
        """;

    private static String controller(String body) {
        return """
            package sample;

            @Controller
            class Rooms {

                private static final String LIST = "room/list";

                @GetMapping("/rooms")
                String list() {
                    %s
                }
            }
            """.formatted(body);
    }

    private static SpringModuleCheck check(String name, String body) {
        Path root = new GitFixture(name).write(CONTROLLER, controller(body)).write(LISTED, PAGE).root();
        return new SpringModuleCheck(root, SOURCES, RESOURCES);
    }

    @Test
    void namesAViewChosenAtRuntimeAsUnassessedWithoutFailingIt() {
        SpringModuleCheck check = check("unassessed-chosen", "return java.util.Optional.of(LIST).orElse(LIST);");
        assertTrue(
            Verdicts.offences(check.findings(), "reach no template the module ships").isEmpty()
                && check.unassessedViews().size() == 1
                && check.unassessedViews().getFirst().startsWith(CONTROLLER + ": line 10: "),
            "a view selected at runtime is reported as unassessed rather than as checked or as broken"
        );
    }

    @Test
    void namesAViewNamedByAConstantDeclaredElsewhereAsUnassessed() {
        assertEquals(
            1, check("unassessed-foreign", "return ELSEWHERE;").unassessedViews().size(),
            "a constant this source does not declare says nothing a template rule can read"
        );
    }

    @Test
    void namesNothingWhereEveryViewIsWritten() {
        assertTrue(
            check("unassessed-written", "return LIST;").unassessedViews().isEmpty(),
            "a view written in the source is the view rules' to read"
        );
    }
}
