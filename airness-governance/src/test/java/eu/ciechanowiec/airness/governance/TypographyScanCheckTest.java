package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * The typography scan reads every committable file, honours the exemptions it is given, and reports an
 * exemption that excluded nothing.
 *
 * <p>The banned glyph is built from its code point rather than typed, because this file is itself a
 * tracked file and the very scan under test would find it here. A fixture that fails the check it tests
 * is a fixture that cannot be committed.
 */
class TypographyScanCheckTest {

    private static final int EM_DASH = 0x2014;
    private static final String VENDOR = "vendor/";
    private static final String THEME = "vendor/theme.css";
    private static final String README = "README.md";
    private static final List<String> VENDORED = List.of(VENDOR);
    private static final String PLAIN = "A line with an ASCII hyphen - and nothing else.\n";
    private static final String OFFENDING = "A line with an em dash " + Character.toString(EM_DASH) + " in it.\n";

    @Test
    void passesOverATreeThatUsesPlainAscii() {
        Path root = new GitFixture("typography-clean")
            .write(README, PLAIN)
            .write(THEME, PLAIN)
            .root();
        assertTrue(
            Verdicts.clean(new TypographyScanCheck(root, VENDORED).findings()),
            "plain typography and an exemption that earned its keep"
        );
    }

    @Test
    void reportsABannedCodePointInAScannedFile() {
        Path root = new GitFixture("typography-violation")
            .write(README, OFFENDING)
            .write(THEME, PLAIN)
            .root();
        List<String> offences = Verdicts.offences(
            new TypographyScanCheck(root, VENDORED).findings(), "Banned typography"
        );
        assertEquals(1, offences.size(), "one banned code point in one scanned file");
        assertTrue(offences.getFirst().contains(README), "and the offence names the file: " + offences);
    }

    @Test
    void leavesAnExemptPathUnreadAndSaysWhatThatCost() {
        Path root = new GitFixture("typography-exempt")
            .write(README, PLAIN)
            .write(THEME, OFFENDING)
            .root();
        TypographyScanCheck check = new TypographyScanCheck(root, VENDORED);
        assertTrue(Verdicts.clean(check.findings()), "the offending file is under the exempt prefix");
        assertEquals(1L, check.skipped().get(VENDOR), "and the count is what puts the cost of that on the record");
    }

    @Test
    void doesNotLetAnExemptionReachAPathThatMerelySharesItsOpeningCharacters() {
        Path root = new GitFixture("typography-sibling")
            .write(THEME, PLAIN)
            .write("vendored-by-hand/theme.css", OFFENDING)
            .root();
        List<String> offences = Verdicts.offences(
            new TypographyScanCheck(root, VENDORED).findings(), "Banned typography"
        );
        assertEquals(1, offences.size(), "a prefix names a directory rather than a run of characters");
        assertTrue(
            offences.getFirst().contains("vendored-by-hand"),
            "and the sibling that only starts the same way stays in the scan: " + offences
        );
    }

    @Test
    void readsAnExemptionNamedTwiceAsIfItWereNamedOnce() {
        Path root = new GitFixture("typography-repeated")
            .write(README, PLAIN)
            .write(THEME, OFFENDING)
            .root();
        TypographyScanCheck check = new TypographyScanCheck(root, List.of(VENDOR, VENDOR));
        assertTrue(
            Verdicts.clean(check.findings()),
            "the prefixes arrive from a hand-written list, where naming one twice is a typo rather than a fault"
        );
        assertEquals(
            1L, check.skipped().get(VENDOR),
            "and a repeated prefix costs what a single one costs, rather than ending the scan with no verdict"
        );
    }

    @Test
    void countsOnlyTheFilesAnExemptionActuallyNames() {
        Path root = new GitFixture("typography-sibling-count")
            .write(THEME, PLAIN)
            .write("vendored-by-hand/theme.css", PLAIN)
            .root();
        assertEquals(
            1L, new TypographyScanCheck(root, VENDORED).skipped().get(VENDOR),
            "the cost an exemption puts on the record has to be the cost it actually incurred"
        );
    }

    @Test
    void reportsAnExemptionThatExcludedNothing() {
        Path root = new GitFixture("typography-stale").write(README, PLAIN).root();
        assertEquals(
            List.of(VENDOR),
            Verdicts.offences(new TypographyScanCheck(root, VENDORED).findings(), "exclusion prefix"),
            "a prefix that excludes nothing names a path that moved or went, and hides the next thing it does exclude"
        );
    }

    @Test
    @SneakyThrows
    void doesNotFollowASymbolicLinkOutsideTheRepository() {
        Path outside = Files.createTempFile("airness-typography-outside-", ".txt");
        Files.writeString(outside, OFFENDING);
        Path root = new GitFixture("typography-symlink").write(README, PLAIN).root();
        Files.createSymbolicLink(root.resolve("outside.txt"), outside);
        assertTrue(
            Verdicts.clean(new TypographyScanCheck(root, List.of()).findings()),
            "a repository scan reads the link itself and never content outside its root"
        );
    }
}
