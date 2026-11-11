package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A {@code .gitignore} pattern matches the paths git matches it against, and two patterns share a path unless one of
 * them provably misses what the other, a literal, names.
 */
class GitIgnoreGlobTest {

    private static final String ROOT = ".gitignore";
    private static final String WEB = "web/.gitignore";

    private static IgnorePattern line(String file, String text) {
        return new IgnorePattern(file, 1, text, "");
    }

    private static boolean matches(String file, String text, String path, boolean isDirectory) {
        return GitIgnoreGlob.of(line(file, text)).orElseThrow().matches(path, isDirectory);
    }

    private static boolean share(String first, String second) {
        return GitIgnoreGlob.mayShareAPath(line(ROOT, first), line(ROOT, second));
    }

    @Test
    void matchesAnUnanchoredPatternAgainstTheLastSegmentAtEveryDepth() {
        assertAll(
            () -> assertTrue(matches(ROOT, "*.iml", "modules/core/core.iml", false)),
            () -> assertTrue(matches(WEB, "dist", "web/app/dist", true)),
            () -> assertFalse(matches(WEB, "dist", "api/dist", true))
        );
    }

    @Test
    void matchesAnAnchoredPatternRelativeToTheDirectoryOfItsFile() {
        assertAll(
            () -> assertTrue(matches(WEB, "build/out", "web/build/out", false)),
            () -> assertFalse(matches(WEB, "build/out", "build/out", false)),
            () -> assertTrue(matches(ROOT, "/out", "out", true)),
            () -> assertFalse(matches(ROOT, "/out", "web/out", true)),
            () -> assertTrue(matches(ROOT, "!.vscode/settings.json", ".vscode/settings.json", false))
        );
    }

    @Test
    void matchesAPatternWithATrailingSeparatorAgainstDirectoriesAlone() {
        assertAll(
            () -> assertTrue(matches(ROOT, "cache/", "cache", true)),
            () -> assertFalse(matches(ROOT, "cache/", "cache", false))
        );
    }

    @Test
    void readsNoPatternWhoseGlobItDoesNotModel() {
        assertEquals(Optional.empty(), GitIgnoreGlob.of(line(ROOT, "[[:digit:]].log")));
    }

    @Test
    void sharesNoPathWithALiteralItProvablyMisses() {
        assertAll(
            () -> assertFalse(share("*.iml", "!.vscode/settings.json")),
            () -> assertFalse(share("*.iml", "!/.idea/runConfigurations")),
            () -> assertFalse(share("*.tmp", "!keep.log")),
            () -> assertFalse(share("src/*.ts", "!keep.js")),
            () -> assertFalse(share("!keep.log", "*.tmp"))
        );
    }

    @Test
    void sharesAPathWithALiteralItMayMatch() {
        assertAll(
            () -> assertTrue(share("*.log", "!keep.log")),
            () -> assertTrue(share("src/*.ts", "!keep.ts")),
            () -> assertTrue(share("logs/**", "!keep.log")),
            () -> assertTrue(share(".vscode/", "!.vscode")),
            () -> assertTrue(share("cache/", "!src/cache"))
        );
    }

    @Test
    void sharesAPathWheneverNeitherPatternIsLiteralOrOneIsNotModelled() {
        assertAll(
            () -> assertTrue(share("*.log", "!**/keep*")),
            () -> assertTrue(share("[[:alpha:]]*", "!keep.log"))
        );
    }

    @Test
    void sharesNoPathWithALiteralOutsideTheDirectoryOfAnUnanchoredPattern() {
        assertFalse(GitIgnoreGlob.mayShareAPath(line(WEB, "*.log"), line(ROOT, "!api/keep.log")));
    }
}
