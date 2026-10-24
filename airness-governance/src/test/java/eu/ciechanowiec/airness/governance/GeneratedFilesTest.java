package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that generated files are placed at every site carrying the capability that declares them.
 */
class GeneratedFilesTest {

    @SneakyThrows
    private static GeneratedFiles declaring(Path classpath, Capability capability, String declarations) {
        Path file = classpath.resolve("airness/generated/" + capability.id() + ".txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, declarations);
        URL[] urls = {classpath.toUri().toURL()};
        return new GeneratedFiles(new URLClassLoader(urls, null));
    }

    @Test
    void placesEachDeclarationAtTheSitesOfItsCapabilityAndLocation(@TempDir Path classpath) {
        GeneratedFiles files = declaring(
            classpath, Capability.PAYLOAD, "# Payload output\nsrc/payload-types.ts\tNODE_PROJECT\nroot-schema.json\n"
        );
        List<AssetSite> sites = List.of(
            AssetSite.root(Set.of(Capability.NODE, Capability.PAYLOAD)),
            new AssetSite("cms", AssetLocation.NODE_PROJECT, Set.of(Capability.NODE, Capability.PAYLOAD)),
            new AssetSite("web", AssetLocation.NODE_PROJECT, Set.of(Capability.NODE))
        );
        assertEquals(
            List.of("root-schema.json", "cms/src/payload-types.ts"), files.paths(sites),
            "a node-project declaration lands in each Payload project and nowhere else"
        );
    }

    @Test
    void declaresNothingForACapabilityWithoutADeclarationFile(@TempDir Path classpath) {
        GeneratedFiles files = declaring(classpath, Capability.PAYLOAD, "src/payload-types.ts\tNODE_PROJECT\n");
        assertEquals(
            List.of(), files.paths(List.of(AssetSite.root(Set.of(Capability.JAVA)))),
            "a repository without the declaring capability denies nothing"
        );
    }

    @Test
    void addsTheFilesAProjectDeclaresAfterTheLayers(@TempDir Path classpath) {
        GeneratedFiles files = declaring(classpath, Capability.GENERIC, "schema/api.json\n");
        List<AssetSite> sites = List.of(AssetSite.root(Set.of()));
        assertEquals(
            List.of("schema/api.json", "src/generated/Client.java"),
            files.paths(sites, List.of(" /src/generated/Client.java ", "schema/api.json", "")),
            "a project declaration adds to the set, read as repository-relative, and repeats nothing"
        );
    }

    @Test
    void refusesAMalformedDeclaration(@TempDir Path classpath) {
        GeneratedFiles files = declaring(classpath, Capability.GENERIC, "a.ts\tROOT\textra\n");
        List<AssetSite> sites = List.of(AssetSite.root(Set.of()));
        assertThrows(IllegalStateException.class, () -> files.paths(sites), "a harness bug is never a silent pass");
    }
}
