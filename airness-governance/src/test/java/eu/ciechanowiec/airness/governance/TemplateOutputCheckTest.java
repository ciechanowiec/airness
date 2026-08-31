package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateOutputCheckTest {

    private static final List<Path> RESOURCES = List.of(Path.of("src", "main", "resources"));

    private static final String TEMPLATE = "src/main/resources/templates/room/detail.html";

    private static String body(String written) {
        return """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            %s
            </body>
            </html>
            """.formatted(written);
    }

    @Test
    void reportsAnAttributeThatWritesItsValueAsMarkup() {
        Path root = fixture("output-utext", "<p th:utext=\"${room.notes()}\"></p>");
        assertEquals(1, unescaped(root).size(), "an unescaped attribute writes what was stored as markup");
    }

    @Test
    void readsTheSpellingADocumentUsesToStayValidHtml() {
        Path root = fixture("output-data-utext", "<p data-th-utext=\"${room.notes()}\"></p>");
        assertEquals(1, unescaped(root).size(), "the data spelling asks for the same thing");
    }

    @Test
    void leavesTheEscapingAttributeAlone() {
        Path root = fixture("output-text", "<p th:text=\"${room.notes()}\"></p>");
        assertTrue(Verdicts.clean(findings(root)), "escaped output is what a template is meant to write");
    }

    @Test
    void reportsAnInlinedExpressionThatWritesItsValueAsMarkup() {
        Path root = fixture("output-inlined", "<p>[(${room.notes()})]</p>");
        assertEquals(1, unescaped(root).size(), "the inlined form asks for the same thing an attribute does");
    }

    @Test
    void leavesTheEscapingInlinedFormAlone() {
        Path root = fixture("output-inlined-escaped", "<p>[[${room.notes()}]]</p>");
        assertTrue(Verdicts.clean(findings(root)), "the doubled brackets escape what they write");
    }

    @Test
    void saysWhatToWriteInstead() {
        Path root = fixture("output-repair", "<p th:utext=\"${room.notes()}\"></p>");
        assertTrue(
            unescaped(root).getFirst().contains("Write it with the escaping form"),
            "an offence names the repair as well as the defect"
        );
    }

    @Test
    void reportsTheOutputUnderThePlaceItWasWritten() {
        Path root = fixture("output-located", "<p th:utext=\"${room.notes()}\"></p>");
        assertTrue(
            unescaped(root).getFirst().startsWith("src/main/resources/templates/room/detail.html:4:"),
            "an offence names the file and the line it was written on"
        );
    }

    @Test
    void reportsAnExpressionPreprocessedInsideAnAttribute() {
        Path root = fixture("output-preprocessed", "<p th:text=\"${__${chosen}__.name}\"></p>");
        assertEquals(1, preprocessed(root).size(), "what the inner expression returns is run as an expression");
    }

    @Test
    void reportsAnExpressionPreprocessedInTheText() {
        Path root = fixture("output-preprocessed-text", "<p>[[__${chosen}__]]</p>");
        assertEquals(1, preprocessed(root).size(), "preprocessing is read in the text as readily as on an element");
    }

    @Test
    void leavesAnOrdinaryExpressionAlone() {
        Path root = fixture("output-ordinary", "<p th:text=\"${room.notes()}\"></p>");
        assertTrue(Verdicts.clean(findings(root)), "an expression read once is what every template writes");
    }

    @Test
    void saysWhatToWriteInsteadOfPreprocessing() {
        Path root = fixture("output-preprocessed-repair", "<p th:text=\"${__${chosen}__.name}\"></p>");
        assertTrue(
            preprocessed(root).getFirst().contains("select on the value rather than composing a name from it"),
            "an offence names the repair as well as the defect"
        );
    }

    @Test
    void readsNothingOutOfMarkupNoEngineCouldRead() {
        String unreadable = "<html><body><p th:utext=\"${notes}\" class=\"open></body>\n";
        Path root = new GitFixture("output-unreadable").write(TEMPLATE, unreadable).root();
        assertTrue(Verdicts.clean(findings(root)), "an unreadable file is template-parse's finding to make");
    }

    private static Path fixture(String name, String written) {
        return new GitFixture(name).write(TEMPLATE, body(written)).root();
    }

    private static List<String> unescaped(Path root) {
        return Verdicts.offences(findings(root), "without escaping");
    }

    private static List<String> preprocessed(Path root) {
        return Verdicts.offences(findings(root), "a second time as expressions");
    }

    private static List<Findings> findings(Path root) {
        return new TemplateOutputCheck(root, RESOURCES).findings();
    }
}
