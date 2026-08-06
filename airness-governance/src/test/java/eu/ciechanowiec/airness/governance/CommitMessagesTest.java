package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The history parser preserves every line stored in the commit object.
 */
class CommitMessagesTest {

    private static final String HEADER = "refactor(config): split the effective config merge";
    private static final String TEMPLATE = """
        # Please enter the commit message for your changes. Lines starting
        # with '#' will be ignored, and an empty message aborts the commit.
        #
        # On branch main
        # Changes to be committed:
        #\tmodified:   pom.xml
        """;

    @Test
    void readsTheHeaderAndTheBody() {
        CommitMessage message = CommitMessages.parse(HEADER + "\n\nThe merge grew past the cap.\n");
        assertEquals(HEADER, message.header(), "the header is the first line");
        assertEquals("The merge grew past the cap.", message.body(), "the body is what follows");
    }

    @Test
    void storedCommentLinesRemainPartOfTheBody() {
        CommitMessage message = CommitMessages.parse(HEADER + "\n\n" + TEMPLATE);
        assertEquals(HEADER, message.header(), "the header survives the comments");
        assertEquals(TEMPLATE.strip(), message.body(), "stored lines are history, whatever prefix they use");
    }

    @Test
    void aRealBodyAndStoredCommentsBothSurvive() {
        CommitMessage message = CommitMessages.parse(HEADER + "\n\nThe merge grew past the cap.\n\n" + TEMPLATE);
        assertTrue(message.body().startsWith("The merge grew past the cap.\n\n# Please enter"));
    }

    @Test
    void aHeaderWithoutABodyParses() {
        CommitMessage message = CommitMessages.parse(HEADER);
        assertEquals(HEADER, message.header(), "a lone header is the header");
        assertEquals("", message.body(), "and it carries no body");
    }
}
