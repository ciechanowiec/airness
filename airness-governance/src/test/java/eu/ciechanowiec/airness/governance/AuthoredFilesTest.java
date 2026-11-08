package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The committable files of a tree are set apart from the ones a generator owns, whichever glob names them.
 */
class AuthoredFilesTest {

    private static final String CONTENT = "Text.\n";

    @Test
    void setsApartTheFilesAGeneratedGlobMatches() {
        GitFixture fixture = new GitFixture("authored-files")
            .write("README.md", CONTENT)
            .write("src/generated-types.ts", CONTENT)
            .write("web/src/app/(admin)/page.tsx", CONTENT);
        fixture.write(".gitignore", "build/\n").write("build/output.txt", CONTENT);
        Path root = fixture.root();
        AuthoredFiles files = AuthoredFiles.of(
            root, List.of("src/generated-types.ts", "web/src/app/?admin?/**", "src/absent.ts")
        );
        assertAll(
            () -> assertEquals(4, files.tracked().size(), "an ignored file is no committable file"),
            () -> assertEquals(
                List.of(root.resolve(".gitignore"), root.resolve("README.md")),
                files.authored().stream().sorted().toList()
            ),
            () -> assertEquals(2L, files.generated())
        );
    }

    @Test
    void keepsEveryCommittableFileWhenNothingIsDeclaredGenerated() {
        Path root = new GitFixture("authored-none").write("README.md", CONTENT).root();
        AuthoredFiles files = AuthoredFiles.of(root, List.of());
        assertAll(
            () -> assertEquals(files.tracked(), files.authored()),
            () -> assertEquals(0L, files.generated())
        );
    }
}
