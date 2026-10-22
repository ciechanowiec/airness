package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateNamedArgumentsTest {

    private static final String CARD = "card(title)";

    @Test
    void acceptsNamedLocalsWithAndWithoutDeclaredParameters() {
        for (String declaration : List.of("card", "card()", CARD)) {
            assertTrue(Verdicts.clean(findings(declaration, "title='Hello',theme='dark'")), declaration);
        }
    }

    @Test
    void requiresEveryDeclaredNameEvenWhenTheCountMatches() {
        assertFalse(Verdicts.clean(findings("card(title,theme)", "title='Hello',other='dark'")));
    }

    @Test
    void acceptsReorderedNamedArguments() {
        assertTrue(Verdicts.clean(findings("card(title,theme)", "theme='dark',title='Hello'")));
    }

    @Test
    void rejectsMixedNamedAndPositionalArguments() {
        assertFalse(Verdicts.clean(findings("card(title,theme)", "title='Hello','dark'")));
        assertFalse(Verdicts.clean(findings(CARD, "title='Hello','dark'")));
    }

    @Test
    void acceptsQuotedAndHyphenatedNamedLabels() {
        assertTrue(Verdicts.clean(findings(CARD, "'title'='Hello',theme='dark'")));
        assertTrue(Verdicts.clean(findings(CARD, "title='Hello',data-theme='dark'")));
    }

    @Test
    void ignoresAssignmentsAndSeparatorsWithinArgumentValues() {
        assertTrue(Verdicts.clean(findings(CARD, "title='a,b=c',theme=${items.get('one,two')}")));
        assertTrue(Verdicts.clean(findings(CARD, "${value == 'one,two'}")));
        assertTrue(Verdicts.clean(findings(CARD, "title=${value} == 'one,two'")));
        assertTrue(Verdicts.clean(findings(CARD, "${value} != 'one,two'")));
    }

    @Test
    void distinguishesComputedNamedLabelsFromPositionalAndLiteralNames() {
        assertTrue(Verdicts.clean(findings(CARD, "${key}='Hello'")));
        assertTrue(Verdicts.clean(findings(CARD, "'ti' + 'tle'='Hello'")));
        assertFalse(Verdicts.clean(findings(CARD, "'${key}'='Hello'")));
        assertFalse(Verdicts.clean(findings(CARD, "${key}='Hello','extra'")));
        assertFalse(Verdicts.clean(findings("card(title,theme)", "${key}='Hello'")));
    }

    private static List<Findings> findings(String declaration, String arguments) {
        Path root = new GitFixture("named-fragments")
            .write(
                "src/main/resources/templates/card.html", "<article th:fragment=\"" + declaration + "\">Card</article>"
            ).write(
                "src/main/resources/templates/page.html", "<div th:replace=\"~{card :: card(" + arguments
                    + ")}\"></div>"
            ).root();
        return new TemplateCallCheck(root, List.of(Path.of("src/main/resources"))).findings();
    }
}
