package eu.ciechanowiec.airness.web;

import java.util.Collection;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * The accessibility question a rendered page is asked, and the reading of what it answers.
 *
 * <p>The rules report three verdicts: a rule that failed, a rule that passed, and a rule that could
 * not decide. Asking for the first alone is what every project of this fleet did, each having written
 * the same question separately, and it is why a link drawn as a filled button with its label in the
 * same colour as its ground passed fifteen test classes at a contrast of one to one. A rule that
 * cannot decide is exactly the case nobody has looked at, so this asks for all of them and reports
 * the two that are not a pass.
 *
 * <p>Nothing here starts a browser. The project owns its own, because signing in, navigating and
 * waiting are what differ between one application and the next, and the question and the reading of
 * the answer are what do not. A project runs {@link AxeLibrary#rules()}, then {@link #SCRIPT}, and
 * hands whatever came back to {@link #of(Object)}.
 */
@UtilityClass
public final class AxeAudit {

    /**
     * The question, as the script to run once the rules are loaded.
     *
     * <p>It names no result type, which is the whole of the difference between this and what it
     * replaces: naming one asks the rules to compute that one and leaves the rest of the answer
     * empty. Every line it answers with carries the verdict, the rule, the element and the sentence
     * that says what to do, separated by tabs, because a nested report would need a reader of its own
     * and these four are what a repair is made from.
     */
    public static final String SCRIPT = """
        const done = arguments[arguments.length - 1];
        const at = node => Array.isArray(node.target) ? node.target.join(' ') : String(node.target);
        const why = (node, rule) => node.any && node.any.length
            ? node.any[0].message
            : (node.none && node.none.length ? node.none[0].message : rule.description);
        axe.run(document)
           .then(report => done([].concat(
               report.violations.flatMap(rule => rule.nodes.map(
                   node => 'failed\\t' + rule.id + '\\t' + at(node) + '\\t' + node.failureSummary
               )),
               report.incomplete.flatMap(rule => rule.nodes.map(
                   node => 'undecided\\t' + rule.id + '\\t' + at(node) + '\\t' + why(node, rule)
               ))
           )))
           .catch(failure => done(
               ['failed\\taxe-did-not-run\\tdocument\\t' + failure.message]
           ));
        """;

    private static final String UNDECIDED = "undecided";

    private static final String SEPARATOR = "\t";

    private static final int FIELDS = 4;

    /**
     * Reads what the browser answered, accepting no undecidable at all.
     *
     * @param answered what running {@link #SCRIPT} returned, which a driver may answer as nothing
     *                 at all
     * @return what the reading came to
     * @throws IllegalStateException when the answer is not a list of lines, which says the rules did
     *                               not run rather than that the page is clean
     */
    public static AxeVerdict of(@Nullable Object answered) {
        return of(answered, List.of());
    }

    /**
     * Reads what the browser answered, accepting the undecidables the project has measured by hand.
     *
     * @param answered what running {@link #SCRIPT} returned, which a driver may answer as nothing
     *                 at all
     * @param accepted the undecidables the project has looked at and written a reason for
     * @return what the reading came to, including any exemption that matched nothing
     * @throws IllegalStateException when the answer is not a list of lines, which says the rules did
     *                               not run rather than that the page is clean
     */
    public static AxeVerdict of(@Nullable Object answered, Collection<AxeExemption> accepted) {
        List<String> lines = lines(answered);
        return AxeVerdict.of(failures(lines), undecided(lines), accepted);
    }

    // A driver answers a script with whatever the script resolved to, and a script that resolved to
    // nothing answers nothing. That is the same case as an answer of the wrong shape: the rules did
    // not report, so there is nothing to read, and a reading that treated it as an empty report would
    // call the page clean.
    private static List<String> lines(@Nullable Object answered) {
        if (answered instanceof List<?> reported) {
            return reported.stream().map(String::valueOf).toList();
        }
        throw new IllegalStateException(
            "The accessibility rules answered with nothing to read, which says they did not run "
                + "rather than that the page is clean"
        );
    }

    private static List<AxeFinding> failures(Collection<String> lines) {
        return lines.stream().filter(line -> !undecided(line)).map(AxeAudit::parsed).toList();
    }

    private static List<AxeFinding> undecided(Collection<String> lines) {
        return lines.stream().filter(AxeAudit::undecided).map(AxeAudit::parsed).toList();
    }

    private static boolean undecided(String line) {
        return line.startsWith(UNDECIDED + SEPARATOR);
    }

    // A line the browser did not write in the shape this asked for is reported whole rather than
    // dropped, because a reading that silently discarded what it could not parse would be the same
    // defect this artifact exists to end, one level further down.
    private static AxeFinding parsed(String line) {
        String[] fields = line.split(SEPARATOR, FIELDS);
        return fields.length == FIELDS
            ? new AxeFinding(fields[1], fields[2], fields[3])
            : new AxeFinding("unreadable-answer", "document", line);
    }
}
