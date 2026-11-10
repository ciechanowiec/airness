package eu.ciechanowiec.airness.governance;

import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
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
    // The same assignment with its value: the rest of the line, less a comment a blank separates from it.
    private static final Pattern VALUED = Pattern.compile(
        "^[ \\t]*(?:export[ \\t]+)?(?<name>[A-Za-z_]\\w*)[ \\t]*=(?<value>.*)$", Pattern.MULTILINE
    );
    private static final Pattern TRAILING_COMMENT = Pattern.compile("[ \\t]#.*$");
    // Quotation marks around nothing, which a dotenv reader reads as the empty value.
    private static final Pattern EMPTY_QUOTES = Pattern.compile("^(?:\"\"|'')$");
    // A name that marks its value as a credential, matched in any case.
    private static final Pattern CREDENTIAL = Pattern.compile(
        "PASSWORD|PASSWD|SECRET|TOKEN|KEY$", Pattern.CASE_INSENSITIVE
    );
    // A compose interpolation with no default. ${NAME:-x} and ${NAME-x} supply themselves and
    // ${NAME:?message} declares its own failure, so only the bare braced form must be supplied. The
    // unbraced $NAME is rare in compose and indistinguishable from a shell fragment, and $${NAME} is an
    // escaped dollar that compose leaves as text.
    private static final Pattern INTERPOLATION = Pattern.compile("(?<!\\$)\\$\\{(?<name>[A-Za-z_]\\w*)}");
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
    // The only reads a Next.js build substitutes into the bundle, textually: `NEXT_PUBLIC_` is the framework's
    // prefix for the values a project invents, in the dotted and the bracketed form.
    private static final Pattern INLINED = Pattern.compile(
        "process\\.env(?:\\.(?<name>NEXT_PUBLIC_\\w+)|\\[\\s*(['\"])(?<bracketed>NEXT_PUBLIC_\\w+)\\2\\s*])"
    );
    // A build-argument declaration, with or without a default, which declares the name either way.
    private static final Pattern ARGUMENT = Pattern.compile(
        "^[ \\t]*ARG[ \\t]+(?<names>\\S.*)$", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BLANKS = Pattern.compile("[ \\t]+");
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
     * The credentials an example file gives a value. A developer copies the example file to supply the
     * environment, so a value written there becomes the credential of every copy that is not edited, a
     * deployment's among them. An empty assignment documents the name and supplies nothing.
     *
     * @param example the example file
     * @return the name of each credential assigned a non-empty value, sorted
     */
    static Set<String> valuedCredentials(CharSequence example) {
        return VALUED.matcher(example).results()
            .filter(assignment -> CREDENTIAL.matcher(assignment.group(NAME)).find())
            .filter(assignment -> !value(assignment.group("value")).isEmpty())
            .map(assignment -> assignment.group(NAME))
            .collect(Collectors.toCollection(TreeSet::new));
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

    /**
     * The {@code NEXT_PUBLIC_} variables a Next.js build inlines from one source file.
     *
     * @param source the source, such as a {@code next.config.ts}
     * @return the names, sorted
     */
    static Set<String> inlined(CharSequence source) {
        return INLINED.matcher(source).results()
            .map(match -> Optional.ofNullable(match.group(NAME)).orElseGet(() -> match.group("bracketed")))
            .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * The build arguments a Dockerfile declares, in every stage, since which stage runs the build is not a fact
     * the text establishes: reading the file as one namespace can only accept a declaration in the wrong stage,
     * never reject a correct one.
     *
     * @param dockerfile the Dockerfile
     * @return the names, sorted
     */
    static Set<String> buildArguments(CharSequence dockerfile) {
        return CONTINUATION.matcher(dockerfile).replaceAll(" ").lines()
            .map(ARGUMENT::matcher)
            .filter(Matcher::find)
            .flatMap(match -> BLANKS.splitAsStream(match.group("names")))
            .map(token -> token.split("=", 2)[0])
            .filter(name -> !name.isEmpty())
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String value(CharSequence written) {
        String uncommented = TRAILING_COMMENT.matcher(written).replaceFirst("").strip();
        return EMPTY_QUOTES.matcher(uncommented).matches() ? "" : uncommented;
    }

    private static Set<String> names(CharSequence text, Pattern pattern) {
        return pattern.matcher(text).results()
            .map(match -> match.group(NAME))
            .collect(Collectors.toCollection(TreeSet::new));
    }
}
