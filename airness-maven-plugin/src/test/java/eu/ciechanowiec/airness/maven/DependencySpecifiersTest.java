package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import eu.ciechanowiec.airness.governance.Findings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DependencySpecifiersTest {

    private static final String MANIFEST = NodeManifests.MANIFEST;
    private static final String WORKSPACE = PnpmWorkspace.FILE;

    @TempDir
    private Path module;

    @ParameterizedTest
    @ValueSource(
        strings = {
            "1.2.3", "1.2.3-rc.1", "1.2.3+build.7", "=1.2.3", "v1.2.3", "npm:other@1.2.3", "npm:@scope/other@1.2.3",
            "workspace:*", "workspace:^", "link:.", "file:../library", "catalog:", "github:owner/project",
            "owner/project", "https://example.invalid/package.tgz"
        }
    )
    void acceptsAnExactVersionAndEverySpecifierThatNamesNoRegistryVersion(String specifier) {
        assertEquals(List.of(), this.offences(MANIFEST, dependency("dependencies", specifier)));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "^1.2.3", "~1.2.3", "*", "", "latest", "1.x", "1.2", "1", ">=1.2.3", "1.2.3 || 1.2.4", "1.2.3 - 1.3.0",
            "npm:other@^1.2.3", "npm:other", "npm:@scope/other"
        }
    )
    void refusesARangeATagAndAnAliasWithoutAnExactVersion(String specifier) {
        assertEquals(
            List.of(MANIFEST + ": devDependencies left-pad asks for " + specifier + "; name the exact version"),
            this.offences(MANIFEST, dependency("devDependencies", specifier))
        );
    }

    @Test
    void readsOptionalDependenciesAndLeavesPeerDependenciesToTheirRange() {
        assertEquals(
            List.of(MANIFEST + ": optionalDependencies left-pad asks for ^1.0.0; name the exact version"),
            this.offences(
                MANIFEST,
                "{\"optionalDependencies\": {\"left-pad\": \"^1.0.0\"}, \"peerDependencies\": {\"react\": \"^19.0.0\"}}"
            )
        );
    }

    @Test
    void holdsEveryCatalogOfTheWorkspaceToAnExactVersion() {
        String workspace = """
            catalog:
              react: ^19.0.0
              zod: 4.1.0
            catalogs:
              legacy:
                react: 18
            minimumReleaseAge: 1440
            """;
        assertEquals(
            List.of(
                WORKSPACE + ": catalog react asks for ^19.0.0; name the exact version",
                WORKSPACE + ": catalogs.legacy react asks for 18; name the exact version"
            ),
            this.offences(WORKSPACE, workspace)
        );
    }

    @Test
    void leavesAFileAnotherCheckRefusesAsUnreadableToThatCheck() {
        assertEquals(List.of(), this.offences(MANIFEST, "{ not json"));
        assertEquals(List.of(), this.offences(WORKSPACE, "catalog: [unclosed"));
        assertEquals(List.of(), this.offences(WORKSPACE, "- a list, not settings"));
    }

    @Test
    void readsNoOtherFile() {
        assertEquals(List.of(), this.offences("tsconfig.json", dependency("dependencies", "^1.0.0")));
    }

    private static String dependency(String section, String specifier) {
        return "{\"" + section + "\": {\"left-pad\": \"" + specifier + "\"}}";
    }

    @SneakyThrows
    private List<String> offences(String name, String content) {
        Path file = Files.writeString(this.module.resolve(name), content);
        return new DependencySpecifiers(this.module, List.of(file)).findings().stream()
            .map(Findings::offences)
            .flatMap(List::stream)
            .toList();
    }
}
