package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * The agent guide's section on managed files names every path the generic, Java and node manifests declare, so a
 * file added to a manifest is documented for the agents in the same change rather than left for them to discover in
 * the jar. A stack layer outside the core documents its files in its own guide.
 */
class ManagedFilesDocumentationTest {

    private static final Path MANIFESTS = Path.of("airness-assets/src/main/resources/airness/manifest");
    private static final Path GUIDE = Path.of(
        "airness-assets/src/main/resources/airness/files/generic/.airness/agent-guide.md.asset"
    );
    private static final String SECTION = "### Managed Files";
    private static final String NEXT_SECTION = "\n### ";
    private static final String COMMENT = "#";

    @Test
    @SneakyThrows
    void namesEveryManifestPathInTheGuideSection() {
        String guide = Files.readString(SelfModules.repository().resolve(GUIDE));
        int start = guide.indexOf(SECTION);
        String section = guide.substring(start, guide.indexOf(NEXT_SECTION, start + SECTION.length()));
        assertEquals(
            List.of(),
            Stream.of("generic.tsv", "java.tsv", "node.tsv").flatMap(ManagedFilesDocumentationTest::paths)
                .filter(path -> !section.contains('`' + path + '`'))
                .distinct()
                .toList(),
            "each manifest path appears, in backticks, in the Managed Files section of the agent guide"
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
