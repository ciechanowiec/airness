package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import eu.ciechanowiec.airness.governance.PackageManifest;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A directory's {@code package.json} is read for the scripts and dependencies an instruction file may name.
 */
class PackageManifestsTest {

    private static final String MANIFEST = "package.json";

    @TempDir
    private Path directory;

    @Test
    @SneakyThrows
    void readsTheScriptsAndEveryKindOfDependency() {
        Files.writeString(
            this.directory.resolve(MANIFEST),
            """
                {
                  "scripts": { "build": "tsc", "lint:arch": "depcruise src" },
                  "dependencies": { "react": "19.0.0" },
                  "devDependencies": { "@airness/node": "2.0.0" },
                  "peerDependencies": { "next": "16.0.0" },
                  "optionalDependencies": { "fsevents": "2.3.3" }
                }
                """
        );
        assertEquals(
            Optional.of(
                new PackageManifest(
                    Set.of("build", "lint:arch"), Set.of("react", "@airness/node", "next", "fsevents")
                )
            ),
            PackageManifests.in(this.directory),
            "every script name and every declared package is read"
        );
    }

    @Test
    void readsNothingWhereTheDirectoryHoldsNoManifest() {
        assertEquals(Optional.empty(), PackageManifests.in(this.directory), "a directory without one has none");
    }

    @Test
    @SneakyThrows
    void readsAManifestWithoutScriptsOrDependenciesAsDeclaringNone() {
        Files.writeString(this.directory.resolve(MANIFEST), "{ \"name\": \"bare\", \"scripts\": [] }\n");
        assertEquals(
            Optional.of(new PackageManifest(Set.of(), Set.of())), PackageManifests.in(this.directory),
            "a missing or malformed section declares nothing"
        );
    }

    @Test
    @SneakyThrows
    void failsOnAManifestThatIsNotAJsonObject() {
        Files.writeString(this.directory.resolve(MANIFEST), "[]\n");
        assertThrows(
            IllegalStateException.class, () -> PackageManifests.in(this.directory),
            "an unreadable manifest is a failure rather than a manifest declaring nothing"
        );
    }

    @Test
    @SneakyThrows
    void failsOnAManifestThatIsNotJson() {
        Files.writeString(this.directory.resolve(MANIFEST), "{ \"name\": \n");
        assertThrows(
            UncheckedIOException.class, () -> PackageManifests.in(this.directory),
            "an unparseable manifest is a failure rather than a manifest declaring nothing"
        );
    }
}
