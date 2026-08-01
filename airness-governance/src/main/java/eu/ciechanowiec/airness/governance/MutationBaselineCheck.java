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
 * every mutant died, and a mutation gate that can report perfection by mutating nothing is a gate that
 * says nothing. A misaimed target pattern produces exactly that, and it looks like success.
 *
 * <p>A missing report throws rather than being reported, because there is no verdict to give: the
 * analysis this reads is produced later in the same phase, so an absent report means the two ran in the
 * wrong order rather than that the code is clean.
 */
public final class MutationBaselineCheck {

    private static final String UNACCEPTED = "Mutants survived that the baseline does not accept";
    private static final String STALE = "The baseline accepts mutants that are now killed, so delete these lines";
    private static final String EMPTY = "The mutation analysis produced no mutants, so it proved nothing";

    private final Path report;
    private final long mutants;
    private final Set<MutationSurvivor> survivors;
    private final Set<MutationSurvivor> accepted;
    private final Set<MutationSurvivor> intermittent;

    /**
     * Reads the report and the baseline.
     *
     * @param report   the path of PIT's {@code mutations.xml}
     * @param baseline the path of the accepted-survivor file
     */
    public MutationBaselineCheck(Path report, Path baseline) {
        String analysis = read(report);
        String accepting = read(baseline);
        this.report = report;
        this.mutants = MutationBaselineRules.count(analysis);
        this.survivors = MutationBaselineRules.survivors(analysis);
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
            new Findings(STALE, MutationBaselineRules.stale(this.survivors, this.accepted, this.intermittent)),
            new Findings(EMPTY, this.empty())
        );
    }

    private List<String> empty() {
        return this.mutants == 0 ? List.of(this.report.toString()) : List.of();
    }

    private static String read(Path file) {
        return Repository.readText(file).orElseThrow(
            () -> new IllegalStateException(
                "Missing " + file + ", so the mutation analysis must run before this check"
            )
        );
    }
}
