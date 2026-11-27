package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import eu.ciechanowiec.airness.governance.Findings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NodeLicensesTest {

    private static final List<String> PROPRIETARY = List.of("LicenseRef-Proprietary");

    @TempDir
    private Path module;

    @Test
    void acceptsAPrivateManifestThatRepeatsTheRootLicense() {
        assertEquals(
            List.of(), this.offences("{\"license\": \"LicenseRef-Proprietary\", \"private\": true}", PROPRIETARY)
        );
    }

    @Test
    void acceptsAPublishedManifestUnderAnOpenRootLicense() {
        assertEquals(List.of(), this.offences("{\"license\": \"MIT-0\"}", List.of("MIT-0")));
    }

    @Test
    void refusesAManifestWithoutALicense() {
        assertEquals(
            List.of("package.json declares no license; write \"license\": \"LicenseRef-Proprietary\""),
            this.offences("{\"private\": true}", PROPRIETARY)
        );
    }

    @Test
    void refusesEveryDeprecatedFormOfTheLicense() {
        String refusal = "package.json writes its license in the deprecated object form; write \"license\" as a string";
        assertEquals(List.of(refusal), this.offences("{\"license\": {\"type\": \"MIT\"}}", PROPRIETARY));
        assertEquals(List.of(refusal), this.offences("{\"licenses\": [{\"type\": \"MIT\"}]}", PROPRIETARY));
    }

    @Test
    void refusesALicenseTheRootPomDoesNotName() {
        assertEquals(
            List.of("package.json declares license MIT, but the root pom names Apache-2.0, MIT-0"),
            this.offences("{\"license\": \"MIT\"}", List.of("Apache-2.0", "MIT-0"))
        );
    }

    @Test
    void refusesAProprietaryManifestThatIsNotPrivateAsABoolean() {
        String refusal = "package.json declares the code proprietary but is not \"private\": true";
        assertEquals(List.of(refusal), this.offences("{\"license\": \"LicenseRef-Proprietary\"}", PROPRIETARY));
        assertEquals(
            List.of(refusal),
            this.offences("{\"license\": \"LicenseRef-Proprietary\", \"private\": \"true\"}", PROPRIETARY)
        );
        assertEquals(
            List.of(refusal),
            this.offences("{\"license\": \"LicenseRef-Proprietary\", \"private\": false}", PROPRIETARY)
        );
    }

    @Test
    void leavesTheManifestsAloneWhileTheRootPomNamesNoLicense() {
        assertEquals(List.of(), this.offences("{}", List.of()));
    }

    @SneakyThrows
    private List<String> offences(String manifest, List<String> root) {
        Path file = Files.writeString(this.module.resolve("package.json"), manifest);
        return new NodeLicenses(this.module, List.of(file), root).findings().stream()
            .map(Findings::offences)
            .flatMap(List::stream)
            .toList();
    }
}
