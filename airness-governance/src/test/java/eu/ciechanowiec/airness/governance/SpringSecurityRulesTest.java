package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A parameter reference is the only part of an authorization expression that reaches back into the
 * call it guards. Airness retains Java parameter names, but a refactor can rename one without changing
 * the expression string. A naming annotation makes that security binding explicit and stable.
 */
class SpringSecurityRulesTest {

    private static String guarded(String annotation, String parameters) {
        return """
            package com.example;

            class Rooms {

                %s
                public Room rename(%s) {
                    return null;
                }
            }
            """.formatted(annotation, parameters);
    }

    @Test
    void reportsAReferenceNoParameterAnnotationNames() {
        String source = guarded("@PreAuthorize(\"#reference == authentication.name\")", "UUID reference");

        List<String> offences = SpringSecurityRules.unnamedSecurityParameters(source);

        assertEquals(1, offences.size(), "the expression carries no explicit binding to this parameter");
    }

    @Test
    void acceptsAReferenceTheParameterAnnotationNames() {
        String source = guarded(
            "@PreAuthorize(\"#reference == authentication.name\")", "@P(\"reference\") UUID reference"
        );
        assertEquals(List.of(), SpringSecurityRules.unnamedSecurityParameters(source), "the name is stated");
    }

    @Test
    void acceptsTheNameSpringDataContributes() {
        String source = guarded(
            "@PreAuthorize(\"#reference == authentication.name\")", "@Param(\"reference\") UUID reference"
        );
        assertEquals(List.of(), SpringSecurityRules.unnamedSecurityParameters(source), "the discoverer reads it");
    }

    @Test
    void acceptsAnExpressionThatReadsNoParameter() {
        String source = guarded("@PreAuthorize(\"hasAnyRole('ADMIN', 'SALES')\")", "UUID reference");
        assertEquals(List.of(), SpringSecurityRules.unnamedSecurityParameters(source), "nothing is resolved");
    }

    @Test
    void acceptsWhatTheEngineSuppliesItself() {
        String source = guarded("@PreAuthorize(\"#root.this != null\")", "UUID reference");
        assertEquals(List.of(), SpringSecurityRules.unnamedSecurityParameters(source), "no parameter answers these");
    }

    @Test
    void readsAnExpressionEvaluatedAfterTheCall() {
        String source = guarded("@PostAuthorize(\"returnObject.owner == #owner\")", "UUID owner");
        assertEquals(1, SpringSecurityRules.unnamedSecurityParameters(source).size(), "the same string, later");
    }

    @Test
    void readsAnExpressionThatFiltersWhatWasPassedIn() {
        String source = guarded("@PreFilter(\"filterObject.owner == #owner\")", "UUID owner");
        assertEquals(1, SpringSecurityRules.unnamedSecurityParameters(source).size(), "a filter reads one too");
    }

    @Test
    void namesTheReferenceWithoutAnExplicitBinding() {
        String source = guarded("@PreAuthorize(\"#reference == authentication.name\")", "UUID reference");
        assertTrue(
            SpringSecurityRules.unnamedSecurityParameters(source).getFirst().contains("reads #reference"),
            "an offence names the reference whose binding was implicit"
        );
    }

    @Test
    void namesTheAnnotationThatWouldAnswerIt() {
        String source = guarded("@PreAuthorize(\"#reference == authentication.name\")", "UUID reference");
        assertTrue(
            SpringSecurityRules.unnamedSecurityParameters(source).getFirst().contains("@P(\"reference\")"),
            "an offence names the repair as well as the defect"
        );
    }

    @Test
    void reportsTheGuardUnderTheLineItWasWrittenOn() {
        String source = guarded("@PreAuthorize(\"#reference == authentication.name\")", "UUID reference");
        assertTrue(
            SpringSecurityRules.unnamedSecurityParameters(source).getFirst().startsWith("line 5:"),
            "an offence names the line the annotation is on"
        );
    }

    @Test
    void reportsEachDistinctReferenceOnce() {
        String source = guarded(
            "@PreAuthorize(\"#one == #two\")", "UUID one, UUID two"
        );
        assertEquals(2, SpringSecurityRules.unnamedSecurityParameters(source).size(), "two names, two offences");
    }

    @Test
    void reportsOneReferenceReadTwiceOnlyOnce() {
        String source = guarded("@PreAuthorize(\"#one == authentication.name or #one != null\")", "UUID one");
        assertEquals(1, SpringSecurityRules.unnamedSecurityParameters(source).size(), "one name, one repair");
    }

    @Test
    void readsPastAnAnnotationWrittenBetweenTheGuardAndTheMethod() {
        String source = guarded(
            "@PreAuthorize(\"#reference == authentication.name\")\n    @Transactional(readOnly = true)",
            "@P(\"reference\") UUID reference"
        );
        assertEquals(List.of(), SpringSecurityRules.unnamedSecurityParameters(source), "the list is still found");
    }

    @Test
    void readsNoReferenceOutOfAComment() {
        String source = """
            package com.example;

            class Rooms {

                // A guard here would read #reference through an implicit binding.
                public Room rename(UUID reference) {
                    return null;
                }
            }
            """;
        assertEquals(List.of(), SpringSecurityRules.unnamedSecurityParameters(source), "prose guards nothing");
    }

    @Test
    void readsAGuardOnADeclarationThatCarriesNoBody() {
        String source = """
            package com.example;

            interface Rooms {

                @PreAuthorize("#reference == authentication.name")
                Room rename(UUID reference);
            }
            """;
        assertEquals(
            1, SpringSecurityRules.unnamedSecurityParameters(source).size(),
            "an interface is an ordinary place to guard a method, and it declares the parameter too"
        );
    }
}
