package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SuppressionNotesTest {

    @TempDir
    private Path directory;

    @Test
    void splitsTheReasonFromTheMitigationPlanAndReadsTheDate() {
        SuppressionNotes notes = SuppressionNotes.parse(
            """
                Added 2026-08-23; reviewed and narrowed 2026-10-09. The bundles contain no
                    help web server.

                Mitigation: They run only during the build. The entry is removed with the next upgrade.
                """
        );
        assertEquals("The bundles contain no help web server.", notes.reason());
        assertEquals("They run only during the build. The entry is removed with the next upgrade.", notes.mitigation());
        assertEquals("2026-08-23", notes.addedOn());
    }

    @Test
    void keepsAReasonWithoutALeadInWhole() {
        SuppressionNotes notes = SuppressionNotes.parse("The parser is absent. Added 2026-09-07. Mitigation: Upgrade.");
        assertEquals("The parser is absent. Added 2026-09-07.", notes.reason());
        assertEquals("Upgrade.", notes.mitigation());
        assertEquals("2026-09-07", notes.addedOn());
    }

    @Test
    void readsNotesWithoutAMarkerOrADateAsHavingNeither() {
        SuppressionNotes notes = SuppressionNotes.parse("  The parser is absent.  ");
        assertEquals(new SuppressionNotes("The parser is absent.", "", ""), notes);
    }

    @Test
    void rejectsNotesWithoutAMitigationPlan() {
        String document = """
            <suppressions>
                <suppress>
                    <notes>The vulnerable code path is a servlet this project never deploys. Added 2026-08-23.
                    Mitigation:</notes>
                    <cve>CVE-2020-27225</cve>
                </suppress>
            </suppressions>
            """;
        assertEquals(
            List.of(
                "Dependency-Check suppression for CVE-2020-27225: state a mitigation plan in notes after a "
                    + "Mitigation: marker, saying what limits the risk now and what will remove the vulnerable package"
            ),
            new SuppressionDocument(this.write(document)).problems(),
            "a reviewer reads the plan beside the advisory, so a marker with nothing after it is no plan"
        );
    }

    @Test
    void readsEveryEntrysNotesAsOneLine() {
        String document = """
            <suppressions>
                <suppress>
                    <notes>The parser is absent.
                        Added 2026-09-07. Mitigation: Removed with the next upgrade.</notes>
                    <cve>CVE-2020-27225</cve>
                </suppress>
            </suppressions>
            """;
        Path file = this.write(document);
        assertEquals(
            List.of("The parser is absent. Added 2026-09-07. Mitigation: Removed with the next upgrade."),
            new SuppressionDocument(file).notes()
        );
    }

    @SneakyThrows
    private Path write(CharSequence content) {
        return Files.writeString(this.directory.resolve("suppressions.xml"), content);
    }
}
