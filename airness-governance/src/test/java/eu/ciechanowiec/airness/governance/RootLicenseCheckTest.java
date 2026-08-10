package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * The root license rule rejects its three exact filenames under every casing without claiming similarly
 * named or nested files.
 */
class RootLicenseCheckTest {

    @Test
    void rejectsAllForbiddenNamesWithoutRegardToCase() {
        Path root = new GitFixture("root-license-forbidden")
            .write("LiCeNsE", "license\n")
            .write("license.Txt", "license\n")
            .write("LICENSE.md", "license\n")
            .root();
        assertEquals(
            List.of("LICENSE.md", "LiCeNsE", "license.Txt"), offences(root),
            "filesystem casing must not change whether an exact name is forbidden"
        );
    }

    @Test
    void allowsUnlistedExtensionsAndNestedLicenseFiles() {
        Path root = new GitFixture("root-license-allowed")
            .write("LICENSE.adoc", "license\n")
            .write("docs/LICENSE", "license\n")
            .root();
        assertTrue(
            Verdicts.clean(new RootLicenseCheck(root).findings()),
            "only the three exact filenames beside the root pom are part of the rule"
        );
    }

    @Test
    @SneakyThrows
    void rejectsAForbiddenNameThatIsASymbolicLink() {
        Path root = new GitFixture("root-license-link").root();
        Files.createSymbolicLink(root.resolve("LICENSE"), Path.of("missing-target"));
        assertEquals(List.of("LICENSE"), offences(root));
    }

    private static List<String> offences(Path root) {
        return Verdicts.offences(new RootLicenseCheck(root).findings(), "must not sit beside");
    }
}
