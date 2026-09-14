package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The rules that forbid the two tags read a file rather than its syntax tree, so a fixture that spelled
 * a tag out would be a file naming that tag, and this build would report it here. The tag is assembled
 * when the fixture is read instead, which keeps the rules enforced everywhere rather than suppressed for
 * the one file that has to contain what they refuse.
 */
class JavadocTagBanCheckstyleTest {

    private static final String AUTHOR = "AirnessJavadocNamesNoAuthor";
    private static final String VERSION = "AirnessJavadocNamesNoVersion";
    private static final String MARKER = "TAG";
    private static final String DOCUMENTED = """
        /**
         * Does a thing.
         *
         * TAG
         */
        class Sample {
        }
        """;

    @Test
    void reportsAnAuthorTag(@TempDir Path directory) {
        String source = DOCUMENTED.replace(MARKER, "@author Someone");

        assertEquals(1, CheckstyleRule.findings(directory, source, AUTHOR), "the tag begins a Javadoc line");
    }

    @Test
    void reportsAVersionTag(@TempDir Path directory) {
        String source = DOCUMENTED.replace(MARKER, "@version 1.0.0");

        assertEquals(1, CheckstyleRule.findings(directory, source, VERSION), "the tag begins a Javadoc line");
    }

    @Test
    void acceptsJavadocThatNamesNeither(@TempDir Path directory) {
        String source = DOCUMENTED.replace(MARKER, "@param value what it does it to");

        assertEquals(0, CheckstyleRule.findings(directory, source, AUTHOR), "no author tag is present");
        assertEquals(0, CheckstyleRule.findings(directory, source, VERSION), "no version tag is present");
    }

    @Test
    void leavesProseAndCommentedLinesAlone(@TempDir Path directory) {
        String source = DOCUMENTED.replace(MARKER, "Explains that the @author tag is refused, as @version is")
            .replace("class Sample {", "class Sample {\n    // @author Someone");

        assertEquals(0, CheckstyleRule.findings(directory, source, AUTHOR), "a tag has to begin the line");
        assertEquals(0, CheckstyleRule.findings(directory, source, VERSION), "a tag has to begin the line");
    }
}
