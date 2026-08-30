package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateParseCheckTest {

    private static final List<Path> RESOURCES = List.of(Path.of("src", "main", "resources"));
    private static final String TEMPLATE = "src/main/resources/templates/page.html";
    private static final String UNREADABLE = """
        <html><body><p>An attribute value that never closes
        <div class="open></div>
        </body></html>
        """;
    private static final String WELL_FORMED = """
        <!DOCTYPE html>
        <html lang="en">
        <body>
        <p>A page that closes everything it opens.</p>
        </body>
        </html>
        """;

    @Test
    void readsATemplateThatClosesEverythingItOpensWithoutComplaint() {
        Path root = new GitFixture("template-parse-well-formed").write(TEMPLATE, WELL_FORMED).root();
        assertTrue(Verdicts.clean(findings(root)), "markup an engine can read is not an offence");
    }

    @Test
    void namesTheTemplateThatNoEngineCouldRead() {
        Path root = new GitFixture("template-parse-unreadable").write(TEMPLATE, UNREADABLE).root();
        assertEquals(1, offences(root).size(), "a file the parser refuses is reported once");
    }

    @Test
    void reportsAnUnreadableTemplateUnderThePathThatLocatesIt() {
        Path root = new GitFixture("template-parse-named").write(TEMPLATE, UNREADABLE).root();
        assertTrue(
            offences(root).getFirst().contains("page.html"),
            "an offence names the file it is about"
        );
    }

    @Test
    void leavesTheAttributesOfATemplatingDialectAlone() {
        String dialect = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div th:replace="~{fragments/nav :: nav('rooms')}"></div>
            <span th:text="${room.name()}" x-data="{ open: true }" @click="open = false">Name</span>
            </body>
            </html>
            """;
        Path root = new GitFixture("template-parse-dialect").write(TEMPLATE, dialect).root();
        assertTrue(Verdicts.clean(findings(root)), "an attribute the HTML specification lacks is not a defect");
    }

    @Test
    void leavesTheElementsOfATemplatingDialectAlone() {
        String block = """
            <!DOCTYPE html>
            <html lang="en">
            <body>
            <th:block th:if="${rooms.isEmpty()}"><p>Nothing yet.</p></th:block>
            </body>
            </html>
            """;
        Path root = new GitFixture("template-parse-block").write(TEMPLATE, block).root();
        assertTrue(Verdicts.clean(findings(root)), "an element the HTML specification lacks is not a defect");
    }

    @Test
    void readsNothingThatSitsOutsideTheResourcesOfTheModule() {
        Path root = new GitFixture("template-parse-elsewhere").write("docs/report.html", UNREADABLE).root();
        assertTrue(Verdicts.clean(findings(root)), "only what the module ships as a resource is read");
    }

    @Test
    void readsNothingButMarkup() {
        Path root = new GitFixture("template-parse-other-resources")
            .write("src/main/resources/application.yaml", "server:\n  port: 0\n")
            .root();
        assertEquals(0, new TemplateParseCheck(root, RESOURCES).scanned(), "a resource that is not markup is not read");
    }

    private static List<String> offences(Path root) {
        return Verdicts.offences(findings(root), "no template engine could read");
    }

    private static List<Findings> findings(Path root) {
        return new TemplateParseCheck(root, RESOURCES).findings();
    }
}
