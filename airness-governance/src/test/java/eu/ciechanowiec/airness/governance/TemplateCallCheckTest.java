package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateCallCheckTest {

    private static final List<Path> RESOURCES = List.of(Path.of("src", "main", "resources"));

    private static final String LIBRARY = "src/main/resources/templates/fragments/field.html";

    private static final String PAGE = "src/main/resources/templates/room/list.html";

    private static final String DECLARES = """
        <!DOCTYPE html>
        <html lang="en" xmlns:th="http://www.thymeleaf.org">
        <body>
        <div th:fragment="field(control, value, error)">
        <input th:id="${control}" />
        </div>
        </body>
        </html>
        """;

    private static String calling(String expression) {
        return """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div th:replace="%s"></div>
            </body>
            </html>
            """.formatted(expression);
    }

    @Test
    void leavesACallThatMatchesTheDeclarationAlone() {
        Path root = fixture("calls-matching", "~{fragments/field :: field(${name}, '', '')}");
        assertTrue(Verdicts.clean(findings(root)), "three arguments reach a fragment declared with three");
    }

    @Test
    void reportsACallHandedTooFewArguments() {
        Path root = fixture("calls-too-few", "~{fragments/field :: field(${name})}");
        assertEquals(1, miscounted(root).size(), "a fragment declared with three is not called with one");
    }

    @Test
    void reportsACallHandedTooManyArguments() {
        Path root = fixture("calls-too-many", "~{fragments/field :: field(a, b, c, d)}");
        assertEquals(1, miscounted(root).size(), "a fragment declared with three is not called with four");
    }

    @Test
    void saysWhatTheDeclarationTakes() {
        Path root = fixture("calls-counted", "~{fragments/field :: field(${name})}");
        assertTrue(
            miscounted(root).getFirst().contains("1 argument(s), and it is declared to take 3"),
            "an offence names the count handed over and the count declared"
        );
    }

    @Test
    void reportsAFragmentNameNothingDeclares() {
        Path root = fixture("calls-unnamed", "~{fragments/field :: control(a, b, c)}");
        assertEquals(1, unresolved(root).size(), "a name the document declares nowhere reaches nothing");
    }

    @Test
    void reportsATemplateNameNothingAnswers() {
        Path root = fixture("calls-unanswered", "~{fragments/control :: field(a, b, c)}");
        assertEquals(1, unresolved(root).size(), "a template no resource answers reaches nothing");
    }

    @Test
    void saysWhichFragmentAndDocumentACallMissed() {
        Path root = fixture("calls-named", "~{fragments/field :: control(a, b, c)}");
        assertTrue(
            unresolved(root).getFirst().contains("names the fragment control"),
            "an offence names the fragment the call reached for"
        );
    }

    @Test
    void reportsTheCallUnderThePlaceItWasWritten() {
        Path root = fixture("calls-located", "~{fragments/field :: control(a, b, c)}");
        assertTrue(
            unresolved(root).getFirst().startsWith("src/main/resources/templates/room/list.html:4:"),
            "an offence names the file and the line the call is on"
        );
    }

    @Test
    void passesOverACallWhoseNameIsBuiltRatherThanWritten() {
        Path root = fixture("calls-built", "${content}");
        assertTrue(Verdicts.clean(findings(root)), "a name chosen at runtime is not a fact about the source");
    }

    @Test
    void passesOverASelectorThatReachesAnElementRatherThanAFragment() {
        Path root = fixture("calls-selector", "~{fragments/field :: #control}");
        assertTrue(Verdicts.clean(findings(root)), "a markup selector reaches an element with no argument list");
    }

    @Test
    void resolvesACallOnTheDocumentThatWroteIt() {
        String local = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div th:fragment="step(label, target)">Step</div>
            <div th:replace="~{:: step('Next', ${ahead})}"></div>
            </body>
            </html>
            """;
        Path root = new GitFixture("calls-local").write(PAGE, local).root();
        assertTrue(Verdicts.clean(findings(root)), "a call naming no template reaches its own document");
    }

    @Test
    void reportsALocalCallOnAFragmentTheDocumentNeverDeclared() {
        String local = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div th:fragment="step(label, target)">Step</div>
            <div th:replace="~{:: stride('Next', ${ahead})}"></div>
            </body>
            </html>
            """;
        Path root = new GitFixture("calls-local-missing").write(PAGE, local).root();
        assertEquals(1, unresolved(root).size(), "a local call is held to the document that wrote it");
    }

    @Test
    void readsTheSpellingADocumentUsesToStayValidHtml() {
        String data = """
            <!DOCTYPE html>
            <html lang="en">
            <body>
            <div data-th-replace="~{fragments/field :: field(a)}"></div>
            </body>
            </html>
            """;
        Path root = new GitFixture("calls-data").write(LIBRARY, DECLARES).write(PAGE, data).root();
        assertEquals(1, miscounted(root).size(), "the data spelling is the same call");
    }

    @Test
    void readsNothingOutOfMarkupNoEngineCouldRead() {
        String unreadable = """
            <html><body><div th:replace="~{fragments/field :: control(a)}" class="open></body>
            """;
        Path root = new GitFixture("calls-unreadable").write(LIBRARY, DECLARES).write(PAGE, unreadable).root();
        assertTrue(Verdicts.clean(findings(root)), "an unreadable file is template-parse's finding to make");
    }

    private static Path fixture(String name, String expression) {
        return new GitFixture(name).write(LIBRARY, DECLARES).write(PAGE, calling(expression)).root();
    }

    private static List<String> unresolved(Path root) {
        return Verdicts.offences(findings(root), "reach nothing the module declares");
    }

    private static List<String> miscounted(Path root) {
        return Verdicts.offences(findings(root), "argument list the declaration does not take");
    }

    private static List<Findings> findings(Path root) {
        return new TemplateCallCheck(root, RESOURCES).findings();
    }
}
