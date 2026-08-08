package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The root license rule rejects its two exact filenames under every casing without claiming similarly
 * named or nested files.
 */
class RootLicenseCheckTest {

    @Test
    void rejectsBothForbiddenNamesWithoutRegardToCase() {
        Path root = new GitFixture("root-license-forbidden")
            .write("LiCeNsE", "license\n")
            .write("license.Txt", "license\n")
            .root();
        assertEquals(
            List.of("LiCeNsE", "license.Txt"), offences(root),
            "filesystem casing must not change whether either exact name is forbidden"
        );
    }

    @Test
    void allowsOtherExtensionsAndNestedLicenseFiles() {
        Path root = new GitFixture("root-license-allowed")
            .write("LICENSE.md", "license\n")
            .write("docs/LICENSE", "license\n")
            .root();
        assertTrue(
            Verdicts.clean(new RootLicenseCheck(root).findings()),
            "only the two exact filenames beside the root pom are part of the rule"
        );
    }

    private static List<String> offences(Path root) {
        return Verdicts.offences(new RootLicenseCheck(root).findings(), "must not sit beside");
    }
}
