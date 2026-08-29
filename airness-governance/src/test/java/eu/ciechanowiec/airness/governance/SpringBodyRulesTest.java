package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpringBodyRulesTest {

    @Test
    void reportsABeanAssigningItsOwnStaticField() {
        String source = """
            package com.example;

            @Service
            class Holder {

                private static Holder current;

                void register() {
                    current = this;
                }
            }
            """;

        List<String> offences = SpringBodyRules.staticBeanHolders(source);

        assertEquals(1, offences.size(), "the bean holds itself outside the container");
        assertTrue(offences.getFirst().contains("outlives the context"), "the offence names the cost");
    }

    @Test
    void acceptsAStaticConstantOnABean() {
        String source = """
            package com.example;

            @Service
            class Holder {

                private static final String NAME = "holder";
            }
            """;

        assertEquals(List.of(), SpringBodyRules.staticBeanHolders(source), "a constant is not a holder");
    }

    @Test
    void passesOverATypeThatIsNotABean() {
        String source = """
            package com.example;

            class Plain {

                private static Plain current;

                void register() {
                    current = this;
                }
            }
            """;

        assertEquals(List.of(), SpringBodyRules.staticBeanHolders(source), "no container manages this");
    }

    @Test
    void reportsEqualityReadFromAGeneratedIdentifier() {
        String source = """
            package com.example;

            @Entity
            class Row {

                @Id
                @GeneratedValue(strategy = GenerationType.IDENTITY)
                private Long id;

                @Override
                public boolean equals(Object other) {
                    return other instanceof Row row && this.id.equals(row.id);
                }
            }
            """;

        List<String> offences = SpringBodyRules.generatedIdentityEquality(source);

        assertEquals(1, offences.size(), "the identifier is not there until the row is written");
        assertTrue(offences.getFirst().contains("cannot be found"), "the offence names the failure");
    }

    // Both members together are what the marker used to get wrong. The call to equals inside the first
    // was read as a declaration, and the reader then ran on to the next brace it found, which opened the
    // body of the second. The result was named after one method and measured against the body of another.
    @Test
    void countsEachEqualityMemberOnceWhenBothAreDeclared() {
        String source = """
            package com.example;

            @Entity
            class Row {

                @Id
                @GeneratedValue
                private Long id;

                @Override
                public boolean equals(Object other) {
                    return other instanceof Row row && this.id.equals(row.id);
                }

                @Override
                public int hashCode() {
                    return this.id.hashCode();
                }
            }
            """;

        List<String> offences = SpringBodyRules.generatedIdentityEquality(source);

        assertEquals(2, offences.size(), "one offence for the declaration, and not one for the call in it");
    }

    // The false positive the phantom member made reachable. Equality here reads a business key, and the
    // body that follows is the only place the identifier appears.
    @Test
    void acceptsEqualityOnABusinessKeyBesideAMethodThatReadsTheIdentifier() {
        String source = """
            package com.example;

            @Entity
            class Row {

                @Id
                @GeneratedValue
                private Long id;

                private String reference;

                @Override
                public boolean equals(Object other) {
                    return other instanceof Row row && this.reference.equals(row.reference);
                }

                Long identifier() {
                    return this.id;
                }
            }
            """;

        assertEquals(
            List.of(), SpringBodyRules.generatedIdentityEquality(source),
            "equality reads the business key, and the identifier is read by something that is not equality"
        );
    }

    @Test
    void acceptsEqualityReadFromABusinessKey() {
        String source = """
            package com.example;

            @Entity
            class Row {

                @Id
                private Long id;

                private String reference;

                @Override
                public boolean equals(Object other) {
                    return other instanceof Row row && this.reference.equals(row.reference);
                }
            }
            """;

        assertEquals(List.of(), SpringBodyRules.generatedIdentityEquality(source), "the key is stable before saving");
    }

    @Test
    void passesOverATypeThatIsNotAnEntity() {
        String source = """
            package com.example;

            class Row {

                private Long id;

                @Override
                public int hashCode() {
                    return this.id.hashCode();
                }
            }
            """;

        assertEquals(List.of(), SpringBodyRules.generatedIdentityEquality(source), "no database assigns this");
    }

    @Test
    void reportsAHandlerCopyingTheExceptionIntoTheResponse() {
        String source = """
            package com.example;

            class Errors {

                @ExceptionHandler(Exception.class)
                ResponseEntity<String> handle(Exception failure) {
                    return ResponseEntity.status(500).body(failure.getMessage());
                }
            }
            """;

        List<String> offences = SpringBodyRules.echoedExceptions(source);

        assertEquals(1, offences.size(), "the response carries the failure text");
        assertTrue(offences.getFirst().contains("reconnaissance"), "the offence names the risk");
    }

    @Test
    void acceptsAHandlerThatAnswersInItsOwnWords() {
        String source = """
            package com.example;

            class Errors {

                @ExceptionHandler(Exception.class)
                ResponseEntity<String> handle(Exception failure) {
                    return ResponseEntity.status(500).body("the request could not be completed");
                }
            }
            """;

        assertEquals(List.of(), SpringBodyRules.echoedExceptions(source), "nothing internal is returned");
    }

    @Test
    void passesOverASourceCarryingNoHandler() {
        assertEquals(List.of(), SpringBodyRules.echoedExceptions("class Plain {}"), "there is no handler to read");
    }

    @Test
    void acceptsAStaticFieldTheBeanNeverAssigns() {
        String source = """
            package com.example;

            @Service
            class Holder {

                private static Logger log;

                void act() {
                }
            }
            """;

        assertEquals(List.of(), SpringBodyRules.staticBeanHolders(source), "nothing here writes the slot");
    }

    @Test
    void passesOverAnEntityThatDeclaresNoIdentifier() {
        String source = """
            package com.example;

            @Entity
            class Row {

                private String reference;

                @Override
                public int hashCode() {
                    return this.reference.hashCode();
                }
            }
            """;

        assertEquals(List.of(), SpringBodyRules.generatedIdentityEquality(source), "there is no identifier to read");
    }

    @Test
    void passesOverAnEntityThatDecidesNoEqualityAtAll() {
        String source = """
            package com.example;

            @Entity
            class Row {

                @Id
                private Long id;
            }
            """;

        assertEquals(List.of(), SpringBodyRules.generatedIdentityEquality(source), "identity is left to the default");
    }
}
