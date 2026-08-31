package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A view name states a template in a string that nothing compiles, so the module check reads every one
 * it can resolve against the markup the module ships. A name a handler builds is passed over, and so
 * is a string returned by anything that answers with a body rather than with a page.
 */
class SpringViewRulesTest {

    private static final List<Path> SOURCES = List.of(Path.of("src/main/java"));

    private static final List<Path> RESOURCES = List.of(Path.of("src/main/resources"));

    private static final String CONTROLLER = "src/main/java/sample/Rooms.java";

    private static final String LISTED = "src/main/resources/templates/room/list.html";

    private static final String UNRESOLVED = "reach no template the module ships";

    private static final String VIEWED = "@Controller";

    private static final String PAGE = """
        <!DOCTYPE html>
        <html lang="en" xmlns:th="http://www.thymeleaf.org">
        <body><div th:fragment="rows">Rows</div></body>
        </html>
        """;

    private static String controller(String annotation, String body) {
        return """
            package sample;

            %s
            class Rooms {

                private static final String LIST = "room/list";
                private static final String GONE = "room/missing";
                private static final String AWAY = "redirect:/rooms";
                private static final String NOTHING = "";
                private static final String REFUSED = "Give a seat count the center knows";
                private static final String PATH = "/rooms";

                String list() {
                    %s
                }
            }
            """.formatted(annotation, body);
    }

    @Test
    void leavesAViewNameThatReachesATemplateAlone() {
        assertTrue(Verdicts.clean(findings("views-ok", VIEWED, "return LIST;")), "the template is shipped");
    }

    @Test
    void reportsAViewNameThatReachesNoTemplate() {
        assertEquals(1, offences("views-gone", VIEWED, "return GONE;").size(), "nothing answers the name");
    }

    @Test
    void resolvesAViewNameWrittenOutRatherThanNamedByAConstant() {
        assertEquals(
            1, offences("views-literal", VIEWED, "return \"room/absent\";").size(),
            "a literal states a view name as plainly as a constant does"
        );
    }

    @Test
    void passesOverAnAddressAHandlerRedirectsTo() {
        assertTrue(Verdicts.clean(findings("views-redirect", VIEWED, "return AWAY;")), "an address");
    }

    @Test
    void passesOverAStringThatIsNoViewNameAtAll() {
        assertTrue(
            Verdicts.clean(findings("views-prose", VIEWED, "return REFUSED;")),
            "a sentence returned from a controller is not a template name"
        );
    }

    @Test
    void passesOverAnEmptyString() {
        assertTrue(Verdicts.clean(findings("views-empty", VIEWED, "return NOTHING;")), "no name at all");
    }

    @Test
    void passesOverAnAbsolutePath() {
        assertTrue(Verdicts.clean(findings("views-path", VIEWED, "return PATH;")), "an address again");
    }

    @Test
    void passesOverAStringReturnedByARestController() {
        assertTrue(
            Verdicts.clean(findings("views-rest", "@RestController", "return GONE;")),
            "a REST controller answers with a body rather than with a page"
        );
    }

    @Test
    void passesOverAStringReturnedByABodyAnnotatedHandler() {
        String source = controller(VIEWED, "return GONE;");
        String annotated = source.replace("String list()", "@ResponseBody String list()");
        Path root = new GitFixture("views-body").write(CONTROLLER, annotated).write(LISTED, PAGE).root();
        assertTrue(Verdicts.clean(verdicts(root)), "a body-annotated handler answers with content");
    }

    @Test
    void passesOverANameTheHandlerBuilds() {
        assertTrue(
            Verdicts.clean(findings("views-built", VIEWED, "return \"%s/%s\".formatted(PATH, LIST);")),
            "a view chosen at runtime is not a fact about the source"
        );
    }

    @Test
    void resolvesAViewNameThatReachesAFragmentOfATemplate() {
        String body = "return \"room/list :: rows\";";
        assertTrue(Verdicts.clean(findings("views-fragment", VIEWED, body)), "the fragment is declared");
    }

    @Test
    void reportsAViewNameReachingAFragmentNothingDeclares() {
        String body = "return \"room/list :: columns\";";
        assertEquals(1, offences("views-fragment-gone", VIEWED, body).size(), "no such fragment");
    }

    @Test
    void readsTheViewAModelAndViewIsBuiltAround() {
        String body = "return new ModelAndView(GONE);";
        assertEquals(1, offences("views-mav", VIEWED, body).size(), "a model and view names a template");
    }

    @Test
    void readsAModelAndViewOutsideAPlainController() {
        String body = "return new ModelAndView(GONE);";
        assertEquals(
            1, offences("views-mav-advice", "@ControllerAdvice", body).size(), "wherever it is built"
        );
    }

    @Test
    void saysWhichNameReachedNothingAndWhereItWasWritten() {
        String only = offences("views-named", VIEWED, "return GONE;").getFirst();
        assertTrue(
            only.contains("room/missing") && only.contains("Rooms.java"),
            "an offence names the view and the source that returns it"
        );
    }

    private static List<String> offences(String name, String annotation, String body) {
        return Verdicts.offences(findings(name, annotation, body), UNRESOLVED);
    }

    private static List<Findings> findings(String name, String annotation, String body) {
        Path root = new GitFixture(name)
            .write(CONTROLLER, controller(annotation, body))
            .write(LISTED, PAGE)
            .root();
        return verdicts(root);
    }

    private static List<Findings> verdicts(Path root) {
        return List.of(
            new Findings(
                UNRESOLVED, Verdicts.offences(
                    new SpringModuleCheck(root, SOURCES, RESOURCES).findings(), UNRESOLVED
                )
            )
        );
    }
}
