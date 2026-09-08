package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateMessageIndexTest {

    private static final List<Path> ROOTS = List.of(Path.of("src/main/resources"));

    @Test
    void readsProcessingAttributesAndIgnoresProseCommentsAndTestResources() {
        Path root = new GitFixture("literal-messages").write(
            "src/main/resources/templates/page.html", """
                <html><body>
                <!-- <p th:text="#{comment}"></p> -->
                <p th:text="#{room.name}" data-th-title="#{welcome(${name})}" title="#{example}">[[#{inline}]]</p>
                <p th:text="&apos;#{quoted.example}&apos;"></p>
                </body></html>
                """
        ).write("src/test/resources/templates/test.html", "<p th:text=\"#{test.key}\"></p>").root();
        List<TemplateMessageReference> references = new TemplateMessageIndex(root, ROOTS).references();
        assertEquals(
            List.of("room.name", "welcome"), references.stream().map(TemplateMessageReference::key).sorted().toList()
        );
        assertTrue(references.stream().allMatch(reference -> "templates/page.html".equals(reference.resource())));
        assertTrue(references.getFirst().location().startsWith("src/main/resources/templates/page.html:3:"));
    }

    @Test
    void respectsCustomResourceRootsAndSkipsFragmentDeclarations() {
        Path root = new GitFixture("literal-roots").write(
            "resources/views/page.html",
            "<p th:fragment=\"piece(title)\" th:text=\"#{caption}\"></p>"
        ).root();
        List<TemplateMessageReference> references = new TemplateMessageIndex(root, List.of(Path.of("resources")))
            .references();
        assertEquals("views/page.html", references.getFirst().resource());
        assertEquals(1, references.size());
    }
}
