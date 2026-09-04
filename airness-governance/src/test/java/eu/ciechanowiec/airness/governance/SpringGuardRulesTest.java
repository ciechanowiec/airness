package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpringGuardRulesTest {

    @Test
    void reportsThePublicMethodItsSiblingsLeftUnguarded() {
        List<String> offences = SpringGuardRules.partiallyGuardedClasses(
            service("public void forgotten(UUID reference) {\n    }")
        );

        assertEquals(1, offences.size(), "the unguarded sibling is the offence");
    }

    @Test
    void namesTheMethodAndTheClassThatGuardsTheOthers() {
        List<String> offences = SpringGuardRules.partiallyGuardedClasses(
            service("public void forgotten(UUID reference) {\n    }")
        );

        assertTrue(
            offences.getFirst().contains("Rooms guards other public methods")
                && offences.getFirst().contains("forgotten carries none"),
            "the offence names the class and the method"
        );
    }

    @Test
    void namesARepairTheDefaultConfigurationHonours() {
        List<String> offences = SpringGuardRules.partiallyGuardedClasses(
            service("public void forgotten(UUID reference) {\n    }")
        );

        assertTrue(
            offences.getFirst().contains("permitAll()"),
            "an open method is declared open, and with the annotation the default configuration reads"
        );
    }

    @Test
    void reportsTheMethodUnderTheLineItIsDeclaredOn() {
        List<String> offences = SpringGuardRules.partiallyGuardedClasses(
            service("public void forgotten(UUID reference) {\n    }")
        );

        assertTrue(offences.getFirst().startsWith("line 10:"), "the offence points at the declaration");
    }

    @Test
    void reportsEveryUnguardedMethodOfTheClass() {
        List<String> offences = SpringGuardRules.partiallyGuardedClasses(
            service("public void first() {\n    }\n\n    public void second() {\n    }")
        );

        assertEquals(2, offences.size(), "each unguarded method is answered for");
    }

    // One service that guards a method and is given one more, which is the shape the rule is about: the
    // obligation is taken on by the guarded sibling, and what the test varies is the method beside it.
    private static String service(String member) {
        return """
            package sample;

            @Service
            class Rooms {

                @PreAuthorize("hasRole('ADMIN')")
                public void register(Form form) {
                }

                %s
            }
            """.formatted(member);
    }
}
