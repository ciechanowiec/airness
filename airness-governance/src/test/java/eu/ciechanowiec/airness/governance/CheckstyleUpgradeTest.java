package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckstyleUpgradeTest {

    private static final String COMMENT = "class Sample {\n    //missing space\n}\n";
    private static final String DOCUMENTED = """
        /**
         * Describes related members before more distant types.
         *
         * @see String
         * @see #value
         */
        class Sample {
            String value;
        }
        """;

    @Test
    void reportsMissingWhitespaceAfterACommentDelimiter(@TempDir Path directory) {
        List<CheckstyleConfigurationTest.Finding> findings = inspect(directory, COMMENT, "WhitespaceAfter");
        assertEquals(1, findings.size());
        assertEquals(2, findings.getFirst().line());
    }

    @Test
    void acceptsSpacedCommentsAndCommentLikeStringContents(@TempDir Path directory) {
        String source = COMMENT.replace("//missing", "// missing")
            .replace("}\n", "    String address = \"https://example.com\";\n}\n");
        assertTrue(inspect(directory, source, "WhitespaceAfter").isEmpty());
    }

    @Test
    void reportsASeeTagThatPlacesALocalMemberAfterAType(@TempDir Path directory) {
        List<CheckstyleConfigurationTest.Finding> findings = inspect(directory, DOCUMENTED, "JavadocSeeTagOrder");
        assertEquals(1, findings.size());
        assertEquals(5, findings.getFirst().line());
    }

    @Test
    void acceptsSeeTagsOrderedFromLocalMembersToTypes(@TempDir Path directory) {
        String source = DOCUMENTED.replace("@see String\n * @see #value", "@see #value\n * @see String");
        assertTrue(inspect(directory, source, "JavadocSeeTagOrder").isEmpty());
    }

    private static List<CheckstyleConfigurationTest.Finding> inspect(Path directory, String source, String rule) {
        CheckstyleConfigurationTest.Fixture fixture = new CheckstyleConfigurationTest.Fixture(
            "Sample.java", source, rule, 1
        );
        return CheckstyleConfigurationTest.inspect(directory, fixture, false).stream()
            .filter(finding -> finding.is(rule)).toList();
    }
}
