package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What the markup of a module calls, which the index holds beside what the markup declares. A name is
 * kept from the call that writes it rather than from the document it resolves to, because a call
 * reaching nothing is already the finding of the rule over calls, and resolving the template half a
 * second time would give one question two answers.
 */
class TemplateIndexCallsTest {

    private static final List<Path> RESOURCES = List.of(Path.of("src", "main", "resources"));

    private static final String LISTING = "src/main/resources/templates/room/list.html";

    @Test
    void namesEveryFragmentACallWritesOut() {
        assertTrue(
            calling("index-called", "th:replace=\"~{layout/page :: footer}\"").called("footer"),
            "a call names the fragment it reaches"
        );
    }

    @Test
    void keepsNoNameFromACallOnAWholeTemplate() {
        assertFalse(
            calling("index-whole", "th:replace=\"~{layout/page}\"").called("page"),
            "a call reaching a whole template names no fragment of it"
        );
    }

    @Test
    void keepsNoNameFromAnAttributeThatCallsNothing() {
        assertFalse(
            calling("index-uncalled", "th:text=\"${footer}\"").called("footer"),
            "an attribute that reaches no fragment is not a call"
        );
    }

    @Test
    void keepsTheNameOfACallWrittenInTheDataSpelling() {
        assertTrue(
            calling("index-data", "data-th-insert=\"~{layout/page :: footer}\"").called("footer"),
            "a document staying valid HTML calls a fragment the same way"
        );
    }

    @Test
    void keepsTheNameOfACallThatNamesNoTemplate() {
        assertTrue(
            calling("index-local", "th:replace=\"~{this :: footer}\"").called("footer"),
            "a call reaching the document it was written in names the fragment like any other"
        );
    }

    @Test
    void namesAFragmentWrittenInsideAnotherCallsArguments() {
        assertTrue(
            calling("index-nested", "th:replace=\"~{layout/page :: page('Rooms', ~{:: footer})}\"")
                .called("footer"),
            "a fragment handed to another as an argument is reached by the value that hands it over"
        );
    }

    @Test
    void keepsNoNameFromASelectorWrittenAsAnArgument() {
        assertFalse(
            calling("index-selector", "th:replace=\"~{layout/page :: page(~{:: #body})}\"").called("#body"),
            "a selector reaches an element rather than a fragment, at any depth"
        );
    }

    private static TemplateIndex calling(String name, String call) {
        Path root = new GitFixture(name)
            .write(
                LISTING,
                """
                    <!DOCTYPE html>
                    <html lang="en" xmlns:th="http://www.thymeleaf.org">
                    <body><div %s>Listed</div></body>
                    </html>
                    """.formatted(call)
            )
            .root();
        return new TemplateIndex(root, RESOURCES);
    }
}
