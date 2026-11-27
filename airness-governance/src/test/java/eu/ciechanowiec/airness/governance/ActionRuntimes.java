package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * The workflows, metadata and expected offences the tests of the Node runtime rule share, and a metadata source
 * that answers from a map and records every action it was asked for.
 */
@UtilityClass
final class ActionRuntimes {

    static final String OUTDATED = "declare a Node runtime below";
    static final String UNREAD = "cannot be read";
    static final String WORKFLOW = ".github/workflows/ci.yml";
    static final String COMMIT = "11bd71901bbe5b1630ceea73d27597364c9af683";
    static final String CHECKOUT = "actions/checkout@" + COMMIT;
    static final String CURRENT_CHECKOUT = "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1";
    static final String DEPRECATED = ", a Node runtime GitHub has deprecated; use ";
    static final String NODE20 = "runs:\n  using: node20\n  main: dist/index.js\n";
    static final String NODE24 = "runs:\n  using: 'node24'\n  main: dist/index.js\n";
    static final String MISSING = "neither action.yml nor action.yaml is at that ref, so the repository, the"
        + " path or the ref does not exist, or reading it needs a credential; use a ref of ";

    static String steps(String... uses) {
        return "jobs:\n  build:\n    runs-on: ubuntu-26.04\n    steps:\n"
            + Arrays.stream(uses).map(use -> "      - uses: " + use + "\n").collect(Collectors.joining());
    }

    static String composite(String... uses) {
        return "runs:\n  using: composite\n  steps:\n"
            + Arrays.stream(uses).map(use -> "    - uses: " + use + "\n").collect(Collectors.joining());
    }

    static String outdated(String location, String action, String runtime) {
        String slug = action.substring(0, action.indexOf('@'));
        return location + ": " + action + " - declares " + runtime + DEPRECATED + "a commit of " + slug
            + " whose metadata declares node24 or later";
    }

    static ActionRuntimeCheck check(Path root, Map<String, String> metadata) {
        return new ActionRuntimeCheck(root, new RecordedMetadata(metadata));
    }

    static List<String> outdatedIn(ActionRuntimeCheck check) {
        return Verdicts.offences(check.findings(), OUTDATED);
    }

    static List<String> unreadIn(ActionRuntimeCheck check) {
        return Verdicts.offences(check.findings(), UNREAD);
    }

    static ActionRuntimeCheck workflow(String name, String yaml, Map<String, String> metadata) {
        return check(new GitFixture(name).write(WORKFLOW, yaml).root(), metadata);
    }
}
