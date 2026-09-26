package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateScopeCheckTest {

    private static final List<Path> RESOURCES = List.of(Path.of("src", "main", "resources"));

    private static final String TEMPLATE = "src/main/resources/templates/client/list.html";

    private static final String BOUND_ABOVE_THE_FRAGMENT = """
        <!DOCTYPE html>
        <html lang="en" xmlns:th="http://www.thymeleaf.org">
        <body>
        <div th:with="ordered=${client.editable()}">
            <section th:fragment="rows">
                <a th:if="${ordered}">Move up</a>
            </section>
        </div>
        </body>
        </html>
        """;

    private static final String BOUND_ON_THE_FRAGMENT = """
        <!DOCTYPE html>
        <html lang="en" xmlns:th="http://www.thymeleaf.org">
        <body>
        <div th:with="ordered=${client.editable()}">
            <section th:fragment="rows" th:with="ordered=${client.editable()}">
                <a th:if="${ordered}">Move up</a>
            </section>
        </div>
        </body>
        </html>
        """;

    @Test
    void reportsAFragmentReadingANameBoundAboveIt() {
        Path root = new GitFixture("scope-above").write(TEMPLATE, BOUND_ABOVE_THE_FRAGMENT).root();
        assertEquals(1, offences(root).size(), "a request for the fragment alone never runs the binding above it");
    }

    @Test
    void leavesAFragmentThatBindsTheNameItselfAlone() {
        Path root = new GitFixture("scope-bound").write(TEMPLATE, BOUND_ON_THE_FRAGMENT).root();
        assertTrue(Verdicts.clean(findings(root)), "a binding on the fragment runs whenever the fragment does");
    }

    @Test
    void reportsTheElementUnderThePlaceItWasWritten() {
        Path root = new GitFixture("scope-located").write(TEMPLATE, BOUND_ABOVE_THE_FRAGMENT).root();
        assertTrue(
            offences(root).getFirst().startsWith("src/main/resources/templates/client/list.html:6:"),
            "an offence names the file and the line the reading is on"
        );
    }

    @Test
    void saysWhichNameIsUnboundAndWhatToDoAboutIt() {
        Path root = new GitFixture("scope-worded").write(TEMPLATE, BOUND_ABOVE_THE_FRAGMENT).root();
        String offence = offences(root).getFirst();
        assertTrue(offence.contains("the fragment reads ordered"), "an offence names the name that goes unbound");
        assertTrue(offence.contains("Bind it on the fragment as well"), "and says how to repair it");
    }

    @Test
    void readsWhatAWalkBindsAsBoundWhereTheWalkIs() {
        String walked = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div th:each="row : ${rows}">
                <section th:fragment="cell"><span th:text="${row.name}">Name</span></section>
            </div>
            </body>
            </html>
            """;
        Path root = new GitFixture("scope-walked").write(TEMPLATE, walked).root();
        assertEquals(1, offences(root).size(), "a walk binds what it is walked as, and a fragment under it reads that");
    }

    @Test
    void readsTheStatusAWalkSuppliesWithoutBeingAskedFor() {
        String status = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div th:each="row : ${rows}">
                <section th:fragment="cell"><span th:text="${rowStat.index}">1</span></section>
            </div>
            </body>
            </html>
            """;
        Path root = new GitFixture("scope-status").write(TEMPLATE, status).root();
        assertEquals(1, offences(root).size(), "a walk naming one name is given the status of the walk beside it");
    }

    @Test
    void leavesANameUsedBesideTheFragmentRatherThanInsideItAlone() {
        String beside = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div th:with="saved=${client.saved()}">
                <p th:if="${saved}">Saved above the fragment</p>
                <section th:fragment="rows"><span th:text="${client.name()}">Name</span></section>
                <p th:if="${saved}">Saved below the fragment</p>
            </div>
            </body>
            </html>
            """;
        Path root = new GitFixture("scope-beside").write(TEMPLATE, beside).root();
        assertTrue(Verdicts.clean(findings(root)), "markup beside a fragment is drawn only when the whole page is");
    }

    @Test
    void leavesANameTheFragmentDeclaresAsAParameterAlone() {
        String declared = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div th:with="ordered=${client.editable()}">
                <section th:fragment="rows(ordered)"><a th:if="${ordered}">Move up</a></section>
            </div>
            </body>
            </html>
            """;
        Path root = new GitFixture("scope-declared").write(TEMPLATE, declared).root();
        assertTrue(Verdicts.clean(findings(root)), "what a caller hands over arrives however the fragment was reached");
    }

    @Test
    void leavesAPropertyThatMerelySharesTheNameAlone() {
        String property = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div th:with="saved=${client.saved()}">
                <section th:fragment="rows"><span th:text="${draft.saved}">When</span></section>
            </div>
            </body>
            </html>
            """;
        Path root = new GitFixture("scope-property").write(TEMPLATE, property).root();
        assertTrue(Verdicts.clean(findings(root)), "a property of something else is not the name that was bound");
    }

    @Test
    void readsAnExpressionWrittenInTheTextBetweenTwoElements() {
        String inlined = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div th:with="ordered=${client.editable()}">
                <section th:fragment="rows">[[${ordered}]]</section>
            </div>
            </body>
            </html>
            """;
        Path root = new GitFixture("scope-inlined").write(TEMPLATE, inlined).root();
        assertEquals(1, offences(root).size(), "an expression is written in a document's text as readily as on one");
    }

    @Test
    void readsTheSpellingADocumentUsesToStayValidMarkup() {
        String spelled = """
            <!DOCTYPE html>
            <html lang="en">
            <body>
            <div data-th-with="ordered=${client.editable()}">
                <section data-th-fragment="rows"><a data-th-if="${ordered}">Move up</a></section>
            </div>
            </body>
            </html>
            """;
        Path root = new GitFixture("scope-spelled").write(TEMPLATE, spelled).root();
        assertEquals(1, offences(root).size(), "a document staying valid markup is held to the same rule");
    }

    @Test
    void leavesADocumentWithNoFragmentAtAllAlone() {
        String ordinary = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div th:with="ordered=${client.editable()}"><a th:if="${ordered}">Move up</a></div>
            </body>
            </html>
            """;
        Path root = new GitFixture("scope-ordinary").write(TEMPLATE, ordinary).root();
        assertTrue(Verdicts.clean(findings(root)), "a page drawn only as a whole binds everything it reads");
    }

    @Test
    void readsNothingOutOfMarkupNoEngineCouldRead() {
        Path root = new GitFixture("scope-unreadable").write(TEMPLATE, "<html><body><div th:with=").root();
        assertTrue(Verdicts.clean(findings(root)), "markup no engine could read is reported by the rule that parses");
    }

    @Test
    void readsNothingThatSitsOutsideTheResourcesOfTheModule() {
        Path root = new GitFixture("scope-outside").write("docs/report.html", BOUND_ABOVE_THE_FRAGMENT).root();
        assertTrue(Verdicts.clean(findings(root)), "a document outside the module's resources is not its markup");
    }

    @Test
    void countsTheMarkupItRead() {
        Path root = new GitFixture("scope-counted").write(TEMPLATE, BOUND_ON_THE_FRAGMENT).root();
        assertEquals(1, new TemplateScopeCheck(root, RESOURCES).scanned(), "the count is what the module ships");
    }

    private static List<String> offences(Path root) {
        return Verdicts.offences(findings(root), "bound outside them");
    }

    private static List<Findings> findings(Path root) {
        return new TemplateScopeCheck(root, RESOURCES).findings();
    }
}
