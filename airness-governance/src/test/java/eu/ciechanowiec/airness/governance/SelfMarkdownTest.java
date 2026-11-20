package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Airness holds its own Markdown, and the Markdown it writes into a consumer, to the rules it ships.
 *
 * <p>The goal runs over this repository in the build of {@code airness-parent} and reads the files that are
 * Markdown here. A managed file and a starter's instruction file are stored under an added {@code .asset} or
 * {@code .fixture} suffix and become Markdown only in the repository they are written into, where a defect in one
 * would fail every consumer at once and none of them could repair it. So the second test writes each of them,
 * under its path with the suffix removed, into a repository of its own and reads it there. A starter's lines
 * marking what a SNAPSHOT harness alone keeps are removed first, as {@code airness:new} removes them from the
 * application it writes.
 */
class SelfMarkdownTest {

    private static final Pattern STORED = Pattern.compile("\\.md\\.(?:asset|fixture)$");
    private static final Pattern SNAPSHOT_MARKER = Pattern.compile("(?m)^.*@SNAPSHOT_ONLY_(?:BEGIN|END)@.*\\n");

    @Test
    void holdsItsOwnMarkdownToTheRulesItShips() {
        MarkdownCheck check = new MarkdownCheck(SelfModules.repository());
        assertAll(
            () -> assertTrue(check.scanned() > 0, "a check that read nothing proves nothing about this repository"),
            () -> assertEquals(List.of(), broken(check.findings()))
        );
    }

    @Test
    void holdsTheMarkdownItWritesIntoAConsumerToTheSameRules() {
        Path repository = SelfModules.repository();
        List<Path> stored = Repository.trackedFiles(repository).stream()
            .filter(file -> STORED.matcher(file.toString()).find())
            .toList();
        GitFixture fixture = new GitFixture("self-markdown-assets");
        stored.forEach(
            file -> fixture.write(
                STORED.matcher(repository.relativize(file).toString()).replaceFirst(".md"),
                SNAPSHOT_MARKER.matcher(Repository.readText(file).orElseThrow()).replaceAll("")
            )
        );
        MarkdownCheck check = new MarkdownCheck(fixture.root());
        assertAll(
            () -> assertEquals(stored.size(), check.scanned(), "every stored Markdown file is read"),
            () -> assertTrue(check.scanned() > 0, "a check that read nothing proves nothing about the assets"),
            () -> assertEquals(List.of(), broken(check.findings()))
        );
    }

    private static List<String> broken(Collection<Findings> findings) {
        return findings.stream().filter(verdict -> !verdict.clean()).map(Findings::report).toList();
    }
}
