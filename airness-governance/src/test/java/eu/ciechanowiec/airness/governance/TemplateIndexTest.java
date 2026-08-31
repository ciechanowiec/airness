package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What the markup of a module declares, read once so that a name written in one document can be
 * answered from another. A name is matched by the tail of a path rather than by a directory this code
 * names, so a project that keeps its templates somewhere else is still read.
 */
class TemplateIndexTest {

    private static final List<Path> RESOURCES = List.of(Path.of("src", "main", "resources"));

    private static final String USUAL = "src/main/resources/templates/layout/page.html";

    private static final String ELSEWHERE = "src/main/resources/views/layout/page.html";

    private static final String DOCUMENT = """
        <!DOCTYPE html>
        <html lang="en" xmlns:th="http://www.thymeleaf.org">
        <body>
        <div th:fragment="page(title, section, content)">Page</div>
        <div th:fragment="footer">Footer</div>
        </body>
        </html>
        """;

    private static final String PLAIN = """
        <!DOCTYPE html>
        <html lang="en"><body><p>Nothing declared here</p></body></html>
        """;

    @Test
    void answersATemplateNameKeptUnderTheUsualPrefix() {
        assertTrue(index("index-usual", USUAL).template("layout/page").isPresent(), "the tail of the path");
    }

    @Test
    void answersATemplateNameKeptUnderAnotherPrefix() {
        assertTrue(
            index("index-elsewhere", ELSEWHERE).template("layout/page").isPresent(),
            "a project that keeps its templates elsewhere is read the same way"
        );
    }

    @Test
    void answersNoTemplateForANameNothingCarries() {
        assertTrue(index("index-absent", USUAL).template("layout/frame").isEmpty(), "nothing carries the name");
    }

    @Test
    void answersNoTemplateForAnEmptyName() {
        assertTrue(index("index-empty", USUAL).template("").isEmpty(), "an empty name reaches nothing");
    }

    @Test
    void readsANameWrittenInSingleQuotes() {
        assertTrue(index("index-single", USUAL).template("'layout/page'").isPresent(), "the quotes are not the name");
    }

    @Test
    void readsANameWrittenInDoubleQuotes() {
        assertTrue(index("index-double", USUAL).template("\"layout/page\"").isPresent(), "either quotation");
    }

    @Test
    void readsANameThatMerelyOpensWithAQuote() {
        assertTrue(index("index-half", USUAL).template("'layout/page").isEmpty(), "one quote is part of the name");
    }

    @Test
    void countsTheArgumentsAFragmentDeclares() {
        TemplateIndex index = index("index-arity", USUAL);
        Path document = index.template("layout/page").orElseThrow();
        assertEquals(3, index.fragment(document, "page").orElseThrow(), "the declaration takes three");
    }

    @Test
    void countsNoArgumentsForAFragmentDeclaringNoList() {
        TemplateIndex index = index("index-bare", USUAL);
        Path document = index.template("layout/page").orElseThrow();
        assertEquals(0, index.fragment(document, "footer").orElseThrow(), "a fragment may take none");
    }

    @Test
    void declaresNoFragmentOfANameTheDocumentNeverWrote() {
        TemplateIndex index = index("index-unnamed", USUAL);
        Path document = index.template("layout/page").orElseThrow();
        assertTrue(index.fragment(document, "header").isEmpty(), "the document declares no such fragment");
    }

    @Test
    void holdsADocumentThatDeclaresNoFragmentAtAll() {
        Path root = new GitFixture("index-plain").write(USUAL, PLAIN).root();
        TemplateIndex index = new TemplateIndex(root, RESOURCES);
        assertTrue(index.template("layout/page").isPresent(), "a page is a template whether or not it declares one");
    }

    @Test
    void asksNothingOfADocumentThatDeclaredNothing() {
        Path root = new GitFixture("index-plain-fragment").write(USUAL, PLAIN).root();
        TemplateIndex index = new TemplateIndex(root, RESOURCES);
        assertTrue(index.fragment(Path.of(USUAL), "page").isEmpty(), "nothing was declared to find");
    }

    @Test
    void countsEveryDocumentItRead() {
        assertEquals(1, index("index-scanned", USUAL).scanned(), "one markup resource was shipped");
    }

    @Test
    void namesEveryDocumentItRead() {
        assertFalse(index("index-documents", USUAL).documents().isEmpty(), "the documents are named");
    }

    private static TemplateIndex index(String name, String where) {
        Path root = new GitFixture(name).write(where, DOCUMENT).root();
        return new TemplateIndex(root, RESOURCES);
    }
}
