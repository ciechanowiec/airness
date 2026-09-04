package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateLinkCheckTest {

    private static final List<Path> RESOURCES = List.of(Path.of("src", "main", "resources"));

    private static final String TEMPLATE = "src/main/resources/templates/fragments/picture.html";

    private static final String BEAN_INSIDE_THE_LINK = """
        <!DOCTYPE html>
        <html lang="en" xmlns:th="http://www.thymeleaf.org">
        <body>
        <img th:src="@{${@artwork.thumbnail(code)}}" alt="" />
        </body>
        </html>
        """;

    private static final String BEAN_BESIDE_THE_LINK = """
        <!DOCTYPE html>
        <html lang="en" xmlns:th="http://www.thymeleaf.org">
        <body>
        <img th:with="drawn=${@artwork.thumbnail(code)}" th:src="@{${drawn}}" alt="" />
        </body>
        </html>
        """;

    @Test
    void reportsABeanReachedForInsideALink() {
        Path root = new GitFixture("links-bean").write(TEMPLATE, BEAN_INSIDE_THE_LINK).root();
        assertEquals(1, offences(root).size(), "a bean is refused inside a link expression");
    }

    @Test
    void leavesABeanAskedForBesideTheLinkAlone() {
        Path root = new GitFixture("links-beside").write(TEMPLATE, BEAN_BESIDE_THE_LINK).root();
        assertTrue(Verdicts.clean(findings(root)), "asking first and handing the link a variable is the repair");
    }

    @Test
    void saysWhichAttributeReachesAndForWhat() {
        Path root = new GitFixture("links-named").write(TEMPLATE, BEAN_INSIDE_THE_LINK).root();
        assertTrue(
            offences(root).getFirst().contains("th:src reaches for a bean"),
            "an offence names the attribute and what it reached for"
        );
    }

    @Test
    void saysWhereToAskForItInstead() {
        Path root = new GitFixture("links-repair").write(TEMPLATE, BEAN_INSIDE_THE_LINK).root();
        assertTrue(
            offences(root).getFirst().contains("Ask for it in a th:with beside this"),
            "an offence names the repair as well as the defect"
        );
    }

    @Test
    void reportsTheElementUnderThePlaceItWasWritten() {
        Path root = new GitFixture("links-located").write(TEMPLATE, BEAN_INSIDE_THE_LINK).root();
        assertTrue(
            offences(root).getFirst().startsWith("src/main/resources/templates/fragments/picture.html:4:"),
            "an offence names the file and the line the element is on"
        );
    }

    @Test
    void reportsAStaticClassReachedForInsideALink() {
        String reached = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <a th:href="@{${T(java.lang.System).getenv('HOME')}}">Home</a>
            </body>
            </html>
            """;
        Path root = new GitFixture("links-static").write(TEMPLATE, reached).root();
        assertEquals(1, offences(root).size(), "a static class is refused inside a link expression");
    }

    @Test
    void reportsAnInstantiationReachedForInsideALink() {
        String reached = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <a th:href="@{${new java.lang.String(code)}}">Room</a>
            </body>
            </html>
            """;
        Path root = new GitFixture("links-instantiation").write(TEMPLATE, reached).root();
        assertEquals(1, offences(root).size(), "an instantiation is refused inside a link expression");
    }

    @Test
    void leavesAPathSegmentSpelledLikeAWordThatIsRefusedAlone() {
        String address = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <a th:href="@{/rooms/new}">Register a room</a>
            </body>
            </html>
            """;
        Path root = new GitFixture("links-address").write(TEMPLATE, address).root();
        assertTrue(Verdicts.clean(findings(root)), "an address is not an expression, so a segment is only a segment");
    }

    @Test
    void readsAPathVariableWithoutLosingWhereTheLinkCloses() {
        String nested = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <a th:href="@{/rooms/{reference}(reference=${room.reference()})}">Kepler Hall</a>
            </body>
            </html>
            """;
        Path root = new GitFixture("links-path-variable").write(TEMPLATE, nested).root();
        assertTrue(Verdicts.clean(findings(root)), "a link nests braces of its own and reaches for nothing here");
    }

    @Test
    void leavesWhatIsWrittenAfterTheLinkAlone() {
        String argument = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <a th:replace="~{:: link('Rooms', @{/rooms}, ${@sections.current()})}"></a>
            </body>
            </html>
            """;
        Path root = new GitFixture("links-argument").write(TEMPLATE, argument).root();
        assertTrue(offences(root).isEmpty(), "what follows the brace that closes a link belongs to its caller");
    }

    @Test
    void readsASelectionExpressionInsideALink() {
        String selection = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <a th:href="@{*{@sections.current()}}">Here</a>
            </body>
            </html>
            """;
        Path root = new GitFixture("links-selection").write(TEMPLATE, selection).root();
        assertEquals(1, offences(root).size(), "a selection expression is read like a variable one");
    }

    @Test
    void readsTheSpellingThatKeepsADocumentValidHtml() {
        String data = """
            <!DOCTYPE html>
            <html lang="en">
            <body>
            <a data-th-href="@{${@sections.current()}}">Here</a>
            </body>
            </html>
            """;
        Path root = new GitFixture("links-data-spelling").write(TEMPLATE, data).root();
        assertEquals(1, offences(root).size(), "the data spelling carries a link like the prefixed one");
    }

    @Test
    void leavesAnAttributeNoDialectReadsAlone() {
        String plain = """
            <!DOCTYPE html>
            <html lang="en">
            <body>
            <a data-note="@{${@sections.current()}}">Here</a>
            </body>
            </html>
            """;
        Path root = new GitFixture("links-plain-attribute").write(TEMPLATE, plain).root();
        assertTrue(Verdicts.clean(findings(root)), "an attribute no engine evaluates carries no link expression");
    }

    @Test
    void reportsAnElementOnceHoweverManyOfItsLinksReach() {
        String several = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <a th:href="@{${@sections.current()}}" th:src="@{${@artwork.mark(name)}}">Here</a>
            </body>
            </html>
            """;
        Path root = new GitFixture("links-several").write(TEMPLATE, several).root();
        assertEquals(1, offences(root).size(), "an element is reported once because the repair is one");
    }

    @Test
    void readsNothingOutOfALinkNoDocumentCloses() {
        String unclosed = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <a th:href="@{${@sections.current()}">Here</a>
            </body>
            </html>
            """;
        Path root = new GitFixture("links-unclosed").write(TEMPLATE, unclosed).root();
        assertTrue(Verdicts.clean(findings(root)), "a link nobody closed is not a link this rule can read");
    }

    @Test
    void readsNothingOutOfAComment() {
        String commented = """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            <!-- <img th:src="@{${@artwork.thumbnail(code)}}" alt="" /> -->
            </body>
            </html>
            """;
        Path root = new GitFixture("links-commented").write(TEMPLATE, commented).root();
        assertTrue(Verdicts.clean(findings(root)), "an element written in a comment reaches for nothing");
    }

    @Test
    void readsNothingOutOfMarkupNoEngineCouldRead() {
        String unreadable = """
            <html><body><img th:src="@{${@artwork.thumbnail(code)}}" class="open></body>
            """;
        Path root = new GitFixture("links-unreadable").write(TEMPLATE, unreadable).root();
        assertTrue(Verdicts.clean(findings(root)), "an unreadable file is template-parse's finding to make");
    }

    private static List<String> offences(Path root) {
        return Verdicts.offences(findings(root), "will not read in one");
    }

    private static List<Findings> findings(Path root) {
        return new TemplateLinkCheck(root, RESOURCES).findings();
    }
}
