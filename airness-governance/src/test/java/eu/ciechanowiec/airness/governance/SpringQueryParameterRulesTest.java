package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpringQueryParameterRulesTest {

    private static final String NAMED = """
        interface Rows {

            @Query("select row from Row row where row.owner = :owner")
            List<Row> rows(@Param("owner") UUID owner, Pageable page);
        }
        """;

    @Test
    void acceptsNamedParametersAndPassesOverSpringDataInfrastructure() {
        assertEquals(List.of(), SpringQueryParameterRules.mismatched(NAMED), "the one binding agrees");
    }

    @Test
    void reportsABindableParameterWithoutParameterAnnotation() {
        String source = NAMED.replace("@Param(\"owner\") ", "");

        List<String> offences = SpringQueryParameterRules.mismatched(source);

        assertEquals(1, offences.size(), "one query method omits the declaration");
        assertTrue(offences.getFirst().contains("owner has no @Param"), "the repair names the parameter");
    }

    @Test
    void reportsANameThatDisagreesWithTheQuery() {
        String source = NAMED.replace("@Param(\"owner\")", "@Param(\"holder\")");

        assertEquals(1, SpringQueryParameterRules.mismatched(source).size(), "holder does not bind owner");
    }

    @Test
    void reportsANumericBindingSeparately() {
        String source = NAMED.replace(":owner", "?1");

        assertEquals(1, SpringQueryParameterRules.positional(source).size(), "the first position is implicit");
    }

    @Test
    void readsNamedSpelWithoutReadingSuppliedVariables() {
        String source = NAMED.replace(
            ":owner", ":#{#owner} and row.kind = :#{#entityName}"
        );

        assertEquals(List.of(), SpringQueryParameterRules.mismatched(source), "only owner is a method value");
    }

    @Test
    void ignoresPlaceholderTextInsideSqlLiteralsAndComments() {
        String source = NAMED.replace(
            "row.owner = :owner",
            "row.owner = :owner and row.note = 'it''s :notBound' /* :blocked */ -- :neither"
        );

        assertEquals(List.of(), SpringQueryParameterRules.mismatched(source), "quoted prose binds nothing");
    }

    @Test
    void requiresAnExplicitCountQueryToBindTheSameNames() {
        String source = NAMED.replace(
            "@Query(\"select row from Row row where row.owner = :owner\")",
            "@Query(value = \"select row from Row row where row.owner = :owner\","
                + " countQuery = \"select count(row) from Row row\")"
        );

        assertEquals(1, SpringQueryParameterRules.mismatched(source).size(), "the count drops its filter");
    }

    @Test
    void readsAQueryWrittenAsATextBlock() {
        String source = """
            interface Rows {

                @Query(
                    \"""
                    select row
                    from Row row
                    where row.owner = :owner
                    \"""
                )
                List<Row> rows(@Param("owner") UUID owner);
            }
            """;

        assertEquals(List.of(), SpringQueryParameterRules.mismatched(source), "the text block has one name");
    }

    @Test
    void leavesADerivedQueryMethodAlone() {
        String source = "interface Rows { List<Row> findByOwner(UUID owner); }";

        assertEquals(List.of(), SpringQueryParameterRules.mismatched(source), "there is no query string");
    }

    @Test
    void reportsADuplicateParameterAnnotationName() {
        String source = """
            interface Rows {

                @Query("select row from Row row where row.owner = :owner")
                Row row(@Param("owner") UUID first, @Param("owner") UUID second);
            }
            """;

        assertTrue(
            SpringQueryParameterRules.mismatched(source).getFirst().contains("owner is duplicated"),
            "two declarations cannot answer one name"
        );
    }

    @Test
    void reportsAnEmptyParameterAnnotationName() {
        String source = NAMED.replace("@Param(\"owner\")", "@Param(\"\")");

        assertTrue(
            SpringQueryParameterRules.mismatched(source).getFirst().contains("@Param name is empty"),
            "empty is not a binding name"
        );
    }

    @Test
    void reportsTheLineWhereTheQueryIsDeclared() {
        String source = NAMED.replace("@Param(\"owner\") ", "");

        assertTrue(
            SpringQueryParameterRules.mismatched(source).getFirst().startsWith("line 3:"),
            "the annotation begins on the third line"
        );
    }
}
