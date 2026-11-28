package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The writing rules of a commit message refuse a semicolon in the header and the body, apart from one
 * inside a code span or on an indented line. Each spelling the rule accepts or refuses is pinned, a trailer
 * and CRLF endings included. {@link CommitParagraphRulesTest} pins the paragraph rule.
 */
class CommitProseRulesTest {

    private static final String HEADER = "feat(core): read the fixture configuration";
    private static final String SEMICOLON = "A semicolon outside a code span, where two sentences or a connecting word "
        + "belong: ";
    private static final String PARAGRAPH = "A body paragraph runs onto a second source line, so join it into one: ";

    @Test
    void rejectsASemicolonBetweenClauses() {
        assertEquals(
            List.of(SEMICOLON + "The reader was slow; it now caches the file."),
            violations("The reader was slow; it now caches the file."),
            "two clauses take two sentences or a connecting word"
        );
    }

    @Test
    void rejectsASemicolonInTheHeader() {
        CommitMessage message = new CommitMessage("build(deps): raise Guava; hold Checkstyle", "");
        assertEquals(
            List.of(SEMICOLON + "build(deps): raise Guava; hold Checkstyle"), CommitProseRules.validate(message),
            "the header is prose as much as the body is"
        );
    }

    @Test
    void rejectsASemicolonInATrailer() {
        assertEquals(
            List.of(SEMICOLON + "Refs: #12; #14"), violations("The reader caches the file.\n\nRefs: #12; #14"),
            "a trailer is exempt from the paragraph rule alone, not from the semicolon rule"
        );
    }

    @Test
    void acceptsASemicolonInsideACodeSpan() {
        assertEquals(
            List.of(), violations("The loop is now `for (;;)` and the call ends in `run();` as before."),
            "a semicolon in a code span belongs to the code"
        );
    }

    @Test
    void acceptsASemicolonInsideADoubleBacktickCodeSpan() {
        assertEquals(
            List.of(), violations("The template prints ``a `b`; c`` verbatim."),
            "a span opened by two backticks closes only at two, so the backticks inside it stay code"
        );
    }

    @Test
    void rejectsASemicolonBetweenTwoCodeSpans() {
        assertEquals(
            List.of(SEMICOLON + "It calls `open` first; `close` follows."),
            violations("It calls `open` first; `close` follows."),
            "the text between two spans is prose"
        );
    }

    @Test
    void rejectsASemicolonAfterABacktickThatNeverCloses() {
        assertEquals(
            List.of(SEMICOLON + "The ` sign stays; it is literal."), violations("The ` sign stays; it is literal."),
            "a backtick that never closes opens no span"
        );
    }

    @Test
    void acceptsASemicolonOnAnIndentedLine() {
        assertEquals(
            List.of(), violations("The build printed:\n\n    error: expected ';' after expression\n\tint a = 1;"),
            "a line indented by spaces or by a tab quotes code or output"
        );
    }

    @Test
    void rejectsASemicolonInACrlfBody() {
        assertEquals(
            List.of(SEMICOLON + "Second; paragraph."), violations("First paragraph.\r\n\r\nSecond; paragraph.\r\n"),
            "a CRLF ending changes nothing the rule reads"
        );
    }

    @Test
    void reportsBothRulesOfOneMessage() {
        assertEquals(
            List.of(SEMICOLON + "The reader was slow; it now", PARAGRAPH + "caches the file."),
            violations("The reader was slow; it now\ncaches the file."),
            "each rule names its own finding"
        );
    }

    private static List<String> violations(String body) {
        return CommitProseRules.validate(new CommitMessage(HEADER, body));
    }
}
