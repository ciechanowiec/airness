package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateExpressionCheckTest {

    private static final List<Path> RESOURCES = List.of(Path.of("src", "main", "resources"));

    private static final String TEMPLATE = "src/main/resources/templates/catalogue/list.html";

    private static final String OUTSIDE = """
        <!DOCTYPE html>
        <html lang="en" xmlns:th="http://www.thymeleaf.org">
        <body>
        <span th:text="${item.offered()} ? words.of('offered') : words.of('withdrawn')">Offered</span>
        </body>
        </html>
        """;

    private static final String INSIDE = """
        <!DOCTYPE html>
        <html lang="en" xmlns:th="http://www.thymeleaf.org">
        <body>
        <span th:text="${item.offered() ? words.of('offered') : words.of('withdrawn')}">Offered</span>
        </body>
        </html>
        """;

    @Test
    void leavesACallWrittenInsideTheExpressionThatEvaluatesItAlone() {
        Path root = new GitFixture("expressions-inside").write(TEMPLATE, INSIDE).root();
        assertTrue(Verdicts.clean(findings(root)), "a call inside a variable expression is evaluated");
    }

    @Test
    void reportsACallWrittenWhereNothingEvaluatesIt() {
        Path root = new GitFixture("expressions-outside").write(TEMPLATE, OUTSIDE).root();
        assertEquals(2, offences(root).size(), "each arm of the conditional is reported");
    }

    @Test
    void namesTheCallItReports() {
        Path root = new GitFixture("expressions-named").write(TEMPLATE, OUTSIDE).root();
        assertTrue(offences(root).getFirst().contains("words.of(...)"), "an offence names the call");
    }

    @Test
    void saysWhatToDoAboutTheCallItReports() {
        Path root = new GitFixture("expressions-repair").write(TEMPLATE, OUTSIDE).root();
        assertTrue(
            offences(root).getFirst().contains("Put the call inside the expression that needs it"),
            "an offence carries the repair"
        );
    }

    @Test
    void reportsTheCallUnderThePlaceItWasWritten() {
        Path root = new GitFixture("expressions-located").write(TEMPLATE, OUTSIDE).root();
        assertTrue(
            offences(root).getFirst().startsWith("src/main/resources/templates/catalogue/list.html:4:"),
            "an offence names the file and the line the value is on"
        );
    }

    @Test
    void readsTheSpellingThatKeepsADocumentValidHtml() {
        String data = """
            <!DOCTYPE html>
            <html lang="en">
            <body>
            <span data-th-text="${a} ? words.of('x') : 'y'">x</span>
            </body>
            </html>
            """;
        Path root = new GitFixture("expressions-data-spelling").write(TEMPLATE, data).root();
        assertEquals(1, offences(root).size(), "the data spelling carries an expression like the prefixed one");
    }

    @Test
    void readsAnExpressionWrittenInTheTextBetweenElements() {
        String inlined = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <span>[[${a} ? words.of('x') : 'y']]</span>
            </body>
            </html>
            """;
        Path root = new GitFixture("expressions-inlined").write(TEMPLATE, inlined).root();
        assertEquals(
            1, offences(root).size(),
            "the engine reads an expression in text as it reads one in an attribute"
        );
    }

    @Test
    void leavesTheSampleContentOfAnElementAlone() {
        String sample = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <option th:text="${offering.label()}">Lunch (Per person, EUR 25.00)</option>
            </body>
            </html>
            """;
        Path root = new GitFixture("expressions-sample").write(TEMPLATE, sample).root();
        assertTrue(Verdicts.clean(findings(root)), "what a designer leaves inside an element is text");
    }

    @Test
    void leavesAFragmentDeclarationAlone() {
        String declaration = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div th:fragment="pill(label, tone)"></div>
            </body>
            </html>
            """;
        Path root = new GitFixture("expressions-declaration").write(TEMPLATE, declaration).root();
        assertTrue(Verdicts.clean(findings(root)), "a declaration writes the names of its parameters");
    }

    @Test
    void readsNothingOutOfAComment() {
        String commented = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <!-- <span th:text="${a} ? words.of('x') : 'y'">x</span> -->
            </body>
            </html>
            """;
        Path root = new GitFixture("expressions-commented").write(TEMPLATE, commented).root();
        assertTrue(Verdicts.clean(findings(root)), "a commented value is drawn by nothing");
    }

    @Test
    void readsNothingOutOfMarkupNoEngineCouldRead() {
        String unreadable = "<html><body><p th:text=\"${a} ? words.of('x') : 'y'\" class=\"open></body>\n";
        Path root = new GitFixture("expressions-unreadable").write(TEMPLATE, unreadable).root();
        assertTrue(Verdicts.clean(findings(root)), "an unreadable file is template-parse's finding to make");
    }

    private static List<String> offences(Path root) {
        return Verdicts.offences(findings(root), "Calls written where");
    }

    private static List<Findings> findings(Path root) {
        return new TemplateExpressionCheck(root, RESOURCES).findings();
    }
}
