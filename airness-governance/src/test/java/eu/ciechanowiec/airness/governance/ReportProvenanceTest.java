package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class ReportProvenanceTest {

    private static final String TARGET = "target";

    @Test
    void refusesToRemoveAReportThatWasForceTrackedInsideTheOutputDirectory() {
        GitFixture fixture = new GitFixture("report-tracked-output").write(".gitignore", "target/\n")
            .write("target/oss-compliance-report-old.html", "authored content\n");
        fixture.git("add", "--force", "target/oss-compliance-report-old.html");
        Path root = fixture.root().toAbsolutePath();
        assertThrows(
            IllegalArgumentException.class,
            () -> ReportDestination.verify(root, root.resolve(TARGET), List.of("new.html", "new.pdf"))
        );
    }

    @Test
    void bindsBothUntrackedEditsAndCommittedSourceIdentity() {
        GitFixture fixture = new GitFixture("report-source").write("source.txt", "initial\n")
            .commit("feat(core): record report fixture sources");
        ReportSource clean = ReportSource.read(fixture.root(), "now");
        assertTrue(clean.draftReasons().isEmpty());
        fixture.write("untracked.txt", "new\n");
        ReportSource dirty = ReportSource.read(fixture.root(), "later");
        assertEquals(clean.commit(), dirty.commit());
        assertNotEquals(clean.fingerprint(), dirty.fingerprint());
        assertTrue(dirty.draftReasons().getFirst().contains("uncommitted"));
    }

    @Test
    void permitsIgnoredDestinationsButRefusesAuthoredOutputs() {
        GitFixture fixture = new GitFixture("report-output").write(".gitignore", "target/\n");
        Path root = fixture.root().toAbsolutePath();
        ReportDestination.verify(root, root.resolve("target/compliance"), List.of("report.html", "report.pdf"));
        assertThrows(
            IllegalArgumentException.class,
            () -> ReportDestination.verify(root, root.resolve("documents"), List.of("report.html"))
        );
    }

    @Test
    @SneakyThrows
    void refusesSymlinkedFilesAndSymlinkedParentDirectories() {
        GitFixture fixture = new GitFixture("report-links").write(".gitignore", "target/\n");
        Path root = fixture.root().toAbsolutePath();
        Path outside = Files.createTempDirectory(root.getParent(), "outside-");
        Files.createSymbolicLink(root.resolve(TARGET), outside);
        assertThrows(
            IllegalArgumentException.class,
            () -> ReportDestination.verify(root, root.resolve("target/reports"), List.of("report.html"))
        );
        Files.delete(root.resolve(TARGET));
        Files.createDirectory(root.resolve(TARGET));
        Files.createSymbolicLink(root.resolve("target/oss-compliance-report-old.pdf"), outside.resolve("secret"));
        assertThrows(
            IllegalArgumentException.class,
            () -> ReportDestination.verify(root, root.resolve(TARGET), List.of("report.html"))
        );
    }

    @Test
    void refusesAnOutputDirectoryOccupiedByAFile() {
        GitFixture fixture = new GitFixture("report-output-file").write(".gitignore", "target/\n")
            .write(TARGET, "existing content\n");
        Path root = fixture.root().toAbsolutePath();
        assertThrows(
            UncheckedIOException.class,
            () -> ReportDestination.verify(root, root.resolve(TARGET), List.of("report.html"))
        );
    }

    @Test
    @SneakyThrows
    void allowsAnExternalOutputDirectory() {
        GitFixture fixture = new GitFixture("report-external");
        Path output = Files.createTempDirectory(fixture.root().toAbsolutePath().getParent(), "external-report-");
        Path previous = Files.writeString(output.resolve("oss-compliance-report-old.html"), "previous report");
        ReportDestination.verify(fixture.root().toAbsolutePath(), output, List.of("report.html"));
        assertEquals("previous report", Files.readString(previous));
    }
}
