package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * View findings follow actual handler declarations rather than annotations elsewhere in their file.
 */
class SpringViewReturnsTest {

    private static final String CONTROLLER = "@Controller";
    private static final String ADVICE = "@ControllerAdvice";
    private static final String PAGE = "@GetMapping(\"/rooms\") String page() { return \"room/missing\"; }";
    private static final String CONTENT = "@GetMapping(\"/data\") @ResponseBody String data() { return \"payload\"; }";
    private static final String FAILURE = "@ExceptionHandler(IllegalArgumentException.class) "
        + "String failure() { return GONE; }";

    @Test
    void checksAnExceptionHandlersStringViewInAdvice() {
        assertEquals(1, SpringViewFixture.missing(ADVICE, FAILURE).size());
    }

    @Test
    void checksAnExceptionHandlersStringViewInAController() {
        assertEquals(1, SpringViewFixture.missing(CONTROLLER, FAILURE).size());
    }

    @Test
    void keepsCheckingAnHtmlHandlerBeforeABodyHandler() {
        assertEquals(1, SpringViewFixture.missing(CONTROLLER, PAGE + CONTENT).size());
    }

    @Test
    void keepsCheckingAnHtmlHandlerAfterABodyHandler() {
        assertEquals(1, SpringViewFixture.missing(CONTROLLER, CONTENT + PAGE).size());
    }

    @Test
    void doesNotTreatAnnotationTextInsideAStringAsAnExemption() {
        assertEquals(1, SpringViewFixture.missing(CONTROLLER, "String note = \"@ResponseBody\";" + PAGE).size());
    }

    @Test
    void doesNotTreatACommentAsAnExemption() {
        assertEquals(1, SpringViewFixture.missing(CONTROLLER, "/* @ResponseBody */" + PAGE).size());
    }

    @Test
    void doesNotTreatATextBlockAsAnExemption() {
        String quoted = "String note = \"\"\"\n@ResponseBody\n\"\"\";";
        assertEquals(1, SpringViewFixture.missing(CONTROLLER, quoted + PAGE).size());
    }

    @Test
    void leavesRestAdviceResponsesAlone() {
        assertEquals(List.of(), SpringViewFixture.missing("@RestControllerAdvice", FAILURE));
    }

    @Test
    void leavesRestControllerResponsesAlone() {
        assertEquals(List.of(), SpringViewFixture.missing("@RestController", PAGE + FAILURE));
    }

    @Test
    void leavesClassLevelBodyResponsesAlone() {
        assertEquals(List.of(), SpringViewFixture.missing("@Controller @ResponseBody", PAGE + FAILURE));
    }

    @Test
    void leavesClassLevelBodyAdviceResponsesAlone() {
        assertEquals(List.of(), SpringViewFixture.missing("@ResponseBody @ControllerAdvice", FAILURE));
    }

    @Test
    void leavesBodyAnnotatedExceptionResponsesAlone() {
        assertEquals(List.of(), SpringViewFixture.missing(ADVICE, "@ResponseBody " + FAILURE));
    }

    @Test
    void leavesModelAttributesAndHelpersAlone() {
        String members = """
            @ModelAttribute String title() { return "title"; }
            String helper() { return "helper"; }
            @GetMapping("/attribute") @ModelAttribute String attribute() { return "attribute"; }
            """;
        assertEquals(List.of(), SpringViewFixture.missing(CONTROLLER, members));
    }

    @Test
    void doesNotMistakeAnAdviceHelperForAnExceptionHandler() {
        assertEquals(List.of(), SpringViewFixture.missing(ADVICE, "String helper() { return GONE; }"));
    }

    @Test
    void recognizesFullyQualifiedSpringAnnotations() {
        String members = """
            @org.springframework.web.bind.annotation.ResponseStatus(
                code = HttpStatus.BAD_REQUEST
            )
            @org.springframework.web.bind.annotation.ExceptionHandler(
                {IllegalArgumentException.class, IllegalStateException.class}
            )
            String failure() { return GONE; }
            @org.springframework.web.bind.annotation.ResponseBody
            @org.springframework.web.bind.annotation.GetMapping("/data")
            String data() { return "payload"; }
            """;
        assertEquals(
            1, SpringViewFixture.missing("@org.springframework.web.bind.annotation.ControllerAdvice", members).size()
        );
    }

    @Test
    void recognizesAFullyQualifiedControllerAndMapping() {
        assertEquals(
            1, SpringViewFixture.missing(
                "@org.springframework.stereotype.Controller",
                "@org.springframework.web.bind.annotation.RequestMapping(\"/rooms\") String page() { return GONE; }"
            ).size()
        );
    }

    @Test
    void recognizesEveryDirectMappingAnnotation() {
        List<String> mappings = List.of("Request", "Get", "Post", "Put", "Delete", "Patch");
        assertTrue(
            mappings.stream().allMatch(
                mapping -> {
                    String member = "@" + mapping + "Mapping String page() { return GONE; }";
                    return SpringViewFixture.missing(CONTROLLER, member).size() == 1;
                }
            ),
            "every built-in mapping marks an HTML handler"
        );
    }
}
