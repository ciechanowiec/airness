package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The writing rules of a commit message refuse a body paragraph cut across source lines, apart from a line
 * that opens a list item, an indented line and the trailer block that closes the message. Each spelling the
 * rule accepts or refuses is pinned, every list marker and CRLF endings included.
 */
class CommitParagraphRulesTest {

    private static final String HEADER = "feat(core): read the fixture configuration";
    private static final String PARAGRAPH = "A body paragraph runs onto a second source line, so join it into one: ";

    @Test
    void acceptsOneSourceLinePerParagraph() {
        assertEquals(
            List.of(), violations("The reader was slow.\n\nIt now caches the parsed file, so a reload is cheap."),
            "two paragraphs, each on one line, are what every viewer wraps once"
        );
    }

    @Test
    void rejectsAParagraphBrokenAcrossLines() {
        assertEquals(
            List.of(PARAGRAPH + "the parsed file, so a reload is cheap."),
            violations("The reader now caches\nthe parsed file, so a reload is cheap."),
            "a hard-wrapped paragraph is wrapped again by every narrower viewer"
        );
    }

    @Test
    void rejectsAParagraphBrokenWithCrlfEndings() {
        assertEquals(
            List.of(PARAGRAPH + "the parsed file."), violations("The reader now caches\r\nthe parsed file.\r\n"),
            "a CRLF break is a break"
        );
    }

    @Test
    void acceptsCrlfParagraphsSeparatedByABlankLine() {
        assertEquals(
            List.of(), violations("The reader was slow.\r\n\r\nIt now caches the parsed file.\r\n"),
            "a blank CRLF line separates paragraphs as a blank LF line does"
        );
    }

    @Test
    void acceptsEveryListItemMarkerAfterAParagraphLine() {
        assertAll(
            Stream.of("- ", "* ", "+ ", "1. ", "1) ", "12. ", "12) ")
                .map(
                    marker -> () -> assertEquals(
                        List.of(), violations("The change:\n" + marker + "caches the file\n" + marker + "reloads it"),
                        "a line opening with '" + marker + "' starts a list item of its own"
                    )
                )
        );
    }

    @Test
    void rejectsAMarkerThatNoSpaceFollows() {
        assertEquals(
            List.of(PARAGRAPH + "-Pextended runs the scans."),
            violations("The profile is named\n-Pextended runs the scans."),
            "a dash that opens a word is the text of a cut paragraph, not a list marker"
        );
    }

    @Test
    void rejectsAListItemWrappedOntoAnUnindentedLine() {
        assertEquals(
            List.of(PARAGRAPH + "file before it reloads."),
            violations("- The reader caches the parsed\nfile before it reloads."),
            "the text of a list item is one source line too"
        );
    }

    @Test
    void acceptsAnIndentedLineAfterAParagraphLine() {
        assertEquals(
            List.of(), violations("The build printed:\n    BUILD FAILURE\n    Total time: 3 s"),
            "an indented line quotes code or output, where the line breaks are the content"
        );
    }

    @Test
    void acceptsATrailerBlock() {
        assertEquals(
            List.of(),
            violations("The reader caches the file.\n\nRefs: #12\nReviewed-by: A Reviewer <a@example.invalid>"),
            "the closing trailer block holds one trailer per line by design"
        );
    }

    @Test
    void acceptsATrailerBlockWithAnIndentedContinuation() {
        assertEquals(
            List.of(), violations("The reader caches the file.\n\nRefs: #12\n  #14\nReviewed-by: A Reviewer"),
            "a trailer continues on an indented line"
        );
    }

    @Test
    void acceptsABodyThatIsOnlyATrailerBlock() {
        assertEquals(
            List.of(), violations("Refs: #12\nReviewed-by: A Reviewer"),
            "a body of trailers alone is still the closing trailer block"
        );
    }

    @Test
    void rejectsAWrappedParagraphBeforeATrailerBlock() {
        assertEquals(
            List.of(PARAGRAPH + "the parsed file."),
            violations("The reader now caches\nthe parsed file.\n\nRefs: #12\nReviewed-by: A Reviewer"),
            "a trailer block excuses its own lines and none above it"
        );
    }

    @Test
    void rejectsATrailerShapedLineInsideAParagraph() {
        assertEquals(
            List.of(PARAGRAPH + "Note: the cache is per process."),
            violations("The reader now caches the file.\nNote: the cache is per process.\n\nIt reloads on change."),
            "a Key: value line counts as a trailer only in the closing paragraph"
        );
    }

    @Test
    void rejectsAClosingParagraphThatOnlyOpensWithATrailer() {
        assertEquals(
            List.of(PARAGRAPH + "the parsed file on start."),
            violations("The reader caches the file.\n\nCache: the reader now loads\nthe parsed file on start."),
            "a closing paragraph is a trailer block only when every line of it is a trailer"
        );
    }

    private static List<String> violations(String body) {
        return CommitProseRules.validate(new CommitMessage(HEADER, body));
    }
}
