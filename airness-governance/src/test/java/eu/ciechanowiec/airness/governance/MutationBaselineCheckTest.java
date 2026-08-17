package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The mutation-baseline check reports a survivor nobody accepted, an accepted entry that no longer
 * survives, and a run that mutated nothing at all. The third is the one that looks like success.
 */
class MutationBaselineCheckTest {

    private static final String REPORT = """
        <?xml version="1.0" encoding="UTF-8"?>
        <mutations>
            <mutation detected='false' status='SURVIVED'>
                <mutatedClass>sample.Subject</mutatedClass>
                <mutatedMethod>value</mutatedMethod>
                <description>replaced int return with 0</description>
            </mutation>
            <mutation detected='true' status='KILLED'>
                <mutatedClass>sample.Subject</mutatedClass>
                <mutatedMethod>other</mutatedMethod>
                <description>removed call to sample.Subject::log</description>
            </mutation>
        </mutations>
        """;

    private static final String EMPTY_REPORT = """
        <?xml version="1.0" encoding="UTF-8"?>
        <mutations>
        </mutations>
        """;

    private static final String ACCEPTS_THE_SURVIVOR = """
        # class\tmethod\tdescription\treason
        sample.Subject\tvalue\treplaced int return with 0\tThe value is not observable from outside
        """;

    private static final String ACCEPTS_A_KILLED_MUTANT = """
        sample.Subject\tvalue\treplaced int return with 0\tThe value is not observable from outside
        sample.Subject\tother\tremoved call to sample.Subject::log\tNo test observes the log call
        """;

    private static final String UNDECIDED_REPORT = """
        <?xml version="1.0" encoding="UTF-8"?>
        <mutations>
            <mutation detected='true' status='TIMED_OUT'>
                <mutatedClass>sample.Subject</mutatedClass>
                <mutatedMethod>spin</mutatedMethod>
                <description>removed call to java/lang/Thread::sleep</description>
            </mutation>
        </mutations>
        """;

    private static final String ACCEPTS_THE_UNDECIDED_MUTANT = """
        sample.Subject\tspin\tremoved call to java/lang/Thread::sleep\tThe pause only sets the cadence
        """;

    @TempDir
    private Path directory;

    @SneakyThrows
    private MutationBaselineCheck check(CharSequence report, CharSequence baseline) {
        Path reportFile = Files.writeString(this.directory.resolve("mutations.xml"), report);
        Path baselineFile = Files.writeString(this.directory.resolve("mutation-baseline.tsv"), baseline);
        return new MutationBaselineCheck(reportFile, baselineFile);
    }

    @Test
    void passesWhenTheBaselineAccountsForEverySurvivor() {
        MutationBaselineCheck check = this.check(REPORT, ACCEPTS_THE_SURVIVOR);
        assertEquals(2, check.mutants(), "both mutants were produced, whatever became of them");
        assertTrue(Verdicts.clean(check.findings()), "and the one that lived is accepted by name");
    }

    @Test
    void reportsASurvivorTheBaselineDoesNotAccept() {
        assertEquals(
            1, Verdicts.offences(this.check(REPORT, "").findings(), "does not accept").size(),
            "a survivor nobody accepted is a new gap in the tests"
        );
    }

    @Test
    void reportsAnAcceptedEntryThatNoLongerSurvives() {
        assertEquals(
            1, Verdicts.offences(this.check(REPORT, ACCEPTS_A_KILLED_MUTANT).findings(), "now killed").size(),
            "a line for a mutant that now dies is what stops the list rotting into a blanket exemption"
        );
    }

    // A timeout says the machine was busy rather than that a test caught anything, so the entry has to
    // outlive the run that timed out. Otherwise the build is red whichever way the line is recorded.
    @Test
    void keepsAnAcceptedEntryThatTheRunFailedToDecide() {
        MutationBaselineCheck check = this.check(UNDECIDED_REPORT, ACCEPTS_THE_UNDECIDED_MUTANT);
        assertEquals(1, check.mutants(), "the mutant was produced, whatever became of it");
        assertTrue(
            Verdicts.offences(check.findings(), "now killed").isEmpty(),
            "a mutant the run did not decide is not a line to delete"
        );
    }

    @Test
    void reportsARunThatMutatedNothing() {
        MutationBaselineCheck check = this.check(EMPTY_REPORT, "");
        assertEquals(0, check.mutants(), "nothing was mutated");
        assertEquals(
            1, Verdicts.offences(check.findings(), "no mutants").size(),
            "which reports the same empty survivor set as a run whose every mutant died"
        );
    }

    @Test
    void refusesToReportOnAnAnalysisThatHasNotRun() {
        Path absent = this.directory.resolve("absent.xml");
        IllegalStateException thrown = assertThrows(
            IllegalStateException.class, () -> new MutationBaselineCheck(absent, absent)
        );
        assertTrue(
            thrown.toString().contains("the mutation analysis must run before this check"),
            "an absent report means the two ran in the wrong order rather than that the code is clean"
        );
        assertTrue(thrown.toString().contains("absent.xml"), "and the message names the file it wanted");
    }

    // The two files go missing for unrelated reasons, and a project that has never written a baseline is
    // the ordinary case rather than a build run out of order. One message for both sent that project off
    // to rerun an analysis that had already produced the report sitting beside it.
    @Test
    @SneakyThrows
    void tellsAnAbsentBaselineApartFromAnAbsentReport() {
        Path reportFile = Files.writeString(this.directory.resolve("mutations.xml"), REPORT);
        Path absent = this.directory.resolve("mutation-baseline.tsv");
        IllegalStateException thrown = assertThrows(
            IllegalStateException.class, () -> new MutationBaselineCheck(reportFile, absent)
        );
        assertTrue(
            thrown.toString().contains("create it"),
            "the remedy for a baseline nobody has written is to write one: " + thrown
        );
        assertTrue(
            thrown.toString().contains("mutation-baseline.tsv"),
            "and the message names the baseline rather than the report that is already there"
        );
    }
}
