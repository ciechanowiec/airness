package eu.ciechanowiec.airness.governance;

import java.util.Collection;
import java.util.List;

/**
 * The agent runtime is denied write access to every generated file, and no machine-local setting gives
 * that access back.
 *
 * <p>The runtime reads its permissions from {@code .claude/settings.json} at the repository root, and it
 * matches a file path for every tool that writes only through an {@code Edit(...)} rule, so that is the
 * form each denial takes. A local settings file that allows one of those rules undoes the denial on the
 * one machine where it matters most, and it is untracked, so nothing else would ever report it.
 *
 * <p>The denial binds the runtime that reads the file. The regeneration check of each generated file
 * remains what catches an edit made another way.
 *
 * @param generated  every repository-relative path a generator owns
 * @param deny       the deny rules of the shared settings
 * @param localAllow the allow rules of the machine-local settings
 */
public record GeneratedDenial(List<String> generated, List<String> deny, List<String> localAllow) {

    private static final String WRITABLE
        = "Generated and pinned files an agent may still edit (run mvn airness:assets-sync, which adds each rule to"
            + " permissions.deny in .claude/settings.json)";
    private static final String REPERMITTED
        = "Write denials the local agent settings give back (remove each from permissions.allow in"
            + " .claude/settings.local.json)";

    /**
     * Copies every list, so the verdict cannot change after it was asked for.
     *
     * @param generated  every repository-relative path a generator owns
     * @param deny       the deny rules of the shared settings
     * @param localAllow the allow rules of the machine-local settings
     */
    public GeneratedDenial {
        generated = List.copyOf(generated);
        deny = List.copyOf(deny);
        localAllow = List.copyOf(localAllow);
    }

    /**
     * The denials missing from the shared settings, and those the local settings give back.
     *
     * @return one verdict per rule
     */
    public List<Findings> findings() {
        List<String> required = this.generated.stream().map(path -> "Edit(" + path + ')').toList();
        return List.of(
            new Findings(WRITABLE, missing(required, this.deny)),
            new Findings(REPERMITTED, required.stream().filter(this.localAllow::contains).toList())
        );
    }

    private static List<String> missing(Collection<String> required, Collection<String> present) {
        return required.stream().filter(rule -> !present.contains(rule)).toList();
    }
}
