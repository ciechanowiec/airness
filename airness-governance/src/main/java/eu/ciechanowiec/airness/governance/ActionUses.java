package eu.ciechanowiec.airness.governance;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Reads the value of one {@code uses} key into what it names.
 *
 * <p>Passed over are a local action ({@code ./path}), which is judged in its own file where it lives, a Docker
 * action ({@code docker://image}), which runs no Node and whose image the blocklist judges, and a reusable
 * workflow ({@code owner/repo/.github/workflows/file.yml@ref}), which is a workflow rather than an action. A
 * value holding a {@code ${{ }}} expression cannot be judged, since GitHub substitutes no expression in a
 * {@code uses} key and so never runs such a step either. That is the stance the blocklist takes on an image
 * reference carrying a variable: what cannot be read as written is refused rather than passed. Neither can a
 * value outside the form GitHub reads an action in be judged, nor one whose owner, repository, path or ref holds
 * a character a repository name or a Git ref does not, or a {@code .} or {@code ..} segment, which also keeps
 * every part safe to place in an address and in a cache path.
 */
@UtilityClass
final class ActionUses {

    /**
     * The form GitHub reads an action in, which a finding names as the replacement for a value outside it.
     */
    static final String FORM = "owner/repo[/path]@ref";

    private static final String EXPRESSION = "${{";
    private static final String LOCAL = "./";
    private static final String DOCKER = "docker://";
    // GitHub's own alphabet for owner and repository names, and for a path and a ref the characters a Git ref and
    // a directory name are written in, without a space, a colon, a backslash or a percent sign.
    private static final Pattern REMOTE = Pattern.compile(
        "(?<owner>[A-Za-z0-9-]+)/(?<repo>[A-Za-z0-9_.-]+)(?:/(?<path>[A-Za-z0-9_.\\-/]+))?"
            + "@(?<ref>[A-Za-z0-9_.\\-/+]+)"
    );
    private static final Pattern REUSABLE = Pattern.compile("\\.github/workflows/[^/]+\\.ya?ml");
    private static final String SEPARATOR = "/";
    private static final String CURRENT = ".";
    private static final String PARENT = "..";

    /**
     * Reads one {@code uses} value.
     *
     * @param written the value as the workflow writes it, quotation marks removed
     * @return what it names
     */
    static ActionUse read(String written) {
        if (written.contains(EXPRESSION)) {
            return new ActionUse.Unjudgeable(
                "an expression, which GitHub never substitutes in a uses key", "the action written literally"
            );
        }
        return passedOver(written) ? new ActionUse.Skipped() : remote(written).map(ActionUses::action)
            .orElseGet(() -> new ActionUse.Unjudgeable("not written in the form GitHub reads an action in", FORM));
    }

    private static boolean passedOver(String written) {
        return written.startsWith(LOCAL) || written.startsWith(DOCKER);
    }

    // A reusable workflow is named like an action in a directory, the directory its workflows live in.
    private static ActionUse action(ActionReference reference) {
        return REUSABLE.matcher(reference.path()).matches() ? new ActionUse.Skipped() : reference;
    }

    private static Optional<ActionReference> remote(CharSequence written) {
        Matcher matched = REMOTE.matcher(written);
        if (!matched.matches()) {
            return Optional.empty();
        }
        ActionReference reference = new ActionReference(
            matched.group("owner"), matched.group("repo"),
            Optional.ofNullable(matched.group("path")).orElse(""), matched.group("ref")
        );
        return Optional.of(reference).filter(ActionUses::navigable);
    }

    // No part may climb out of the directory it names, in an address or in a cache path.
    private static boolean navigable(ActionReference reference) {
        String repository = reference.repository();
        boolean named = !CURRENT.equals(repository) && !PARENT.equals(repository);
        boolean pathStays = reference.path().isEmpty() || segmentsStay(reference.path());
        return named && pathStays && segmentsStay(reference.revision());
    }

    private static boolean segmentsStay(String path) {
        return Arrays.stream(path.split(SEPARATOR, -1))
            .noneMatch(segment -> segment.isEmpty() || CURRENT.equals(segment) || PARENT.equals(segment));
    }
}
