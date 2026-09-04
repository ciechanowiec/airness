package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A fragment call written inside another call's argument list, which is how a page hands its own parts
 * to the shell that draws it. Such a call names a fragment of a template and hands it a positional list
 * exactly as one written on its own does, so it answers to the same two rules.
 */
class TemplateNestedCallTest {

    private static final List<Path> RESOURCES = List.of(Path.of("src", "main", "resources"));

    private static final String SHELL = "src/main/resources/templates/layout/page.html";

    private static final String PARTS = "src/main/resources/templates/fragments/field.html";

    private static final String LISTING = "src/main/resources/templates/room/list.html";

    private static final String UNRESOLVED = "reach nothing the module declares";

    private static final String MISCOUNTED = "argument list the declaration does not take";

    // The shell takes what a page hands it and puts it in place through the variable it took, which is
    // a built name and so is passed over. Nothing here offends on its own account.
    private static final String WRAPS = """
        <!DOCTYPE html>
        <html lang="en" xmlns:th="http://www.thymeleaf.org">
        <body>
        <div th:fragment="page(title, body)"><div th:replace="${body}"></div></div>
        </body>
        </html>
        """;

    private static final String PART = """
        <!DOCTYPE html>
        <html lang="en" xmlns:th="http://www.thymeleaf.org">
        <body><div th:fragment="field(control, value, error)">Field</div></body>
        </html>
        """;

    @Test
    void reportsACallHandedTooFewArgumentsInsideAnotherCall() {
        List<String> offences = handing(
            "nested-few", "~{layout/page :: page('Rooms', ~{fragments/field :: field(${name})})}", MISCOUNTED
        );
        assertEquals(1, offences.size(), "a call handed over is held to the list it hands over too");
    }

    @Test
    void saysWhatTheNestedDeclarationTakes() {
        List<String> offences = handing(
            "nested-says", "~{layout/page :: page('Rooms', ~{fragments/field :: field(${name})})}", MISCOUNTED
        );
        assertTrue(
            offences.getFirst().contains("1 argument(s), and it is declared to take 3"),
            "the offence names what the nested call handed and what the declaration takes"
        );
    }

    @Test
    void reportsANestedCallNamingAFragmentNothingDeclares() {
        List<String> offences = handing(
            "nested-unnamed", "~{layout/page :: page('Rooms', ~{fragments/field :: control(a, b, c)})}", UNRESOLVED
        );
        assertEquals(1, offences.size(), "a name declared nowhere reaches nothing at any depth");
    }

    @Test
    void reportsANestedCallNamingATemplateNothingAnswers() {
        List<String> offences = handing(
            "nested-unanswered", "~{layout/page :: page('Rooms', ~{fragments/control :: field(a, b, c)})}", UNRESOLVED
        );
        assertEquals(1, offences.size(), "a template no resource answers reaches nothing at any depth");
    }

    @Test
    void leavesANestedCallThatMatchesTheDeclarationAlone() {
        List<String> offences = handing(
            "nested-matching", "~{layout/page :: page('Rooms', ~{fragments/field :: field(a, b, c)})}", MISCOUNTED
        );
        assertEquals(List.of(), offences, "a call handed over that matches its declaration is no offence");
    }

    @Test
    void passesOverASelectorHandedToAnotherCall() {
        List<String> offences = handing(
            "nested-selector", "~{layout/page :: page('Rooms', ~{:: #controls})}", UNRESOLVED
        );
        assertEquals(List.of(), offences, "a selector reaches an element rather than a declaration");
    }

    @Test
    void readsACallWrittenInsideACallInsideAnother() {
        List<String> offences = handing(
            "nested-twice",
            "~{layout/page :: page('A', ~{layout/page :: page('B', ~{fragments/field :: field(a)})})}",
            MISCOUNTED
        );
        assertEquals(1, offences.size(), "a value is read to the bottom rather than one level down");
    }

    // The shape every nested call of a real project takes: a page hands a fragment of its own to the
    // shell, naming no template, so what it is resolved against is the document that wrote it.
    @Test
    void resolvesANestedCallOnTheDocumentThatWroteIt() {
        Path root = new GitFixture("nested-local")
            .write(SHELL, WRAPS)
            .write(
                LISTING,
                """
                    <!DOCTYPE html>
                    <html lang="en" xmlns:th="http://www.thymeleaf.org">
                    <body>
                    <div th:fragment="controls">Controls</div>
                    <div th:replace="~{layout/page :: page('Rooms', ~{:: controls})}"></div>
                    </body>
                    </html>
                    """
            )
            .root();
        assertTrue(
            Verdicts.clean(new TemplateCallCheck(root, RESOURCES).findings()),
            "a call handed over and naming no template reaches the document that wrote it"
        );
    }

    private static List<String> handing(String name, String expression, String rule) {
        Path root = new GitFixture(name)
            .write(SHELL, WRAPS)
            .write(PARTS, PART)
            .write(
                LISTING,
                """
                    <!DOCTYPE html>
                    <html lang="en" xmlns:th="http://www.thymeleaf.org">
                    <body><div th:replace="%s"></div></body>
                    </html>
                    """.formatted(expression)
            )
            .root();
        return Verdicts.offences(new TemplateCallCheck(root, RESOURCES).findings(), rule);
    }
}
