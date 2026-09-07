package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * View findings follow actual handler declarations rather than annotations elsewhere in their file.
 */
class SpringViewDeclarationsTest {

    private static final String UNRESOLVED = "View names that reach no template the module ships";
    private static final String MISCOUNTED = "Fragment views handed an argument list the declaration does not take";
    private static final String CONTROLLER = "@Controller";
    private static final String ADVICE = "@ControllerAdvice";
    private static final String PAGE = "@GetMapping(\"/rooms\") String page() { return \"room/missing\"; }";
    private static final String CONTENT = "@GetMapping(\"/data\") @ResponseBody String data() { return \"payload\"; }";
    private static final String FAILURE = "@ExceptionHandler(IllegalArgumentException.class) "
        + "String failure() { return GONE; }";

    @Test
    void keepsNestedTypeAnnotationsSeparateFromTheirEnclosingType() {
        String members = """
            @RestController static class Content {
                @GetMapping("/data") String data() { return "payload"; }
            }
            """;
        assertEquals(1, SpringViewFixture.missing(CONTROLLER, members + PAGE).size());
    }

    @Test
    void checksANestedControllerWithoutInheritingItsEnclosingTypesBodyAnnotation() {
        String members = "@Controller static class Pages { " + PAGE + " }";
        assertEquals(1, SpringViewFixture.missing("@RestController", members).size());
    }

    @Test
    void leavesReturnsInLambdasAndLocalOrAnonymousClassesAlone() {
        String members = """
            @GetMapping("/rooms") String page() {
                Supplier<String> lambda = () -> { return "lambda"; };
                Object anonymous = new Object() {
                    String text() { return "anonymous"; }
                };
                class Local {
                    String text() { return "local"; }
                }
                return "room/list";
            }
            """;
        assertEquals(List.of(), SpringViewFixture.missing(CONTROLLER, members));
    }

    @Test
    void checksReturnsInNestedControlFlowAndSwitchArms() {
        String members = """
            @GetMapping("/rooms") String page() {
                if (condition) { return "room/list"; }
                switch (number) {
                    case 1 -> { return "room/missing"; }
                    default -> { return "room/list"; }
                }
            }
            """;
        assertEquals(1, SpringViewFixture.missing(CONTROLLER, members).size());
    }

    @Test
    void checksExplicitModelsEvenInBodyAnnotatedClasses() {
        assertEquals(
            1, SpringViewFixture.missing("@RestController", "Object page() { return new ModelAndView(GONE); }").size()
        );
    }

    @Test
    void checksFullyQualifiedModelAndViewConstruction() {
        String member = "Object page() { return new org.springframework.web.servlet.ModelAndView(GONE); }";
        assertEquals(1, SpringViewFixture.missing(ADVICE, member).size());
    }

    @Test
    void leavesAModelAndViewWithADynamicallyBuiltNameAlone() {
        String member = "Object page(String name) { return new ModelAndView(GONE + name); }";
        assertEquals(List.of(), SpringViewFixture.missing(ADVICE, member));
    }

    @Test
    void leavesAModelAndViewWithNoExplicitNameAlone() {
        assertEquals(List.of(), SpringViewFixture.missing(ADVICE, "Object page() { return new ModelAndView(); }"));
    }

    @Test
    void leavesAViewReturnedByAnUnrelatedAnnotationAlone() {
        assertEquals(List.of(), SpringViewFixture.missing("@example.Controller", PAGE));
    }

    @Test
    void checksFragmentArgumentCountsInAdviceAndMixedControllers() {
        String fragment = "return \"room/list :: rows('one', 'two')\";";
        List<Findings> advice = SpringViewFixture.findings(ADVICE, FAILURE.replace("return GONE;", fragment));
        assertEquals(1, Verdicts.offences(advice, MISCOUNTED).size());
        String member = CONTENT + PAGE.replace("return \"room/missing\";", fragment);
        assertEquals(1, Verdicts.offences(SpringViewFixture.findings(CONTROLLER, member), MISCOUNTED).size());
    }

    @Test
    void leavesMissingFragmentsToTheUnresolvedViewFinding() {
        String member = FAILURE.replace("GONE", "\"room/list :: missing()\"");
        List<Findings> findings = SpringViewFixture.findings(ADVICE, member);
        assertEquals(1, Verdicts.offences(findings, UNRESOLVED).size());
        assertEquals(List.of(), Verdicts.offences(findings, MISCOUNTED));
    }

    @Test
    void reportsTheOriginalReturnLineAfterMultilineAnnotations() {
        String members = """
            @ExceptionHandler(
                {IllegalArgumentException.class, IllegalStateException.class}
            )
            @ResponseStatus(code = HttpStatus.BAD_REQUEST)
            String failure() {
                return GONE;
            }
            """;
        String offence = SpringViewFixture.missing(ADVICE, members).getFirst();
        assertTrue(offence.contains("Rooms.java: line 10: the view name room/missing"), offence);
    }
}
