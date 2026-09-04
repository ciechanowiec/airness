package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What the rule deliberately passes over. A target this application chose, a value bounded by the path
 * the mapping declared, a value that cannot hold an address at all, and a value the handler worked out
 * before redirecting to it are each correct, and each has to stay silent for the rule to be worth having.
 */
class SpringRedirectScopeTest {

    private static final String SENT = "@RequestParam(name = \"to\", required = false) String to";

    @Test
    void acceptsATargetNamedByAConstant() {
        List<String> offences = SpringRedirectRules.requestBuiltRedirects(handler(SENT, "AGAIN"));

        assertEquals(List.of(), offences, "a constant is a target this application chose");
    }

    @Test
    void acceptsAFormattedTargetOverConstantsAndAPathVariable() {
        List<String> offences = SpringRedirectRules.requestBuiltRedirects(
            handler(
                "@PathVariable(name = \"reference\") UUID reference", "\"redirect:%s/%s\".formatted(ROOMS, reference)"
            )
        );

        assertEquals(List.of(), offences, "a segment beneath this application is a different question");
    }

    @Test
    void acceptsATargetBuiltFromAPathVariable() {
        List<String> offences = SpringRedirectRules.requestBuiltRedirects(
            handler("@PathVariable(name = \"code\") String code", "\"redirect:\" + code")
        );

        assertEquals(List.of(), offences, "a path variable holds a segment of a path this application declared");
    }

    @Test
    void acceptsAParameterThatCannotHoldAnAddress() {
        List<String> offences = SpringRedirectRules.requestBuiltRedirects(
            handler("@RequestParam(name = \"page\") int page", "\"redirect:\" + page")
        );

        assertEquals(List.of(), offences, "a converter refuses anything that is not a number first");
    }

    @Test
    void acceptsAPrefixCarryingAPathOfItsOwn() {
        List<String> offences = SpringRedirectRules.requestBuiltRedirects(
            handler(SENT, "\"redirect:/pages/%s\".formatted(to)")
        );

        assertEquals(List.of(), offences, "the value lands in a segment rather than being the whole address");
    }

    @Test
    void acceptsATargetTheHandlerResolvedFirst() {
        String source = """
            package sample;

            @Controller
            class Pages {

                @GetMapping("/a")
                public String open(@RequestParam(name = "to", required = false) String to) {
                    String target = known(to);
                    return "redirect:" + target;
                }
            }
            """;

        assertEquals(
            List.of(), SpringRedirectRules.requestBuiltRedirects(source),
            "a value that went through a call of the project's own is a value it worked out"
        );
    }

    @Test
    void readsOnlyTheParametersOfTheHandlerTheTargetIsIn() {
        String source = """
            package sample;

            @Controller
            class Pages {

                @GetMapping("/a")
                public String first(@RequestParam(name = "to", required = false) String to) {
                    return "redirect:/pages";
                }

                @GetMapping("/b")
                public String second(@PathVariable(name = "code") String code) {
                    return "redirect:" + code;
                }
            }
            """;

        assertEquals(
            List.of(), SpringRedirectRules.requestBuiltRedirects(source),
            "a parameter of another handler names nothing here"
        );
    }

    @Test
    void readsNoTargetOutOfAComment() {
        String source = """
            package sample;

            @Controller
            class Pages {

                @GetMapping("/a")
                public String open(@RequestParam(name = "to", required = false) String to) {
                    // return "redirect:" + to;
                    return "redirect:/pages";
                }
            }
            """;

        assertEquals(
            List.of(), SpringRedirectRules.requestBuiltRedirects(source), "an explanation dispatches nothing"
        );
    }

    @Test
    void passesOverAMappingWrittenOnTheClass() {
        String source = """
            package sample;

            @Controller
            @RequestMapping("/pages")
            class Pages {

                private static final Control NAMED = new Control("pages.control.to", "to", "text", "off");

                @GetMapping("/a")
                public String open(@PathVariable(name = "code") String code) {
                    return "redirect:/pages";
                }
            }
            """;

        assertEquals(
            List.of(), SpringRedirectRules.requestBuiltRedirects(source),
            "a class mapping states an address and answers no request of its own"
        );
    }

    @Test
    void passesOverAMappedMethodDeclaringNoBody() {
        String source = """
            package sample;

            interface Pages {

                @GetMapping("/a")
                String open(@RequestParam(name = "to", required = false) String to);

                @GetMapping("/b")
                default String other() {
                    return "redirect:" + PAGES;
                }
            }
            """;

        assertEquals(
            List.of(), SpringRedirectRules.requestBuiltRedirects(source),
            "a declaration with no body dispatches nothing, and the brace after it belongs to another method"
        );
    }

    @Test
    void readsNoFieldNamedAfterAParameterAsThatParameter() {
        List<String> offences = SpringRedirectRules.requestBuiltRedirects(
            handler(SENT, "\"redirect:\" + holder.to")
        );

        assertEquals(List.of(), offences, "what a qualifier reaches is a value of something else");
    }

    // One handler, taking whatever parameter a test gives it and returning whatever target it names.
    private static String handler(String parameter, String target) {
        return """
            package sample;

            @Controller
            class Pages {

                private static final String AGAIN = "redirect:" + PROFILE;

                @GetMapping("/a")
                public String open(%s) {
                    return %s;
                }
            }
            """.formatted(parameter, target);
    }
}
