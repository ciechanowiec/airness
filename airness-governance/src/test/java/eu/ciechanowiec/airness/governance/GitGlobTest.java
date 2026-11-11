package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * A {@code .gitignore} glob translates to the expression git matches it as, and a form the translation does not
 * model translates to nothing.
 */
class GitGlobTest {

    private static boolean matches(String glob, String path) {
        return GitGlob.regex(glob).orElseThrow().matcher(path).matches();
    }

    @Test
    void keepsAnAsteriskAndAQuestionMarkWithinOneSegment() {
        assertAll(
            () -> assertTrue(matches("*.log", "debug.log")),
            () -> assertFalse(matches("*.log", "logs/debug.log")),
            () -> assertTrue(matches("?.md", "a.md")),
            () -> assertFalse(matches("?.md", "ab.md"))
        );
    }

    @Test
    void crossesDirectoriesWithTwoAsterisksThatMakeUpASegment() {
        assertAll(
            () -> assertTrue(matches("**/build", "build")),
            () -> assertTrue(matches("**/build", "web/app/build")),
            () -> assertTrue(matches("logs/**", "logs/2026/debug.log")),
            () -> assertFalse(matches("logs/**", "logs")),
            () -> assertTrue(matches("a/**/b", "a/b")),
            () -> assertTrue(matches("a/**/b", "a/x/y/b")),
            () -> assertTrue(matches("**", "any/depth"))
        );
    }

    @Test
    void readsAnyOtherRunOfAsterisksAsOne() {
        assertAll(
            () -> assertTrue(matches("a**b", "axyb")),
            () -> assertFalse(matches("a**b", "ax/yb"))
        );
    }

    @Test
    void matchesABracketExpressionWithinOneSegment() {
        assertAll(
            () -> assertTrue(matches("[ab].txt", "a.txt")),
            () -> assertFalse(matches("[ab].txt", "c.txt")),
            () -> assertTrue(matches("[!ab].txt", "c.txt")),
            () -> assertFalse(matches("[^ab].txt", "a.txt")),
            () -> assertTrue(matches("[]].txt", "].txt")),
            () -> assertTrue(matches("[a-c].txt", "b.txt")),
            () -> assertFalse(matches("x[!a]y", "x/y"))
        );
    }

    @Test
    void makesTheCharacterAfterABackslashLiteral() {
        assertAll(
            () -> assertTrue(matches("\\*.txt", "*.txt")),
            () -> assertFalse(matches("\\*.txt", "a.txt")),
            () -> assertTrue(matches("trailing\\", "trailing\\"))
        );
    }

    @Test
    void translatesNoFormItDoesNotModel() {
        Optional<Pattern> none = Optional.empty();
        assertAll(
            () -> assertEquals(none, GitGlob.regex("[[:alpha:]].txt")),
            () -> assertEquals(none, GitGlob.regex("[ab")),
            () -> assertEquals(none, GitGlob.regex("[")),
            () -> assertEquals(none, GitGlob.regex("[a\\]].txt")),
            () -> assertEquals(none, GitGlob.regex("[z-a].txt"))
        );
    }

    @Test
    void callsAGlobLiteralOnlyWhenItHoldsNoWildcardAndNoEscape() {
        assertAll(
            () -> assertTrue(GitGlob.literal(".vscode/settings.json")),
            () -> assertFalse(GitGlob.literal("*.log")),
            () -> assertFalse(GitGlob.literal("a?")),
            () -> assertFalse(GitGlob.literal("[ab]")),
            () -> assertFalse(GitGlob.literal("notes\\ "))
        );
    }
}
