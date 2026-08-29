package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads every Java source of the whole build for the questions a single module cannot answer.
 *
 * <p>One question is here so far, and it is here rather than in {@link SpringModuleCheck} for a reason
 * that decides the scope rather than the convenience: a module holding one application class is correct,
 * and two modules each holding one is the defect. A per-module check sees only the first half of that
 * and reports nothing, however many modules it is run over.
 *
 * <p>The goal that runs this therefore runs once for the session rather than once per module, which is
 * what stops one finding being printed as many times as the reactor has modules.
 */
public final class SpringReactorCheck {

    private static final String ONE_APPLICATION
        = "Spring application classes declared more than once in this build";
    private static final Pattern APPLICATION = Pattern.compile("@SpringBootApplication\\b");

    private final SpringTypes types;
    private final int sources;

    /**
     * Reads the sources of every module in the build.
     *
     * @param root        repository root the offences are reported relative to
     * @param sourceRoots source directories of every module
     */
    public SpringReactorCheck(Path root, Collection<Path> sourceRoots) {
        List<Path> found = JavaSources.under(root, sourceRoots);
        this.sources = found.size();
        this.types = SpringTypes.over(root, found);
    }

    /**
     * How many sources the check read, which the goal refuses a zero of.
     *
     * @return the number of Java sources in scope
     */
    public int scanned() {
        return this.sources;
    }

    /**
     * The one rule, with one offence per application class when there is more than one.
     *
     * @return the verdict
     */
    public List<Findings> findings() {
        List<SpringTypes.Declared> declared = this.types.carrying(APPLICATION);
        return List.of(new Findings(ONE_APPLICATION, declared.size() > 1 ? named(declared) : List.of()));
    }

    private static List<String> named(Collection<SpringTypes.Declared> declared) {
        return declared.stream()
            .map(
                type -> type.source()
                    + ": a second class carrying @SpringBootApplication, so which application starts is"
                    + " decided by whichever the search finds first, and a test can boot one the artifact"
                    + " does not ship"
            )
            .toList();
    }
}
