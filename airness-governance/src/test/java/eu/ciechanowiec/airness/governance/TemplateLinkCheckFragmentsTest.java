package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * How a fragment expression is held to the same rule as a link expression, from the files that carry one.
 */
class TemplateLinkCheckFragmentsTest {

    private static final List<Path> RESOURCES = List.of(Path.of("src", "main", "resources"));

    private static final String TEMPLATE = "src/main/resources/templates/fragments/picture.html";

    private static final String BEAN_INSIDE_THE_FRAGMENT = """
        <!DOCTYPE html>
        <html lang="en" xmlns:th="http://www.thymeleaf.org">
        <body>
        <div th:replace="~{fragments/status-pill :: pill(${@vocabulary.of(booking.status())}, ${tone})}"></div>
        </body>
        </html>
        """;

    private static final String BEAN_BESIDE_THE_FRAGMENT = """
        <!DOCTYPE html>
        <html lang="en" xmlns:th="http://www.thymeleaf.org">
        <body>
        <div th:with="label=${@vocabulary.of(booking.status())}"
             th:replace="~{fragments/status-pill :: pill(${label}, ${tone})}"></div>
        </body>
        </html>
        """;

    @Test
    void reportsABeanReachedForInsideAFragmentExpression() {
        Path root = new GitFixture("fragments-bean").write(TEMPLATE, BEAN_INSIDE_THE_FRAGMENT).root();
        assertEquals(1, offences(root).size(), "a bean is refused inside a fragment expression");
    }

    @Test
    void reportsTheElementUnderThePlaceItWasWritten() {
        Path root = new GitFixture("fragments-located").write(TEMPLATE, BEAN_INSIDE_THE_FRAGMENT).root();
        assertTrue(
            offences(root).getFirst().startsWith("src/main/resources/templates/fragments/picture.html:4:1:"),
            "an offence names the file, the line and the column the element is at"
        );
    }

    @Test
    void saysWhichAttributeReachesAndForWhat() {
        Path root = new GitFixture("fragments-named").write(TEMPLATE, BEAN_INSIDE_THE_FRAGMENT).root();
        assertTrue(
            offences(root).getFirst().contains("th:replace reaches for a bean inside a fragment expression"),
            "an offence names the attribute, what it reached for and where"
        );
    }

    @Test
    void saysWhereToAskForItInstead() {
        Path root = new GitFixture("fragments-repair").write(TEMPLATE, BEAN_INSIDE_THE_FRAGMENT).root();
        assertTrue(
            offences(root).getFirst().contains("Ask for it in a th:with beside this and hand the fragment the"),
            "an offence names the repair as well as the defect"
        );
    }

    @Test
    void leavesTheLinkRuleQuietOverAFragmentExpression() {
        Path root = new GitFixture("fragments-not-links").write(TEMPLATE, BEAN_INSIDE_THE_FRAGMENT).root();
        assertTrue(
            Verdicts.offences(findings(root), "will not read in one").isEmpty(),
            "a fragment expression is reported under its own rule and not under the link rule"
        );
    }

    @Test
    void reportsAStaticClassReachedForInsideAFragmentExpression() {
        String reached = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div th:insert="~{fragments/clock :: face(${T(java.time.Year).now()})}"></div>
            </body>
            </html>
            """;
        Path root = new GitFixture("fragments-static").write(TEMPLATE, reached).root();
        assertTrue(
            offences(root).getFirst().contains("th:insert reaches for a static class"),
            "a static class is refused inside a fragment expression"
        );
    }

    @Test
    void reportsAnInstantiationReachedForInsideAFragmentExpression() {
        String reached = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div th:replace="~{fragments/list :: items(${new java.util.ArrayList()})}"></div>
            </body>
            </html>
            """;
        Path root = new GitFixture("fragments-new").write(TEMPLATE, reached).root();
        assertTrue(
            offences(root).getFirst().contains("th:replace reaches for an instantiation"),
            "an instantiation is refused inside a fragment expression"
        );
    }

    @Test
    void leavesABeanAskedForBesideTheFragmentAlone() {
        Path root = new GitFixture("fragments-beside").write(TEMPLATE, BEAN_BESIDE_THE_FRAGMENT).root();
        assertTrue(Verdicts.clean(findings(root)), "asking first and handing the fragment a variable is the repair");
    }

    @Test
    void leavesAMessageExpressionInsideAFragmentExpressionAlone() {
        String message = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div th:replace="~{fragments/pill :: pill(#{status.label(${code}, ${tone})}, #{status.plain})}"></div>
            </body>
            </html>
            """;
        Path root = new GitFixture("fragments-message").write(TEMPLATE, message).root();
        assertTrue(Verdicts.clean(findings(root)), "a message expression is read inside a fragment expression");
    }

    @Test
    void readsAFragmentExpressionNestedInsideAnotherAsPartOfIt() {
        String nested = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div th:replace="~{layout/page :: page(${title}, 'wide', ~{:: #body})}"></div>
            </body>
            </html>
            """;
        Path root = new GitFixture("fragments-nested").write(TEMPLATE, nested).root();
        assertTrue(Verdicts.clean(findings(root)), "a nested expression handing over a body reaches for nothing");
    }

    @Test
    void reportsABeanReachedForInsideANestedFragmentExpression() {
        String nested = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div th:replace="~{layout/page :: page(${title}, ~{pill :: pill(${@vocabulary.of(code)})})}"></div>
            </body>
            </html>
            """;
        Path root = new GitFixture("fragments-nested-bean").write(TEMPLATE, nested).root();
        assertEquals(1, offences(root).size(), "a bean inside the nested expression is inside the outer one too");
    }

    @Test
    void leavesAnAddressInsideAQuotedLiteralAlone() {
        String literal = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div th:replace="~{fragments/contact :: line(${'mail@example.com'})}"></div>
            </body>
            </html>
            """;
        Path root = new GitFixture("fragments-literal").write(TEMPLATE, literal).root();
        assertTrue(Verdicts.clean(findings(root)), "an address inside a literal is not a bean");
    }

    @Test
    void leavesATemplateNameSpelledLikeARefusedWordAlone() {
        String path = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div th:replace="~{fragments/new :: pill(${code})}"></div>
            </body>
            </html>
            """;
        Path root = new GitFixture("fragments-path").write(TEMPLATE, path).root();
        assertTrue(Verdicts.clean(findings(root)), "a template name is not an expression, so a word is only a word");
    }

    @Test
    void readsTheDataSpellingOfTheDialect() {
        String data = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <div data-th-replace="~{fragments/status-pill :: pill(${@vocabulary.of(code)})}"></div>
            </body>
            </html>
            """;
        Path root = new GitFixture("fragments-data").write(TEMPLATE, data).root();
        assertTrue(
            offences(root).getFirst().contains("data-th-replace reaches for a bean"),
            "the spelling a document uses to stay valid HTML5 is read as the dialect"
        );
    }

    private static List<String> offences(Path root) {
        return Verdicts.offences(findings(root), "Fragment expressions that reach");
    }

    private static List<Findings> findings(Path root) {
        return new TemplateLinkCheck(root, RESOURCES).findings();
    }
}
