package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What the obligation does not reach. A class guarding none of its methods has taken none on, a
 * controller answers to the filter chain instead, and a method the container invokes itself runs where
 * no principal is in scope, so a guard on one would deny every invocation rather than decide anything.
 */
class SpringGuardScopeTest {

    private static final String GUARDED = """
        @PreAuthorize("hasRole('ADMIN')")
            public void register(Form form) {
            }
        """;

    @Test
    void acceptsAServiceThatGuardsEveryPublicMethod() {
        List<String> offences = SpringGuardRules.partiallyGuardedClasses(
            service(GUARDED, "@Secured(\"ROLE_ADMIN\")\n    public void retire(UUID reference) {\n    }")
        );

        assertEquals(List.of(), offences, "a class guarding all of them owes nothing");
    }

    @Test
    void acceptsAServiceThatGuardsNoneOfThem() {
        List<String> offences = SpringGuardRules.partiallyGuardedClasses(
            service("public void first() {\n    }", "public void second() {\n    }")
        );

        assertEquals(List.of(), offences, "a class guarding none has taken on no obligation");
    }

    @Test
    void acceptsAClassGuardedAboveItsBody() {
        String source = """
            package sample;

            @Service
            @PreAuthorize("hasRole('ADMIN')")
            class Rooms {

                public void first() {
                }

                @PreAuthorize("hasRole('ADMIN')")
                public void second() {
                }
            }
            """;

        assertEquals(
            List.of(), SpringGuardRules.partiallyGuardedClasses(source),
            "a guard above the body covers every method beneath it"
        );
    }

    @Test
    void acceptsAMethodDeclaredOpenWithPermitAll() {
        List<String> offences = SpringGuardRules.partiallyGuardedClasses(
            service(GUARDED, "@PermitAll\n    public void open() {\n    }")
        );

        assertEquals(List.of(), offences, "admitting everybody is a decision like any other");
    }

    @Test
    void passesOverAControllerLeftToTheChainEvidence() {
        String source = """
            package sample;

            @RestController
            class Rooms {

                @PreAuthorize("hasRole('ADMIN')")
                public String guarded() {
                    return "";
                }

                public String open() {
                    return "";
                }
            }
            """;

        assertEquals(
            List.of(), SpringGuardRules.partiallyGuardedClasses(source),
            "what reaches a handler is settled by the filter chain and read from the running application"
        );
    }

    @Test
    void passesOverATypeCarryingNoStereotype() {
        String source = """
            package sample;

            class Rooms {

                @PreAuthorize("hasRole('ADMIN')")
                public void guarded() {
                }

                public void open() {
                }
            }
            """;

        assertEquals(List.of(), SpringGuardRules.partiallyGuardedClasses(source), "no bean, no obligation");
    }

    @Test
    void readsNoConstructorAsAPublicMethod() {
        List<String> offences = SpringGuardRules.partiallyGuardedClasses(
            service(GUARDED, "public Rooms(Diary diary) {\n    }")
        );

        assertEquals(List.of(), offences, "a constructor is called by the container rather than by a caller");
    }

    @Test
    void readsNoFieldAsAPublicMethod() {
        List<String> offences = SpringGuardRules.partiallyGuardedClasses(
            service(GUARDED, "public static final Control NAME = new Control(\"a\", \"b\");")
        );

        assertEquals(List.of(), offences, "a field with a call in its initialiser declares no method");
    }

    @Test
    void readsNoRecordHeaderAsAPublicMethod() {
        List<String> offences = SpringGuardRules.partiallyGuardedClasses(
            service(GUARDED, "public record Row(String label, int count) {\n    }")
        );

        assertEquals(List.of(), offences, "a record header reads like a method and declares a type");
    }

    @Test
    void readsNoMethodOfANestedType() {
        List<String> offences = SpringGuardRules.partiallyGuardedClasses(
            service(GUARDED, "private static final class Nested {\n\n        public void inner() {\n        }\n    }")
        );

        assertEquals(List.of(), offences, "the obligation is the outer class's rather than the file's");
    }

    @Test
    void readsNoGuardOutOfAQuotedSource() {
        List<String> offences = SpringGuardRules.partiallyGuardedClasses(
            service(
                "public void first() {\n    }", "public String quoted() {\n        return \"@PreAuthorize\";\n    }"
            )
        );

        assertEquals(List.of(), offences, "a guard written inside a literal guards nothing");
    }

    @Test
    void exemptsAMethodTheSchedulerInvokes() {
        List<String> offences = SpringGuardRules.partiallyGuardedClasses(
            service(GUARDED, "@Scheduled(cron = \"0 0 * * * *\")\n    public void swept() {\n    }")
        );

        assertEquals(List.of(), offences, "no principal is in scope on the thread a schedule runs on");
    }

    @Test
    void exemptsAMethodAnEventInvokes() {
        List<String> offences = SpringGuardRules.partiallyGuardedClasses(
            service(GUARDED, "@TransactionalEventListener\n    public void heard(Posted posted) {\n    }")
        );

        assertEquals(List.of(), offences, "a listener is called by the publisher rather than by a caller");
    }

    @Test
    void exemptsALifecycleCallbackTheContainerInvokes() {
        List<String> offences = SpringGuardRules.partiallyGuardedClasses(
            service(GUARDED, "@PostConstruct\n    public void started() {\n    }")
        );

        assertEquals(List.of(), offences, "a callback runs while the context is still being built");
    }

    @Test
    void exemptsAnOverrideWhoseGuardMayBeInherited() {
        List<String> offences = SpringGuardRules.partiallyGuardedClasses(
            service(GUARDED, "@Override\n    public String toString() {\n        return \"\";\n    }")
        );

        assertEquals(List.of(), offences, "the guard may be declared on the method this one overrides");
    }

    // One service, written with whatever members a test gives it, the first of them guarded wherever the
    // test is about a class that has taken on the obligation.
    private static String service(String first, String second) {
        return """
            package sample;

            @Service
            class Rooms {

                %s

                %s
            }
            """.formatted(first, second);
    }
}
