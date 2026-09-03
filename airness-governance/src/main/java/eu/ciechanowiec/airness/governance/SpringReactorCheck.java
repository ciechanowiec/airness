package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads every Java source of the whole build for the questions a single module cannot answer.
 *
 * <p>Two shapes of question live here. A module holding one application class is correct, and two modules
 * each holding one is the defect. A feature annotation in a library module and its enabling configuration
 * in the application module are the opposite: either module alone looks incomplete, while the pair is the
 * working declaration. A per-module check gives the wrong verdict in both directions.
 *
 * <p>The goal that runs this therefore runs once for the session rather than once per module, which is
 * what stops one finding being printed as many times as the reactor has modules.
 */
public final class SpringReactorCheck {

    private static final String ONE_APPLICATION
        = "Spring application classes declared more than once in this build";
    private static final String UNENABLED_ASYNC
        = "Asynchronous methods no production configuration enables";
    private static final String UNENABLED_SCHEDULING
        = "Scheduled methods no production configuration enables";
    private static final String UNENABLED_CACHING
        = "Cache operations no production configuration enables";
    private static final String UNENABLED_RETRY
        = "Retry operations no production configuration enables";
    private static final String UNENABLED_AUDITING
        = "JPA auditing members no production configuration enables";
    private static final String DISABLED_METHOD_SECURITY
        = "Method-security annotations whose family remains disabled";
    private static final String UNDECLARED_ROLES
        = "Security annotations naming a role no enum declares";
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
     * Every reactor-wide Spring rule.
     *
     * @return the verdict
     */
    public List<Findings> findings() {
        List<SpringTypes.Declared> declared = this.types.carrying(APPLICATION);
        return List.of(
            new Findings(ONE_APPLICATION, declared.size() > 1 ? named(declared) : List.of()),
            new Findings(UNENABLED_ASYNC, SpringFeatureRules.unenabledAsync(this.types)),
            new Findings(UNENABLED_SCHEDULING, SpringFeatureRules.unenabledScheduling(this.types)),
            new Findings(UNENABLED_CACHING, SpringFeatureRules.unenabledCaching(this.types)),
            new Findings(UNENABLED_RETRY, SpringFeatureRules.unenabledRetry(this.types)),
            new Findings(UNENABLED_AUDITING, SpringFeatureRules.unenabledAuditing(this.types)),
            new Findings(DISABLED_METHOD_SECURITY, SpringFeatureRules.disabledMethodSecurity(this.types)),
            new Findings(UNDECLARED_ROLES, SpringRoleRules.undeclaredRoles(this.types))
        );
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
