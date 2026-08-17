package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Compares the mutants a run left alive against the ones a repository has accepted, and reports the
 * three ways the two can disagree, by the rules {@link MutationBaselineRules} states.
 *
 * <p>The third of them is not a disagreement between the two files but a doubt about the run that
 * produced one of them. A run that mutated nothing reports the same empty survivor set as a run whose
 * every mutant died, and a mutation check that can report perfection by mutating nothing is a check that
 * says nothing. A misaimed target pattern produces exactly that, and it looks like success.
 *
 * <p>An accepted entry the run could not decide is left alone. PIT counts a timeout as a detection, so
 * a mutant that merely slows a test down looks killed on a loaded machine and alive on a quiet one, and
 * reporting it would ask for a line whose deletion the next run reverses.
 *
 * <p>A missing report throws rather than being reported, because there is no verdict to give: the
 * analysis this reads is produced later in the same phase, so an absent report means the two ran in the
 * wrong order rather than that the code is clean. A missing baseline throws for a different reason and
 * says so in different words. It is the ordinary state of a project that has not written one yet, and the
 * remedy is to create the file rather than to rerun anything.
 */
public final class MutationBaselineCheck {

    private static final String UNACCEPTED = "Mutants survived that the baseline does not accept";
    private static final String STALE = "The baseline accepts mutants that are now killed, so delete these lines";
    private static final String EMPTY = "The mutation analysis produced no mutants, so it proved nothing";
    private static final String NO_REPORT = "the mutation analysis must run before this check";
    private static final String NO_BASELINE = "create it, empty when this project accepts no survivor";

    private final Path report;
    private final long mutants;
    private final Set<MutationSurvivor> survivors;
    private final Set<MutationSurvivor> accepted;
    private final Set<MutationSurvivor> intermittent;
    private final Set<MutationSurvivor> undecided;

    /**
     * Reads the report and the baseline.
     *
     * @param report   the path of PIT's {@code mutations.xml}
     * @param baseline the path of the accepted-survivor file
     */
    public MutationBaselineCheck(Path report, Path baseline) {
        String analysis = read(report, NO_REPORT);
        this.report = report;
        this.mutants = MutationBaselineRules.count(analysis);
        this.survivors = MutationBaselineRules.survivors(analysis);
        this.undecided = MutationBaselineRules.undecided(analysis);
        String accepting = read(baseline, NO_BASELINE);
        this.accepted = MutationBaselineRules.accepted(accepting);
        this.intermittent = MutationBaselineRules.intermittent(accepting);
    }

    /**
     * How many mutants the run produced, which a caller logs so the reach of a clean verdict is on the
     * record.
     *
     * @return the number of mutants in the report
     */
    public long mutants() {
        return this.mutants;
    }

    /**
     * The two disagreements between report and baseline, and the doubt about the run itself.
     *
     * @return one verdict per rule
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(UNACCEPTED, MutationBaselineRules.unaccepted(this.survivors, this.accepted)),
            new Findings(
                STALE, MutationBaselineRules.stale(
                    this.survivors, this.accepted, this.intermittent, this.undecided
                )
            ),
            new Findings(EMPTY, this.empty())
        );
    }

    private List<String> empty() {
        return this.mutants == 0 ? List.of(this.report.toString()) : List.of();
    }

    // The remedy travels with the caller rather than with the reader, because the two files are absent for
    // unrelated reasons and a message that covered both would have to name neither.
    private static String read(Path file, String remedy) {
        return Repository.readText(file).orElseThrow(
            () -> new IllegalStateException("Missing " + file + ", so " + remedy)
        );
    }
}
