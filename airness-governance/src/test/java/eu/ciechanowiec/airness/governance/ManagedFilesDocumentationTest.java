package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * The policy table of the README's managed-files section names every path the generic, Java and node
 * manifests declare, so a file added to a manifest is documented in the same change rather than left for a
 * reader to discover in the jar. A stack layer outside the core documents its files in its own section.
 */
class ManagedFilesDocumentationTest {

    private static final Path MANIFESTS = Path.of("airness-assets/src/main/resources/airness/manifest");
    private static final String POLICIES = "Each managed path has one of the following policies:";
    private static final String TABLE_END = "|===";
    private static final String COMMENT = "#";

    @Test
    @SneakyThrows
    void namesEveryManifestPathInThePolicyTable() {
        String readme = Files.readString(SelfModules.repository().resolve("README.adoc"));
        int start = readme.indexOf(POLICIES);
        int end = readme.indexOf(TABLE_END, readme.indexOf(TABLE_END, start) + TABLE_END.length());
        String table = readme.substring(start, end);
        assertEquals(
            List.of(),
            Stream.of("generic.tsv", "java.tsv", "node.tsv").flatMap(ManagedFilesDocumentationTest::paths)
                .filter(path -> !table.contains('`' + path + '`'))
                .distinct()
                .toList(),
            "each manifest path appears, in backticks, in the README's managed-files policy table"
        );
    }

    @SneakyThrows
    private static Stream<String> paths(String manifest) {
        return Files.readAllLines(SelfModules.repository().resolve(MANIFESTS).resolve(manifest)).stream()
            .map(String::strip)
            .filter(line -> !line.isEmpty() && !line.startsWith(COMMENT))
            .map(line -> line.split("\t", -1)[0]);
    }
}
