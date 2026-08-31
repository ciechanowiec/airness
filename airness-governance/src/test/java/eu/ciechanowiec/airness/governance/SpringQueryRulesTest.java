package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpringQueryRulesTest {

    private static final String ROW = "com.example.Row";

    private static final Map<String, Integer> RECORDS = Map.of(ROW, 4);

    private static final String REPOSITORY = """
        package com.example;

        interface Rows {

            @Query(\"""
                SELECT new com.example.Row(booking, room.name, client.name, contact.name)
                FROM Booking booking
                \""")
            List<Row> rows();
        }
        """;

    @Test
    void leavesAConstructorGivenWhatTheRecordTakes() {
        assertEquals(List.of(), offences(REPOSITORY), "four arguments answer four components");
    }

    @Test
    void reportsAConstructorGivenFewerArgumentsThanTheRecordTakes() {
        String source = REPOSITORY.replace(", contact.name", "");

        assertEquals(1, offences(source).size(), "three arguments do not answer four components");
    }

    @Test
    void reportsAConstructorGivenMoreArgumentsThanTheRecordTakes() {
        String source = REPOSITORY.replace(", contact.name", ", contact.name, contact.email");

        assertEquals(1, offences(source).size(), "five arguments do not answer four components");
    }

    @Test
    void namesTheTypeAndBothCountsInTheOffence() {
        String source = REPOSITORY.replace(", contact.name", "");

        String offence = offences(source).getFirst();

        assertTrue(
            offence.contains(ROW) && offence.contains("3 argument(s), and it takes 4"),
            "the offence names what was constructed, what it was given and what it takes"
        );
    }

    @Test
    void countsTheLineTheExpressionIsWrittenOn() {
        String source = REPOSITORY.replace(", contact.name", "");

        assertTrue(offences(source).getFirst().startsWith("line 6:"), "the expression is on the sixth line");
    }

    @Test
    void readsACommaInsideACallAsNoSeparatorOfItsOwn() {
        String source = REPOSITORY.replace(
            "contact.name", "CONCAT(contact.firstName, ' ', contact.lastName)"
        );

        assertEquals(List.of(), offences(source), "a nested call is one argument however many it takes");
    }

    @Test
    void readsAQuotedCommaAsNoSeparatorAtAll() {
        String source = REPOSITORY.replace("contact.name", "CONCAT(contact.name, ', ')");

        assertEquals(List.of(), offences(source), "a comma inside a quoted string separates nothing");
    }

    @Test
    void readsAQueryWrittenOnOneLine() {
        String source = """
            package com.example;

            interface Rows {

                @Query("SELECT new com.example.Row(booking, room.name) FROM Booking booking")
                List<Row> rows();
            }
            """;

        assertEquals(1, offences(source).size(), "a one-line query is read like a text block");
    }

    @Test
    void passesOverATypeTheModuleDoesNotDeclare() {
        String source = REPOSITORY.replace("com.example.Row(", "com.example.Elsewhere(");

        assertEquals(List.of(), offences(source), "a module beside this one may declare it");
    }

    @Test
    void passesOverAnExpressionWrittenInAComment() {
        String source = REPOSITORY.replace(
            "interface Rows {", "// new com.example.Row(booking)\ninterface Rows {"
        );

        assertEquals(List.of(), offences(source), "a comment constructs nothing");
    }

    @Test
    void countsTheComponentsOfEveryRecordTheModuleDeclares() {
        String source = """
            package com.example;

            record Row(Booking booking, String room, String client, String contact) {
            }
            """;

        assertEquals(
            Map.of(ROW, 4),
            SpringQueryRules.records(List.of(declared("Row", source))),
            "the header carries four"
        );
    }

    @Test
    void readsATypeArgumentAsOneComponent() {
        String source = """
            package com.example;

            record Row(Map<String, Integer> counted, String named) {
            }
            """;

        assertEquals(
            Map.of(ROW, 2),
            SpringQueryRules.records(List.of(declared("Row", source))),
            "a type argument is one component"
        );
    }

    @Test
    void countsNothingForARecordThatDeclaresNoComponent() {
        String source = """
            package com.example;

            record Row() {
            }
            """;

        assertEquals(
            Map.of(ROW, 0),
            SpringQueryRules.records(List.of(declared("Row", source))),
            "an empty header carries none"
        );
    }

    @Test
    void passesOverARecordDeclaredInsideAnotherType() {
        String source = """
            package com.example;

            final class Rows {

                record Row(String named) {
                }
            }
            """;

        assertEquals(
            Map.of(),
            SpringQueryRules.records(List.of(declared("Rows", source))),
            "the file declares a class, and a record inside one is not what a query names"
        );
    }

    private static List<String> offences(String source) {
        return SpringQueryRules.mismatchedConstructors(declared("Rows", source), RECORDS);
    }

    private static SpringTypes.Declared declared(String name, String source) {
        return new SpringTypes.Declared(
            Path.of("src/main/java/com/example/" + name + ".java"),
            name, JavaCode.blanked(source), source
        );
    }
}
