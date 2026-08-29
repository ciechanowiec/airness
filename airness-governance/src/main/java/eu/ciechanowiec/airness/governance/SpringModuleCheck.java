package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads every Java source of one module before judging any of them.
 *
 * <p>This is the check for the rules whose two halves live in different files, and it is therefore the
 * one check here that cannot answer from the source in front of it. {@link SpringSourceCheck} reads a
 * file and reports on that file. This reads the module and reports on a file because of what a
 * different one said. The shape is the one {@link PackageCycleCheck} already uses.
 *
 * <p>The module rather than the reactor is the scope, because a controller and the entity it returns are
 * compiled together or they do not compile at all. A question that genuinely spans modules is asked by
 * {@link SpringReactorCheck} instead, which runs once for the whole build.
 *
 * <p>One rule here reaches past Java into the resource trees, because the profile a test activates is
 * answered by a file rather than by a type. {@link SpringConfigurationCheck} reads what those files say
 * and this reads only which of them exist, so the two do not overlap: a profile file is a fact about
 * the module that a rule over Java needs, in the same way the entity list is.
 */
public final class SpringModuleCheck {

    private static final String EXPOSED_ENTITIES
        = "Persistence entities carried by a web request or response";
    private static final String UNHANDLED_ERRORS
        = "Controllers left to the framework's own error page";
    private static final String CONTROLLERS_ON_REPOSITORIES
        = "Controllers holding the repository layer directly";
    private static final String INSTANTIATED_COMPONENTS
        = "Components built with new rather than taken from the container";
    private static final String UNPROVIDED_PROTOTYPES
        = "Prototype beans injected into a singleton that is built once";
    private static final String UNENABLED_METHOD_SECURITY
        = "Method security annotations that nothing in the module enables";
    private static final String UNNAMED_EXECUTORS
        = "Asynchronous methods left to an executor that pools nothing";
    private static final String MISSING_PROFILES
        = "Test profiles activated with nothing to activate";
    private static final Pattern PROFILE_FILE
        = Pattern.compile("application-(.+)\\.(?:yml|yaml|properties)");

    private final SpringTypes types;
    private final int sources;
    private final Set<String> profiles;

    /**
     * Reads the sources and records the types they declare.
     *
     * @param root          repository root the offences are reported relative to
     * @param sourceRoots   source directories of the module
     * @param resourceRoots resource directories of the module, main and test alike
     */
    public SpringModuleCheck(Path root, Collection<Path> sourceRoots, Collection<Path> resourceRoots) {
        List<Path> found = JavaSources.under(root, sourceRoots);
        this.sources = found.size();
        this.types = SpringTypes.over(root, found);
        this.profiles = profiled(root, resourceRoots);
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
     * How many types those sources declared, which a report names so that a source declaring none reads
     * as the empty scope it is rather than as a module that broke nothing.
     *
     * @return the number of declared types
     */
    public int types() {
        return this.types.size();
    }

    /**
     * Every module-wide rule and the sources that break it.
     *
     * @return one verdict per rule
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(EXPOSED_ENTITIES, SpringModuleRules.exposedEntities(this.types)),
            new Findings(UNHANDLED_ERRORS, SpringModuleRules.unhandledErrors(this.types)),
            new Findings(CONTROLLERS_ON_REPOSITORIES, SpringModuleRules.controllersOnRepositories(this.types)),
            new Findings(INSTANTIATED_COMPONENTS, SpringWiringRules.instantiatedComponents(this.types)),
            new Findings(UNPROVIDED_PROTOTYPES, SpringWiringRules.prototypesWithoutProviders(this.types)),
            new Findings(UNENABLED_METHOD_SECURITY, SpringWiringRules.unenabledMethodSecurity(this.types)),
            new Findings(UNNAMED_EXECUTORS, SpringWiringRules.unnamedAsyncExecutors(this.types)),
            new Findings(MISSING_PROFILES, SpringTestRules.missingProfiles(this.types, this.profiles))
        );
    }

    /*
     * The profiles this module carries a configuration file for. Only the name is taken, since whether
     * the file says anything useful is a question the configuration check already asks of it.
     */
    private static Set<String> profiled(Path root, Collection<Path> resourceRoots) {
        List<Path> resolved = resourceRoots.stream().map(root::resolve).map(Path::normalize).toList();
        return Repository.trackedFiles(root).stream()
            .filter(file -> resolved.stream().anyMatch(file::startsWith))
            .map(file -> PROFILE_FILE.matcher(file.getFileName().toString()))
            .filter(Matcher::matches)
            .map(profile -> profile.group(1))
            .collect(Collectors.toCollection(TreeSet::new));
    }
}
