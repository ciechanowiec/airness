package eu.ciechanowiec.airness.governance;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reads environment variable names out of the files that declare, interpolate or supply them.
 *
 * <p>Each reader answers one side of a containment the check holds: what a compose file needs, what the
 * example file documents, and what a workflow gives its jobs. Every pattern captures into a group named
 * {@code name}, and every answer is sorted, so a finding list reads the same on every machine.
 */
@UtilityClass
final class EnvironmentRules {

    private static final String NAME = "name";
    // A dotenv assignment, optionally exported. A comment line cannot match, because '#' is no name.
    private static final Pattern ASSIGNMENT = Pattern.compile(
        "^[ \\t]*(?:export[ \\t]+)?(?<name>[A-Za-z_]\\w*)[ \\t]*=", Pattern.MULTILINE
    );
    // A compose interpolation with no default. ${NAME:-x} and ${NAME-x} supply themselves and
    // ${NAME:?message} declares its own failure, so only the bare braced form must be supplied. The
    // unbraced $NAME is rare in compose and indistinguishable from a shell fragment, and $${NAME} is an
    // escaped dollar that compose leaves as text.
    private static final Pattern INTERPOLATION = Pattern.compile("(?<!\\$)\\$\\{(?<name>[A-Za-z_]\\w*)\\}");
    // A workflow supplies a name as a mapping key in capitals at any depth, or by referring to a
    // secret, a variable or the job environment.
    private static final Pattern WORKFLOW_KEY = Pattern.compile(
        "^[ \\t]*(?<name>[A-Z_][A-Z0-9_]*)[ \\t]*:", Pattern.MULTILINE
    );
    private static final Pattern WORKFLOW_REFERENCE = Pattern.compile(
        "\\$\\{\\{[^}]*?\\b(?:secrets|vars|env)\\.(?<name>[A-Za-z_]\\w*)"
    );
    // A Maven invocation of a phase that reaches verify, where the container check evaluates every compose
    // file. A goal such as deploy:help names a plugin rather than a phase.
    private static final Pattern VERIFYING = Pattern.compile(
        "\\bmvnw?\\b[^\\n]*(?<![\\w:.-])(?:verify|install|deploy)(?![\\w:.-])"
    );
    // A run that skips the checks evaluates nothing, whichever phase it names.
    private static final Pattern SKIPPING = Pattern.compile("-D(?:skipTests|maven\\.test\\.skip)(?:=true)?(?![\\w.=])");
    // A shell line continued with a trailing backslash is one command.
    private static final Pattern CONTINUATION = Pattern.compile("\\\\[ \\t]*\\r?\\n[ \\t]*");

    /**
     * The variables an example file documents.
     *
     * @param example the example file
     * @return each name once, sorted
     */
    static Set<String> documented(CharSequence example) {
        return names(example, ASSIGNMENT);
    }

    /**
     * The variables a compose file interpolates without supplying a default.
     *
     * @param compose the compose file
     * @return each name once, sorted
     */
    static Set<String> interpolated(CharSequence compose) {
        return names(compose, INTERPOLATION);
    }

    /**
     * The variables a workflow supplies, by any means and at any depth.
     *
     * @param workflow the workflow
     * @return each name once, sorted
     */
    static Set<String> supplied(CharSequence workflow) {
        return Stream.concat(names(workflow, WORKFLOW_KEY).stream(), names(workflow, WORKFLOW_REFERENCE).stream())
            .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Whether a workflow runs the verification that evaluates every compose file.
     *
     * @param workflow the workflow
     * @return whether it invokes Maven through verify without skipping the checks
     */
    static boolean verifying(CharSequence workflow) {
        return CONTINUATION.matcher(workflow).replaceAll(" ").lines()
            .anyMatch(line -> VERIFYING.matcher(line).find() && !SKIPPING.matcher(line).find());
    }

    private static Set<String> names(CharSequence text, Pattern pattern) {
        return pattern.matcher(text).results()
            .map(match -> match.group(NAME))
            .collect(Collectors.toCollection(TreeSet::new));
    }
}
