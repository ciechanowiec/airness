package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the image check reads every committable image and names each broken one.
 */
class ImageAssetCheckTest {

    @Test
    void namesEveryBrokenImageInTheCommittableTree() {
        Path root = new GitFixture("images-broken")
            .write(".gitignore", "build/\n")
            .write("public/logo.png", ImageFixtures.png())
            .write("public/photo.jpg", ImageFixtures.withoutLast(ImageFixtures.jpeg(), 1))
            .write("docs/empty.gif", new byte[0])
            .write("build/stale.png", new byte[0])
            .write("notes.txt", "Not an image.\n")
            .root();
        ImageAssetCheck check = new ImageAssetCheck(root);
        assertEquals(
            List.of(
                Path.of("docs", "empty.gif") + ": empty file",
                Path.of("public", "photo.jpg") + ": truncated JPEG (missing EOI marker)"
            ),
            Verdicts.offences(check.findings(), "do not decode").stream().sorted().toList(),
            "each broken image is named once, and an ignored one is not read"
        );
        assertEquals(3, check.scanned(), "the three committable images are read and the ignored one is not");
    }

    @Test
    void passesATreeWithNoImage() {
        Path root = new GitFixture("images-none").write("README.md", "No images.\n").root();
        assertTrue(Verdicts.clean(new ImageAssetCheck(root).findings()), "a tree with no image has nothing to break");
    }
}
