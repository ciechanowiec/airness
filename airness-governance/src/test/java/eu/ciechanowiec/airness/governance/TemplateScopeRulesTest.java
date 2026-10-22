package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TemplateScopeRulesTest {

    private static final String ORDERED = "ordered";

    @Test
    void ignoresLiteralTextAndLiteralResourceNames() {
        for (
            String value : List.of(
                "'ordered'", "\"ordered\"", "'it''s ordered'", "'it\\'s ordered'", "#{ordered}",
                "#{ordered(${other})}", "@{/ordered(ordered=${other})}", "~{ordered :: ordered}",
                "|ordered ${other}|", "'${ordered}'", "${'ordered'}"
            )
        ) {
            assertFalse(TemplateScopeRules.reads(value, ORDERED), value);
        }
    }

    @Test
    void readsVariablesInsideComposedExpressions() {
        for (
            String value : List.of(
                "${ordered == 'ordered'}", "#{key(${ordered})}", "@{${ordered}}",
                "|before ${ordered}|", "${ {'key': ordered} }", "|It's ${ordered} and it's ready|",
                "*{ordered}", "${ordered + '}'}"
            )
        ) {
            assertTrue(TemplateScopeRules.reads(value, ORDERED), value);
        }
    }

    @Test
    void allowsAFragmentToPrintTheLiteralNameOfAnOuterVariable() {
        Path root = new GitFixture("literal-fragment")
            .write(
                "src/main/resources/templates/card.html", """
                    <main th:with="label=${value}">
                      <div th:fragment="body"><span th:text="'label'">Label</span></div>
                    </main>
                    """
            ).root();
        assertTrue(Verdicts.clean(new TemplateScopeCheck(root, List.of(Path.of("src/main/resources"))).findings()));
    }

    @Test
    void readsEveryNameAListOfAssignmentsGivesAValueTo() {
        assertEquals(
            Set.of(ORDERED, "saved"),
            TemplateScopeRules.bound(Map.of("th:with", "ordered=${room.editable()},saved=${room.saved()}")),
            "a list of assignments binds one name per assignment"
        );
    }

    @Test
    void readsAComparisonInsideAnExpressionAsNoAssignment() {
        assertEquals(
            Set.of(ORDERED),
            TemplateScopeRules.bound(Map.of("th:with", "ordered=${seats == 4}")),
            "an equals sign inside an expression compares rather than binds"
        );
    }

    @Test
    void readsWhatAWalkIsWalkedAsAndTheStatusBesideIt() {
        assertEquals(
            Set.of("row", "rowStat"),
            TemplateScopeRules.bound(Map.of("th:each", "row : ${rows}")),
            "a walk naming one name is given the status of the walk under that name with a suffix"
        );
    }

    @Test
    void readsBothNamesAWalkDeclaresAndSuppliesNoFurtherOne() {
        assertEquals(
            Set.of("row", "counted"),
            TemplateScopeRules.bound(Map.of("th:each", "row, counted : ${rows}")),
            "a walk naming its own status is given nothing else"
        );
    }

    @Test
    void readsNothingFromAWalkThatWritesNoSeparator() {
        assertTrue(
            TemplateScopeRules.bound(Map.of("th:each", "${rows}")).isEmpty(),
            "a walk that names nothing to walk as binds nothing"
        );
    }

    @Test
    void readsNothingFromAWalkThatNamesNothingBeforeTheSeparator() {
        assertTrue(
            TemplateScopeRules.bound(Map.of("th:each", " : ${rows}")).isEmpty(),
            "a name that is only space is no name at all"
        );
    }

    @Test
    void readsNothingFromAnElementThatBindsNothing() {
        assertTrue(
            TemplateScopeRules.bound(Map.of("th:text", "${room.name()}")).isEmpty(),
            "an attribute that writes a value binds no name"
        );
    }

    @Test
    void readsTheSpellingADocumentUsesToStayValidMarkup() {
        assertEquals(
            Set.of(ORDERED),
            TemplateScopeRules.bound(Map.of("data-th-with", "ordered=${room.editable()}")),
            "a document staying valid markup binds the same way"
        );
    }

    @Test
    void readsANameThatIsTheRootOfWhatAnExpressionNames() {
        assertTrue(TemplateScopeRules.reads("${ordered}", ORDERED), "a name written alone is the root of itself");
        assertTrue(TemplateScopeRules.reads("${ordered.size()}", ORDERED), "and so is one that is asked something");
    }

    @Test
    void readsNeitherAPropertyNorALongerNameAsThatName() {
        assertFalse(TemplateScopeRules.reads("${draft.ordered}", ORDERED), "a property of something else is not it");
        assertFalse(TemplateScopeRules.reads("${reordered}", ORDERED), "and neither is a longer name holding it");
    }
}
