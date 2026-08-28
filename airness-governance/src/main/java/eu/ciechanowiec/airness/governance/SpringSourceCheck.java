package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Reads the Java sources of one module for the Spring constructs that fail without saying so.
 *
 * <p>Both rules need something no analyzer configuration carries. The first compares a package against
 * the root the project declared, which is a value the build supplies rather than one the source states.
 * The second correlates a call with the declarations beside it, which the XPath the rule set is written
 * in cannot express. So they live here, where a check reads a file and answers about the whole of it.
 *
 * <p>The goal that runs this is bound by {@code airness-parent-spring-boot} alone, so a project that is
 * not a Spring Boot one never asks either question.
 */
public final class SpringSourceCheck {

    private static final String ENTRY_POINT
        = "Spring application classes outside the declared package root";
    private static final String BEAN_CALLS
        = "Bean methods calling another bean method of the same class";

    private final Path root;
    private final List<Path> sources;
    private final String packageRoot;

    /**
     * Creates a check over the Java sources one module holds.
     *
     * @param root        repository root the offences are reported relative to
     * @param sourceRoots source directories of the module
     * @param packageRoot the package every class of the project lives under
     */
    public SpringSourceCheck(Path root, Collection<Path> sourceRoots, String packageRoot) {
        this.root = root;
        this.sources = JavaSources.under(root, sourceRoots);
        this.packageRoot = packageRoot;
    }

    /**
     * How many sources the check read, which the goal refuses a zero of.
     *
     * @return the number of Java sources in scope
     */
    public int scanned() {
        return this.sources.size();
    }

    /**
     * Every Spring rule and the sources that break it.
     *
     * @return one verdict per rule
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(ENTRY_POINT, this.offences(this::entryPoint)),
            new Findings(BEAN_CALLS, this.offences(SpringSourceRules::calledBeanMethods))
        );
    }

    private List<String> entryPoint(CharSequence source) {
        return SpringSourceRules.misplacedEntryPoint(source, this.packageRoot);
    }

    private List<String> offences(SpringRule rule) {
        return this.sources.stream()
            .flatMap(
                source -> Repository.readText(source).stream()
                    .flatMap(text -> rule.offences(text).stream())
                    .map(offence -> "%s: %s".formatted(this.root.relativize(source), offence))
            )
            .toList();
    }

    /**
     * One rule read over the text of one source.
     */
    @FunctionalInterface
    private interface SpringRule {

        List<String> offences(CharSequence source);
    }
}
