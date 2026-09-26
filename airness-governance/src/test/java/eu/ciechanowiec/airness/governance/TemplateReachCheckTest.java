package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateReachCheckTest {

    private static final List<Path> RESOURCES = List.of(Path.of("src", "main", "resources"));

    private static final String TEMPLATE = "src/main/resources/templates/client/list.html";

    private static final String HANDED_OVER = """
        <!DOCTYPE html>
        <html th:replace="~{layout/page :: page('Clients', ~{:: #page-body})}" xmlns:th="http://www.thymeleaf.org">
        <body>
        <div id="page-body"><p>The clients of this month</p></div>
        </body>
        </html>
        """;

    private static final String BESIDE_WHAT_IS_HANDED_OVER = """
        <!DOCTYPE html>
        <html th:replace="~{layout/page :: page('Clients', ~{:: #page-body})}" xmlns:th="http://www.thymeleaf.org">
        <body>
        <div id="page-body"><p>The clients of this month</p></div>
        <div id="toast">Saved</div>
        </body>
        </html>
        """;

    @Test
    void leavesTheMarkupALayoutIsHandedAlone() {
        Path root = new GitFixture("reach-handed").write(TEMPLATE, HANDED_OVER).root();
        assertTrue(Verdicts.clean(findings(root)), "a page keeping only what it hands over keeps all of it");
    }

    @Test
    void reportsMarkupWrittenBesideWhatTheLayoutIsHanded() {
        Path root = new GitFixture("reach-beside").write(TEMPLATE, BESIDE_WHAT_IS_HANDED_OVER).root();
        assertEquals(1, offences(root).size(), "markup no expression reaches is drawn for nobody");
    }

    @Test
    void reportsTheElementUnderThePlaceItWasWritten() {
        Path root = new GitFixture("reach-located").write(TEMPLATE, BESIDE_WHAT_IS_HANDED_OVER).root();
        assertTrue(
            offences(root).getFirst().startsWith("src/main/resources/templates/client/list.html:5:"),
            "an offence names the file and the line the discarded element is on"
        );
    }

    @Test
    void saysWhatWasDiscardedAndWhatToDoAboutIt() {
        Path root = new GitFixture("reach-worded").write(TEMPLATE, BESIDE_WHAT_IS_HANDED_OVER).root();
        String offence = offences(root).getFirst();
        assertTrue(offence.contains("<div> is handed to no fragment expression"), "an offence names the element");
        assertTrue(offence.contains("Put it inside one the layout is given"), "and says how to repair it");
    }

    @Test
    void readsAnElementHoldingWhatIsHandedOverAsReached() {
        String wrapped = """
            <!DOCTYPE html>
            <html th:replace="~{layout/page :: page('C', ~{:: #page-body})}" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div class="frame"><div id="page-body"><p>Inside a wrapper</p></div></div>
            </body>
            </html>
            """;
        Path root = new GitFixture("reach-wrapping").write(TEMPLATE, wrapped).root();
        assertTrue(Verdicts.clean(findings(root)), "an element is reached through whatever it holds");
    }

    @Test
    void readsAFragmentDeclarationAsReachedByItsName() {
        String declaring = """
            <!DOCTYPE html>
            <html th:replace="~{layout/page :: page('C', ~{:: #page-body})}" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div id="page-body"><p>The clients</p></div>
            <div th:fragment="actions"><a href="/clients/new">Add a client</a></div>
            </body>
            </html>
            """;
        Path root = new GitFixture("reach-declaring").write(TEMPLATE, declaring).root();
        assertTrue(Verdicts.clean(findings(root)), "a fragment is reached by its name rather than by a selector");
    }

    @Test
    void readsAnElementHoldingAFragmentDeclarationAsReached() {
        String hosting = """
            <!DOCTYPE html>
            <html th:replace="~{layout/page :: page('C', ~{:: #page-body})}" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div id="page-body"><table th:replace="~{:: rows}"></table></div>
            <table><tbody th:fragment="rows"><tr><td>A client</td></tr></tbody></table>
            </body>
            </html>
            """;
        Path root = new GitFixture("reach-hosting").write(TEMPLATE, hosting).root();
        assertTrue(
            Verdicts.clean(findings(root)),
            "a wrapper exists so that the fragment it holds is valid markup, and is never drawn itself"
        );
    }

    @Test
    void passesOverAPageThatReplacesItselfWithNothing() {
        String ordinary = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <main><p>A page drawing its own body</p></main>
            </body>
            </html>
            """;
        Path root = new GitFixture("reach-ordinary").write(TEMPLATE, ordinary).root();
        assertTrue(Verdicts.clean(findings(root)), "a page keeping its own body keeps every part of it");
    }

    @Test
    void passesOverALayoutRatherThanReadingItAsAPage() {
        String layout = """
            <!DOCTYPE html>
            <html th:fragment="page(title, content)" xmlns:th="http://www.thymeleaf.org">
            <body>
            <nav>The sections</nav>
            <main><div th:replace="${content}"></div></main>
            </body>
            </html>
            """;
        Path root = new GitFixture("reach-layout").write("src/main/resources/templates/layout/page.html", layout)
            .root();
        assertTrue(Verdicts.clean(findings(root)), "a layout is what draws a page rather than one that is drawn");
    }

    @Test
    void passesOverAPageHandingOverSomethingItCannotName() {
        String tagged = """
            <!DOCTYPE html>
            <html th:replace="~{layout/page :: page('C', ~{:: main})}" xmlns:th="http://www.thymeleaf.org">
            <body>
            <main><p>Handed over by its tag</p></main>
            <div id="toast">Saved</div>
            </body>
            </html>
            """;
        Path root = new GitFixture("reach-tagged").write(TEMPLATE, tagged).root();
        assertTrue(
            Verdicts.clean(findings(root)),
            "a selector naming however many elements carry it leaves this unable to say what is reached"
        );
    }

    @Test
    void readsTheSpellingADocumentUsesToStayValidMarkup() {
        String spelled = """
            <!DOCTYPE html>
            <html data-th-replace="~{layout/page :: page('C', ~{:: #page-body})}">
            <body>
            <div id="page-body"><p>The clients</p></div>
            <div id="toast">Saved</div>
            </body>
            </html>
            """;
        Path root = new GitFixture("reach-spelled").write(TEMPLATE, spelled).root();
        assertEquals(1, offences(root).size(), "a document staying valid markup is held to the same rule");
    }

    @Test
    void readsAnElementThatClosesItself() {
        String standalone = """
            <!DOCTYPE html>
            <html th:replace="~{layout/page :: page('C', ~{:: #page-body})}" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div id="page-body"><p>The clients</p></div>
            <img src="/img/mark.svg" alt="" />
            </body>
            </html>
            """;
        Path root = new GitFixture("reach-standalone").write(TEMPLATE, standalone).root();
        assertEquals(1, offences(root).size(), "an element holding nothing is drawn for nobody just as surely");
    }

    @Test
    void countsThroughMarkupTheDocumentNeverClosed() {
        String implied = """
            <!DOCTYPE html>
            <html th:replace="~{layout/page :: page('C', ~{:: #page-body})}" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div id="page-body"><ul><li>one<li>two</ul></div>
            <div id="toast">Saved</div>
            </body>
            </html>
            """;
        Path root = new GitFixture("reach-implied").write(TEMPLATE, implied).root();
        assertEquals(
            1, offences(root).size(),
            "a list closing its own items leaves the count where the next element of the body is read"
        );
    }

    @Test
    void readsNothingThatSitsOutsideTheResourcesOfTheModule() {
        Path root = new GitFixture("reach-outside").write("docs/report.html", BESIDE_WHAT_IS_HANDED_OVER).root();
        assertTrue(Verdicts.clean(findings(root)), "a document outside the module's resources is not its markup");
    }

    @Test
    void countsTheMarkupItRead() {
        Path root = new GitFixture("reach-counted").write(TEMPLATE, HANDED_OVER).root();
        assertEquals(1, new TemplateReachCheck(root, RESOURCES).scanned(), "the count is what the module ships");
    }

    private static List<String> offences(Path root) {
        return Verdicts.offences(findings(root), "no fragment expression reaches");
    }

    private static List<Findings> findings(Path root) {
        return new TemplateReachCheck(root, RESOURCES).findings();
    }
}
