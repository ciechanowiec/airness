package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateReplacementCheckTest {

    private static final List<Path> RESOURCES = List.of(Path.of("src", "main", "resources"));

    private static final String TEMPLATE = "src/main/resources/templates/client/contacts.html";

    private static final String REPLACEMENT_ALONE = """
        <!DOCTYPE html>
        <html lang="en" xmlns:th="http://www.thymeleaf.org">
        <body>
        <th:block th:if="${contact.primary()}">
        <span th:replace="~{fragments/status-pill :: pill('Primary contact', 'positive')}"></span>
        </th:block>
        </body>
        </html>
        """;

    private static final String CONDITIONED_REPLACEMENT = """
        <!DOCTYPE html>
        <html lang="en" xmlns:th="http://www.thymeleaf.org">
        <body>
        <span th:if="${contact.primary()}" th:replace="~{fragments/status-pill :: pill('Primary', 'positive')}"></span>
        </body>
        </html>
        """;

    @Test
    void leavesAReplacementThatCarriesNothingElseAlone() {
        Path root = new GitFixture("replacements-alone").write(TEMPLATE, REPLACEMENT_ALONE).root();
        assertTrue(Verdicts.clean(findings(root)), "a condition on the block around it is read");
    }

    @Test
    void reportsAReplacementThatDiscardsTheConditionBesideIt() {
        Path root = new GitFixture("replacements-conditioned").write(TEMPLATE, CONDITIONED_REPLACEMENT).root();
        assertEquals(1, offences(root).size(), "an element is reported once however much it discards");
    }

    @Test
    void saysWhatTheReplacementDiscards() {
        Path root = new GitFixture("replacements-named").write(TEMPLATE, CONDITIONED_REPLACEMENT).root();
        assertTrue(
            offences(root).getFirst().contains("th:replace discards th:if"),
            "an offence names the replacement and what it throws away"
        );
    }

    @Test
    void reportsTheElementUnderThePlaceItWasWritten() {
        Path root = new GitFixture("replacements-located").write(TEMPLATE, CONDITIONED_REPLACEMENT).root();
        assertTrue(
            offences(root).getFirst().startsWith("src/main/resources/templates/client/contacts.html:4:"),
            "an offence names the file and the line the element is on"
        );
    }

    @Test
    void namesEverythingOneReplacementDiscards() {
        String several = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <li th:each="row : ${rows}" th:text="${row}" th:replace="~{f :: g}"></li>
            </body>
            </html>
            """;
        Path root = new GitFixture("replacements-several").write(TEMPLATE, several).root();
        assertTrue(
            offences(root).getFirst().contains("discards th:each, th:text"),
            "an offence names every attribute the replacement throws away, in one order"
        );
    }

    @Test
    void readsTheSpellingThatKeepsADocumentValidHtml() {
        String data = """
            <!DOCTYPE html>
            <html lang="en">
            <body>
            <span data-th-unless="${shown}" data-th-replace="~{f :: g}"></span>
            </body>
            </html>
            """;
        Path root = new GitFixture("replacements-data-spelling").write(TEMPLATE, data).root();
        assertEquals(1, offences(root).size(), "the data spelling replaces an element like the prefixed one");
    }

    @Test
    void leavesAnInsertionAlone() {
        String inserted = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div th:if="${shown}" th:insert="~{f :: g}"></div>
            </body>
            </html>
            """;
        Path root = new GitFixture("replacements-insertion").write(TEMPLATE, inserted).root();
        assertTrue(Verdicts.clean(findings(root)), "an insertion keeps the element, so what is on it still runs");
    }

    @Test
    void readsNoDialectAttributeOutOfTheNamespaceADocumentDeclares() {
        String declared = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/page :: page}">
            <body></body>
            </html>
            """;
        Path root = new GitFixture("replacements-namespace").write(TEMPLATE, declared).root();
        assertTrue(Verdicts.clean(findings(root)), "declaring the dialect is not using it");
    }

    @Test
    void readsAReplacementOnAnElementThatClosesItself() {
        String standalone = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <input th:if="${shown}" th:replace="~{f :: g}" />
            </body>
            </html>
            """;
        Path root = new GitFixture("replacements-standalone").write(TEMPLATE, standalone).root();
        assertEquals(1, offences(root).size(), "an element that closes itself is replaced too");
    }

    @Test
    void readsNothingOutOfAComment() {
        String commented = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <!-- <span th:if="${shown}" th:replace="~{f :: g}"></span> -->
            </body>
            </html>
            """;
        Path root = new GitFixture("replacements-commented").write(TEMPLATE, commented).root();
        assertTrue(Verdicts.clean(findings(root)), "an element written in a comment replaces nothing");
    }

    @Test
    void readsNothingOutOfMarkupNoEngineCouldRead() {
        String unreadable = """
            <html><body><span th:if="${shown}" th:replace="~{f :: g}" class="open></span>
            """;
        Path root = new GitFixture("replacements-unreadable").write(TEMPLATE, unreadable).root();
        assertTrue(Verdicts.clean(findings(root)), "an unreadable file is template-parse's finding to make");
    }

    private static List<String> offences(Path root) {
        return Verdicts.offences(findings(root), "carry an attribute nothing reads");
    }

    private static List<Findings> findings(Path root) {
        return new TemplateReplacementCheck(root, RESOURCES).findings();
    }
}
