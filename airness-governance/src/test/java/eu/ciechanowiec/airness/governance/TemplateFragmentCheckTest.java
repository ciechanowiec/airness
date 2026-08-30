package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateFragmentCheckTest {

    private static final List<Path> RESOURCES = List.of(Path.of("src", "main", "resources"));

    private static final String TEMPLATE = "src/main/resources/templates/fragments/field.html";

    private static final String AT_THE_CAP = """
        <!DOCTYPE html>
        <html lang="en" xmlns:th="http://www.thymeleaf.org">
        <body>
        <div th:fragment="field(label, name, control, error)"></div>
        </body>
        </html>
        """;

    private static final String OVER_THE_CAP = """
        <!DOCTYPE html>
        <html lang="en" xmlns:th="http://www.thymeleaf.org">
        <body>
        <div th:fragment="field(label, name, type, purpose, value, error)"></div>
        </body>
        </html>
        """;

    @Test
    void leavesAFragmentAtTheCapAlone() {
        Path root = new GitFixture("fragments-at-the-cap").write(TEMPLATE, AT_THE_CAP).root();
        assertTrue(Verdicts.clean(findings(root)), "four arguments is what a callable may take");
    }

    @Test
    void reportsAFragmentThatTakesMoreArgumentsThanACallableMay() {
        Path root = new GitFixture("fragments-over-the-cap").write(TEMPLATE, OVER_THE_CAP).root();
        assertEquals(1, offences(root).size(), "a fragment past the cap is reported once");
    }

    @Test
    void saysHowManyArgumentsTheReportedFragmentTakes() {
        Path root = new GitFixture("fragments-counted").write(TEMPLATE, OVER_THE_CAP).root();
        assertTrue(
            offences(root).getFirst().contains("field takes 6 arguments"),
            "an offence names the fragment and its count"
        );
    }

    @Test
    void reportsTheFragmentUnderThePlaceItWasWritten() {
        Path root = new GitFixture("fragments-located").write(TEMPLATE, OVER_THE_CAP).root();
        assertTrue(
            offences(root).getFirst().startsWith("src/main/resources/templates/fragments/field.html:4:"),
            "an offence names the file and the line the declaration is on"
        );
    }

    @Test
    void readsTheSpellingThatKeepsADocumentValidHtml() {
        String data = """
            <!DOCTYPE html>
            <html lang="en">
            <body>
            <div data-th-fragment="field(label, name, type, purpose, value, error)"></div>
            </body>
            </html>
            """;
        Path root = new GitFixture("fragments-data-spelling").write(TEMPLATE, data).root();
        assertEquals(1, offences(root).size(), "the data spelling declares a fragment like the prefixed one");
    }

    @Test
    void readsNoFragmentOutOfAComment() {
        String commented = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <!-- th:fragment="field(label, name, type, purpose, value, error)" -->
            </body>
            </html>
            """;
        Path root = new GitFixture("fragments-commented").write(TEMPLATE, commented).root();
        assertTrue(Verdicts.clean(findings(root)), "a fragment named in a comment declares nothing");
    }

    @Test
    void readsNothingOutOfAnAttributeOfAnotherName() {
        String other = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div th:replace="~{fragments/field :: field(a, b, c, d, e, f)}"></div>
            </body>
            </html>
            """;
        Path root = new GitFixture("fragments-invocation").write(TEMPLATE, other).root();
        assertTrue(Verdicts.clean(findings(root)), "calling a fragment is not declaring one");
    }

    @Test
    void readsAFragmentDeclaredOnAnElementThatClosesItself() {
        String standalone = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <input th:fragment="field(label, name, type, purpose, value, error)" />
            </body>
            </html>
            """;
        Path root = new GitFixture("fragments-standalone").write(TEMPLATE, standalone).root();
        assertEquals(1, offences(root).size(), "an element that closes itself declares a fragment too");
    }

    @Test
    void readsNothingOutOfMarkupNoEngineCouldRead() {
        String unreadable = """
            <html><body><div th:fragment="field(a, b, c, d, e, f)" class="open></div>
            """;
        Path root = new GitFixture("fragments-unreadable").write(TEMPLATE, unreadable).root();
        assertTrue(Verdicts.clean(findings(root)), "an unreadable file is template-parse's finding to make");
    }

    private static List<String> offences(Path root) {
        return Verdicts.offences(findings(root), "more arguments than a callable may");
    }

    private static List<Findings> findings(Path root) {
        return new TemplateFragmentCheck(root, RESOURCES).findings();
    }
}
