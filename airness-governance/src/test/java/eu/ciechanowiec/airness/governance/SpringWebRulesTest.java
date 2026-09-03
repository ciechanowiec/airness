package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A path variable is bound by name to a placeholder of the mapping, and a name the mapping never
 * declares fails on the first request rather than at startup.
 */
class SpringWebRulesTest {

    private static String controller(String classMapping, String methodMapping, String parameter) {
        return """
            package sample;

            %s
            class Rooms {

                private static final String ROOMS = "/rooms/{reference}";
                private static final String REFERENCE = "reference";

                %s
                public String one(%s) {
                    return "";
                }
            }
            """.formatted(classMapping, methodMapping, parameter);
    }

    @Test
    void acceptsAVariableTheMethodPathDeclares() {
        String source = controller(
            "", "@GetMapping(\"/rooms/{reference}\")", "@PathVariable(\"reference\") String reference"
        );

        assertEquals(List.of(), SpringWebRules.unboundPathVariables(source), "the placeholder is declared");
    }

    @Test
    void acceptsAVariableTheClassPathDeclares() {
        String source = controller(
            "@RequestMapping(ROOMS)", "@GetMapping(\"/seating\")",
            "@PathVariable(name = REFERENCE, required = true) String reference"
        );

        assertEquals(List.of(), SpringWebRules.unboundPathVariables(source), "the class path declares it");
    }

    @Test
    void acceptsAVariableDeclaredUnderABareMethodMapping() {
        String source = controller("@RequestMapping(ROOMS)", "@GetMapping", "@PathVariable(REFERENCE) String r");

        assertEquals(List.of(), SpringWebRules.unboundPathVariables(source), "the class path is the whole path");
    }

    @Test
    void acceptsAVariableOneOfSeveralPathsDeclares() {
        String source = controller(
            "", "@GetMapping(path = {\"/rooms\", \"/rooms/{reference}\"}, produces = \"text/html\")",
            "@PathVariable(\"reference\") String reference"
        );

        assertEquals(List.of(), SpringWebRules.unboundPathVariables(source), "the second path declares it");
    }

    @Test
    void readsAPlaceholderCarryingAPattern() {
        String source = controller(
            "", "@GetMapping(\"/rooms/{reference:[a-f0-9-]+}\")", "@PathVariable(\"reference\") String r"
        );

        assertEquals(List.of(), SpringWebRules.unboundPathVariables(source), "the name precedes the pattern");
    }

    @Test
    void reportsAVariableNoPathDeclares() {
        String source = controller(
            "@RequestMapping(ROOMS)", "@GetMapping(\"/{line}\")", "@PathVariable(\"lines\") String line"
        );

        List<String> offences = SpringWebRules.unboundPathVariables(source);

        assertEquals(1, offences.size(), "neither path declares lines");
        assertTrue(offences.getFirst().startsWith("line 10:"), "the offence points at the parameter");
        assertTrue(offences.getFirst().contains("[line, reference]"), "the offence lists what is declared");
    }

    @Test
    void reportsAVariableUnderAMappingDeclaringNoPath() {
        String source = controller("", "@GetMapping(params = \"draft\")", "@PathVariable(\"reference\") String r");

        List<String> offences = SpringWebRules.unboundPathVariables(source);

        assertEquals(1, offences.size(), "a mapping by parameter alone declares no placeholder");
        assertTrue(offences.getFirst().contains("no placeholder"), "the offence says nothing is declared");
    }

    @Test
    void passesOverAPathTheSourceDoesNotState() {
        String source = controller(
            "@RequestMapping(Paths.ROOMS)", "@GetMapping(\"/seating\")", "@PathVariable(\"reference\") String r"
        );

        assertEquals(List.of(), SpringWebRules.unboundPathVariables(source), "the class path is declared elsewhere");
    }

    @Test
    void passesOverANameTheSourceDoesNotState() {
        String source = controller("", "@GetMapping(\"/rooms\")", "@PathVariable(Names.REFERENCE) String r");

        assertEquals(List.of(), SpringWebRules.unboundPathVariables(source), "the name is declared elsewhere");
    }

    @Test
    void readsOnlyTheParametersOfTheMappedMethod() {
        String source = """
            package sample;

            class Rooms {

                @GetMapping("/rooms/{reference}")
                public String one(@PathVariable("reference") String reference) {
                    return "";
                }

                public String helper(@PathVariable("other") String other) {
                    return "";
                }
            }
            """;

        assertEquals(List.of(), SpringWebRules.unboundPathVariables(source), "an unmapped method is no handler");
    }
}
