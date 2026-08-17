package eu.ciechanowiec.airness.governance;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Compares the mutants a run left alive against the ones this repository has accepted, and reports the
 * two ways the two can disagree.
 *
 * <p>The accepted set is a list rather than a count. A count cannot tell one mutant from another, so
 * killing an accepted mutant while introducing a real one leaves the total unchanged and the gap
 * invisible. That window is narrow at a dozen entries and wide at a hundred. Listing them makes the
 * two failures distinct: a survivor nobody accepted is a new gap, and an accepted entry that no longer
 * survives is a stale line to delete, which is what stops the list from rotting into a blanket
 * exemption.
 *
 * <p>Both halves read what became of a mutant, so both need that outcome to mean something. A mutant
 * the analysis could not decide means nothing either way, and is held apart from the two so that a
 * loaded machine cannot turn an accepted entry into a line to delete and a quiet one ask for it back.
 */
@UtilityClass
final class MutationBaselineRules {

    private static final Pattern SURVIVOR = Pattern.compile(
        "(?s)<mutation detected='false' status='(?:SURVIVED|NO_COVERAGE)'.*?</mutation>"
    );
    private static final Pattern UNDECIDED = Pattern.compile(
        "(?s)<mutation detected='(?:true|false)' status='(?:TIMED_OUT|MEMORY_ERROR|RUN_ERROR)'.*?</mutation>"
    );
    private static final Pattern MUTATION = Pattern.compile("<mutation ");
    private static final Pattern OWNER = Pattern.compile("<mutatedClass>([^<]*)</mutatedClass>");
    private static final Pattern METHOD = Pattern.compile("<mutatedMethod>([^<]*)</mutatedMethod>");
    private static final Pattern DESCRIPTION = Pattern.compile("<description>([^<]*)</description>");
    private static final String COMMENT = "#";
    private static final String INTERMITTENT = "[intermittent]";
    private static final String SEPARATOR = "\t";
    private static final int IDENTITY_FIELDS = 3;

    /**
     * How many mutants a PIT report describes, whatever became of them.
     *
     * <p>A run that produced no mutants at all reports a perfect kill rate, and so does a run whose
     * every mutant died. The two are indistinguishable in the survivor set, which is empty either way,
     * so the caller asks for the total and refuses the first of them. It is the outcome a misaimed
     * {@code targetClasses} produces, and it is the one that looks like success.
     *
     * @param report the contents of PIT's {@code mutations.xml}
     * @return how many mutants the run produced
     */
    static long count(CharSequence report) {
        return MUTATION.matcher(report).results().count();
    }

    /**
     * The mutants a PIT report shows alive, without repeats.
     *
     * @param report the contents of PIT's {@code mutations.xml}
     * @return the surviving mutants
     */
    static Set<MutationSurvivor> survivors(CharSequence report) {
        return matching(SURVIVOR, report);
    }

    /**
     * The mutants a run reached no verdict on, so what became of them is evidence of nothing.
     *
     * <p>PIT reports a mutant as timed out, out of memory, or failed to run when the analysis could not
     * decide it, and each of those turns on how loaded the machine was rather than on what a test
     * asserts. A timeout is the common one, because PIT counts it as a detection and a mutant that
     * merely slows a test down crosses the bound on a busy runner and clears it on a quiet one.
     *
     * <p>The stale half of this check infers a kill from absence from the survivor set, and an undecided
     * mutant is absent from it. Without separating the two, such a mutant reads as reliably killed, its
     * accepted entry is reported as a line to delete, and the next run asks for the same line back.
     *
     * @param report the contents of PIT's {@code mutations.xml}
     * @return the mutants the run did not decide
     */
    static Set<MutationSurvivor> undecided(CharSequence report) {
        return matching(UNDECIDED, report);
    }

    private static Set<MutationSurvivor> matching(Pattern pattern, CharSequence report) {
        return pattern.matcher(report).results()
            .map(MatchResult::group)
            .map(MutationBaselineRules::survivor)
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * The entries of the baseline file, without repeats. Blank lines and comments are ignored, and a
     * line's reason is not part of its identity.
     *
     * @param baseline the contents of the baseline file
     * @return the accepted mutants
     */
    static Set<MutationSurvivor> accepted(String baseline) {
        return entries(baseline)
            .map(MutationBaselineRules::entry)
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * The accepted entries whose outcome is not deterministic, so being killed on one run says nothing
     * about the next.
     *
     * <p>A mutant in a teardown path is killed only if the scheduler happens to let a test observe the
     * missing call, and the same mutant has been seen killed on one run and surviving on the next. The
     * stale half of this check assumes an outcome that repeats, so without a way to say "this one does
     * not", a flapping mutant fails the build whichever way it is recorded, and the check stops meaning
     * anything. Marking one is a cost: it can rot into a permanent exemption, which is the very thing
     * this file exists to prevent, so the marker belongs only on an entry seen to flap
     *
     * @param baseline the contents of the baseline file
     * @return the entries marked intermittent
     */
    static Set<MutationSurvivor> intermittent(String baseline) {
        return entries(baseline)
            .filter(line -> reason(line).startsWith(INTERMITTENT))
            .map(MutationBaselineRules::entry)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static Stream<String> entries(String baseline) {
        return baseline.lines()
            .map(String::strip)
            .filter(line -> !line.isEmpty() && !line.startsWith(COMMENT));
    }

    private static String reason(String line) {
        String[] fields = line.split(SEPARATOR, IDENTITY_FIELDS + 1);
        return fields.length > IDENTITY_FIELDS ? fields[IDENTITY_FIELDS] : "";
    }

    /**
     * Mutants that survived without being accepted, which are new gaps in the tests.
     *
     * @param survivors what the run left alive
     * @param accepted  what this repository has accepted
     * @return the unaccounted survivors, most readable first
     */
    static List<String> unaccepted(
        Collection<MutationSurvivor> survivors, Collection<MutationSurvivor> accepted
    ) {
        return survivors.stream()
            .filter(survivor -> !accepted.contains(survivor))
            .map(MutationSurvivor::readable)
            .sorted()
            .toList();
    }

    /**
     * Accepted entries that no longer survive, which are lines to delete.
     *
     * @param survivors    what the run left alive
     * @param accepted     what this repository has accepted
     * @param intermittent the accepted entries whose outcome does not repeat
     * @param undecided    the mutants this run reached no verdict on
     * @return the stale entries, most readable first
     */
    static List<String> stale(
        Collection<MutationSurvivor> survivors, Collection<MutationSurvivor> accepted,
        Collection<MutationSurvivor> intermittent, Collection<MutationSurvivor> undecided
    ) {
        return accepted.stream()
            .filter(entry -> !survivors.contains(entry))
            .filter(entry -> !intermittent.contains(entry))
            .filter(entry -> !undecided.contains(entry))
            .map(MutationSurvivor::readable)
            .sorted()
            .toList();
    }

    private static MutationSurvivor survivor(CharSequence mutation) {
        return MutationSurvivor.of(
            first(OWNER, mutation), first(METHOD, mutation), first(DESCRIPTION, mutation)
        );
    }

    private static MutationSurvivor entry(String line) {
        String[] fields = line.split(SEPARATOR, IDENTITY_FIELDS + 1);
        if (fields.length < IDENTITY_FIELDS) {
            throw new IllegalStateException("A baseline line needs class, method and description: " + line);
        }
        return MutationSurvivor.of(fields[0], fields[1], fields[2]);
    }

    private static String first(Pattern pattern, CharSequence text) {
        return pattern.matcher(text).results().findFirst().map(match -> match.group(1)).orElse("");
    }
}
