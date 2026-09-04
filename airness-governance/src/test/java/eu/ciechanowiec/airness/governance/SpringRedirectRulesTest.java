package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpringRedirectRulesTest {

    private static final String SENT = "@RequestParam(name = \"to\", required = false) String to";

    @Test
    void reportsATargetConcatenatedFromARequestParameter() {
        List<String> offences = SpringRedirectRules.requestBuiltRedirects(handler(SENT, "\"redirect:\" + to"));

        assertEquals(1, offences.size(), "the caller chose the whole address");
    }

    @Test
    void reportsATargetBuiltWithFormatted() {
        List<String> offences = SpringRedirectRules.requestBuiltRedirects(
            handler(SENT, "\"redirect:%s\".formatted(to)")
        );

        assertEquals(1, offences.size(), "a substitution puts the value where a concatenation would");
    }

    @Test
    void reportsATargetBuiltWithStringFormat() {
        List<String> offences = SpringRedirectRules.requestBuiltRedirects(
            handler(SENT, "String.format(\"redirect:%s\", to)")
        );

        assertEquals(1, offences.size(), "the qualifier in front of format decides nothing");
    }

    @Test
    void reportsATargetBuiltWithConcat() {
        List<String> offences = SpringRedirectRules.requestBuiltRedirects(
            handler(SENT, "\"redirect:\".concat(to)")
        );

        assertEquals(1, offences.size(), "concat puts its argument straight into the target");
    }

    @Test
    void reportsATargetBuiltFromAHeader() {
        List<String> offences = SpringRedirectRules.requestBuiltRedirects(
            handler("@RequestHeader(name = \"Referer\", required = false) String to", "\"redirect:\" + to")
        );

        assertEquals(1, offences.size(), "a header is a value the caller sends");
    }

    @Test
    void reportsATargetBuiltFromACookie() {
        List<String> offences = SpringRedirectRules.requestBuiltRedirects(
            handler("@CookieValue(name = \"last\", required = false) String to", "\"redirect:\" + to")
        );

        assertEquals(1, offences.size(), "a cookie is a value the caller sends");
    }

    @Test
    void reportsARedirectViewConstructedFromARequestValue() {
        List<String> offences = SpringRedirectRules.requestBuiltRedirects(handler(SENT, "new RedirectView(to)"));

        assertEquals(1, offences.size(), "a redirect view names an address the way a prefix does");
    }

    @Test
    void reportsAForwardTheSameWayAsARedirect() {
        List<String> offences = SpringRedirectRules.requestBuiltRedirects(handler(SENT, "\"forward:\" + to"));

        assertEquals(1, offences.size(), "a forward dispatches on a target as a redirect does");
    }

    @Test
    void namesTheParameterAndTheRepairInTheOffence() {
        List<String> offences = SpringRedirectRules.requestBuiltRedirects(handler(SENT, "\"redirect:\" + to"));

        assertTrue(
            offences.getFirst().contains("built from to") && offences.getFirst().contains("resolve the value"),
            "the offence names the value and what to do instead"
        );
    }

    @Test
    void reportsTheTargetUnderTheLineItIsWrittenOn() {
        List<String> offences = SpringRedirectRules.requestBuiltRedirects(handler(SENT, "\"redirect:\" + to"));

        assertTrue(offences.getFirst().startsWith("line 10:"), "the offence points at the target");
    }

    @Test
    void readsAMappingWrittenWithNoArgumentsOfItsOwn() {
        String source = """
            package sample;

            @Controller
            class Pages {

                @GetMapping
                public String open(@RequestParam(name = "to", required = false) String to) {
                    return "redirect:" + to;
                }
            }
            """;

        assertEquals(
            1, SpringRedirectRules.requestBuiltRedirects(source).size(),
            "an annotation carrying no arguments maps a method as one carrying them does"
        );
    }

    @Test
    void readsATargetHoweverItIsSpaced() {
        List<String> offences = SpringRedirectRules.requestBuiltRedirects(
            handler(SENT, "\"redirect:\"\n            + to")
        );

        assertEquals(1, offences.size(), "the whitespace an author writes decides nothing");
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
