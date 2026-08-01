package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The typography scan reads every committable file, honours the exemptions it is given, and reports an
 * exemption that excluded nothing.
 *
 * <p>The banned glyph is built from its code point rather than typed, because this file is itself a
 * tracked file and the very scan under test would find it here. A fixture that fails the gate it tests
 * is a fixture that cannot be committed.
 */
class TypographyScanCheckTest {

    private static final int EM_DASH = 0x2014;
    private static final List<String> VENDORED = List.of("vendor/");
    private static final String PLAIN = "A line with an ASCII hyphen - and nothing else.\n";
    private static final String OFFENDING = "A line with an em dash " + Character.toString(EM_DASH) + " in it.\n";

    @Test
    void passesOverATreeThatUsesPlainAscii() {
        Path root = new GitFixture("typography-clean")
            .write("README.md", PLAIN)
            .write("vendor/theme.css", PLAIN)
            .root();
        assertTrue(
            Verdicts.clean(new TypographyScanCheck(root, VENDORED).findings()),
            "plain typography and an exemption that earned its keep"
        );
    }

    @Test
    void reportsABannedCodePointInAScannedFile() {
        Path root = new GitFixture("typography-violation")
            .write("README.md", OFFENDING)
            .write("vendor/theme.css", PLAIN)
            .root();
        List<String> offences = Verdicts.offences(
            new TypographyScanCheck(root, VENDORED).findings(), "Banned typography"
        );
        assertEquals(1, offences.size(), "one banned code point in one scanned file");
        assertTrue(offences.getFirst().contains("README.md"), "and the offence names the file: " + offences);
    }

    @Test
    void leavesAnExemptPathUnreadAndSaysWhatThatCost() {
        Path root = new GitFixture("typography-exempt")
            .write("README.md", PLAIN)
            .write("vendor/theme.css", OFFENDING)
            .root();
        TypographyScanCheck check = new TypographyScanCheck(root, VENDORED);
        assertTrue(Verdicts.clean(check.findings()), "the offending file is under the exempt prefix");
        assertEquals(1L, check.skipped().get("vendor/"), "and the count is what puts the cost of that on the record");
    }

    @Test
    void reportsAnExemptionThatExcludedNothing() {
        Path root = new GitFixture("typography-stale").write("README.md", PLAIN).root();
        assertEquals(
            List.of("vendor/"),
            Verdicts.offences(new TypographyScanCheck(root, VENDORED).findings(), "exclusion prefix"),
            "a prefix that excludes nothing names a path that moved or went, and hides the next thing it does exclude"
        );
    }
}
