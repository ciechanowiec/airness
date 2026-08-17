package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The comparison has to separate two failures that a surviving-mutant count folds together: a mutant
 * nobody accepted, and an accepted mutant that is no longer alive.
 */
class MutationBaselineRulesTest {

    private static final String REPORT = """
        <mutations>
        <mutation detected='false' status='SURVIVED'><mutatedClass>a.B</mutatedClass>\
        <mutatedMethod>run</mutatedMethod><description>removed call to a/B::log</description></mutation>
        <mutation detected='true' status='KILLED'><mutatedClass>a.B</mutatedClass>\
        <mutatedMethod>run</mutatedMethod><description>negated conditional</description></mutation>
        <mutation detected='false' status='NO_COVERAGE'><mutatedClass>a.C</mutatedClass>\
        <mutatedMethod>stop</mutatedMethod><description>removed call to a/C::close</description></mutation>
        </mutations>
        """;

    private static final String UNDECIDED_REPORT = """
        <mutations>
        <mutation detected='true' status='TIMED_OUT'><mutatedClass>a.D</mutatedClass>\
        <mutatedMethod>spin</mutatedMethod><description>removed call to java/lang/Thread::sleep</description>\
        </mutation>
        </mutations>
        """;

    private static final String ACCEPTS_THE_UNDECIDED = """
        a.D\tspin\tremoved call to java/lang/Thread::sleep\tthe pause sets cadence rather than any answer
        """;

    @Test
    void readsOnlyTheMutantsThatAreStillAlive() {
        Set<MutationSurvivor> survivors = MutationBaselineRules.survivors(REPORT);
        assertEquals(2, survivors.size(), "a killed mutant is not a survivor");
        assertTrue(survivors.contains(MutationSurvivor.of("a.B", "run", "removed call to a/B::log")));
        assertTrue(survivors.contains(MutationSurvivor.of("a.C", "stop", "removed call to a/C::close")));
    }

    @Test
    void reportsASurvivorNobodyAccepted() {
        Set<MutationSurvivor> accepted = MutationBaselineRules.accepted(
            "a.B\trun\tremoved call to a/B::log\tthe log line is asserted elsewhere\n"
        );
        assertEquals(
            List.of("C.stop: removed call to a/C::close"),
            MutationBaselineRules.unaccepted(MutationBaselineRules.survivors(REPORT), accepted)
        );
    }

    @Test
    void reportsAnAcceptedMutantThatIsNoLongerAlive() {
        String baseline = "a.B\tgone\tremoved call to a/B::log\tstale entry\n";
        assertEquals(
            List.of("B.gone: removed call to a/B::log"),
            MutationBaselineRules.stale(
                MutationBaselineRules.survivors(REPORT),
                MutationBaselineRules.accepted(baseline),
                MutationBaselineRules.intermittent(baseline),
                MutationBaselineRules.undecided(REPORT)
            )
        );
    }

    // A mutant whose outcome does not repeat must not be reported stale on the run that happens to kill
    // it, or the build is red whichever way the entry is recorded.
    @Test
    void keepsAnIntermittentEntryThatThisRunHappenedToKill() {
        String baseline = "a.B\tgone\tremoved call to a/B::log\t[intermittent] seen to flap\n";
        assertTrue(
            MutationBaselineRules.stale(
                MutationBaselineRules.survivors(REPORT),
                MutationBaselineRules.accepted(baseline),
                MutationBaselineRules.intermittent(baseline),
                MutationBaselineRules.undecided(REPORT)
            ).isEmpty(),
            "a marked entry is not a line to delete"
        );
    }

    // The marker suspends only the stale half. A survivor nobody accepted is still a new gap, marked
    // neighbours or not, which is what keeps the marker from becoming a way to switch the check off.
    @Test
    void stillReportsAnUnacceptedSurvivorBesideAnIntermittentEntry() {
        Set<MutationSurvivor> accepted = MutationBaselineRules.accepted(
            "a.B\trun\tremoved call to a/B::log\t[intermittent] seen to flap\n"
        );
        assertEquals(
            List.of("C.stop: removed call to a/C::close"),
            MutationBaselineRules.unaccepted(MutationBaselineRules.survivors(REPORT), accepted)
        );
    }

    @Test
    void readsTheMutantsTheRunFailedToDecide() {
        Set<MutationSurvivor> undecided = MutationBaselineRules.undecided(UNDECIDED_REPORT);
        assertEquals(1, undecided.size(), "the timed-out mutant reached no verdict");
        assertTrue(
            undecided.contains(
                MutationSurvivor.of("a.D", "spin", "removed call to java/lang/Thread::sleep")
            )
        );
    }

    @Test
    void doesNotCountAnUndecidedMutantAsAlive() {
        assertTrue(
            MutationBaselineRules.survivors(UNDECIDED_REPORT).isEmpty(),
            "a mutant that timed out was not seen to survive either"
        );
    }

    // PIT counts a timeout as a detection, so a mutant that merely slows a test down looks killed on a
    // loaded machine and alive on a quiet one. Reporting the entry would ask for a line whose deletion
    // the next run reverses, which is the same red-either-way trap the intermittent marker exists for.
    @Test
    void keepsAnAcceptedEntryThatThisRunFailedToDecide() {
        assertTrue(
            MutationBaselineRules.stale(
                MutationBaselineRules.survivors(UNDECIDED_REPORT),
                MutationBaselineRules.accepted(ACCEPTS_THE_UNDECIDED),
                MutationBaselineRules.intermittent(ACCEPTS_THE_UNDECIDED),
                MutationBaselineRules.undecided(UNDECIDED_REPORT)
            ).isEmpty(),
            "a mutant the run did not decide is not a line to delete"
        );
    }

    // The exemption is only as wide as the doubt. An entry absent from a report that decided everything
    // it produced is still a stale line, so a vanished mutant is still cleaned up.
    @Test
    void stillReportsAnAcceptedEntryTheRunDecidedAgainst() {
        assertEquals(
            List.of("D.spin: removed call to java/lang/Thread::sleep"),
            MutationBaselineRules.stale(
                MutationBaselineRules.survivors(REPORT),
                MutationBaselineRules.accepted(ACCEPTS_THE_UNDECIDED),
                MutationBaselineRules.intermittent(ACCEPTS_THE_UNDECIDED),
                MutationBaselineRules.undecided(REPORT)
            )
        );
    }

    @Test
    void marksOnlyTheEntriesThatSayTheyFlap() {
        String baseline = """
            a.B\trun\tremoved call to a/B::log\t[intermittent] flaps
            a.C\tstop\tremoved call to a/C::close\tan ordinary reason
            """;
        assertEquals(1, MutationBaselineRules.intermittent(baseline).size());
    }

    @Test
    void ignoresCommentsAndBlankLinesInTheBaseline() {
        Set<MutationSurvivor> accepted = MutationBaselineRules.accepted(
            "# a heading\n\na.B\trun\tremoved call to a/B::log\treason\n\n"
        );
        assertEquals(1, accepted.size(), "only real entries count");
    }

    @Test
    void rejectsABaselineLineThatCannotIdentifyAMutant() {
        assertThrows(
            IllegalStateException.class, () -> MutationBaselineRules.accepted("a.B\trun\n")
        );
    }
}
