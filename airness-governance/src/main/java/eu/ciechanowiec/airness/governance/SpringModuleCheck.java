package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

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
 */
public final class SpringModuleCheck {

    private static final String EXPOSED_ENTITIES
        = "Persistence entities carried by a web request or response";

    private final SpringTypes types;
    private final int sources;

    /**
     * Reads the sources and records the types they declare.
     *
     * @param root        repository root the offences are reported relative to
     * @param sourceRoots source directories of the module
     */
    public SpringModuleCheck(Path root, Collection<Path> sourceRoots) {
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
            new Findings(EXPOSED_ENTITIES, SpringModuleRules.exposedEntities(this.types))
        );
    }
}
