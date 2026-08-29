package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Reads what a module is built from, before anything it holds is compiled.
 *
 * <p>Every other Spring check here reads a file a developer wrote. This reads the declarations around
 * them, because the defects it names are settled by the model: an archive that ships a debug endpoint,
 * a deployable application with no probes, a mapped schema nothing creates, two web stacks of which one
 * silently loses, and auto-configuration an application publishes to itself.
 *
 * <p>The goal that runs this is bound at {@code validate}, which is the one departure from the other
 * Spring goals and the reason is the whole point of the check: every question it asks is answered before
 * a compiler runs, so asking at {@code package} would mean building an archive in order to be told that
 * it should not have been built that way. It still answers to {@code airness.enforce}, unlike the
 * preflight goals beside it, because these are findings about the project rather than about the harness.
 */
public final class SpringModelCheck {

    private static final String SHIPPED_DEVTOOLS
        = "Development tooling declared in a way that lets it ship";
    private static final String UNMIGRATED_SCHEMA
        = "A mapped schema with nothing declared that would create it";
    private static final String MISSING_ACTUATOR
        = "A deployable application publishing nothing an orchestrator can read";
    private static final String TWO_WEB_STACKS
        = "Both web stacks declared where only one of them can start";
    private static final String OWN_AUTO_CONFIGURATION
        = "Auto-configuration declared inside the application it configures";
    private static final List<String> REGISTRATIONS = List.of(
        "org.springframework.boot.autoconfigure.AutoConfiguration.imports", "spring.factories"
    );

    private final String pom;
    private final List<SpringDependency> dependencies;
    private final List<String> declarations;
    private final boolean repackaged;

    /**
     * Reads the module's declarations and the registration files it carries.
     *
     * @param pom           the module's pom, which every offence is named after
     * @param resourceRoots resource directories of the module, main and test alike
     * @param dependencies  the module's effective dependencies
     * @param repackaged    whether the Boot plugin turns this module into a deployable archive
     */
    public SpringModelCheck(
        Path pom, Collection<Path> resourceRoots, Collection<SpringDependency> dependencies,
        boolean repackaged
    ) {
        Path module = Objects.requireNonNull(
            pom.getParent(), "A module pom is a file in the module directory and always has one"
        );
        Path root = Repository.rootFrom(module);
        this.pom = root.relativize(pom).toString();
        this.dependencies = List.copyOf(dependencies);
        this.declarations = registered(root, resourceRoots);
        this.repackaged = repackaged;
    }

    /**
     * How many dependencies the check read, which the goal reports so an empty model reads as one.
     *
     * @return the number of declared dependencies in scope
     */
    public int scanned() {
        return this.dependencies.size();
    }

    /**
     * Whether the module is the one that gets deployed, which four of the five rules ask first.
     *
     * @return whether the Boot plugin repackages this module
     */
    public boolean repackaged() {
        return this.repackaged;
    }

    /**
     * Every model-level rule and the declaration that breaks it.
     *
     * @return one verdict per rule
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(SHIPPED_DEVTOOLS, SpringModelRules.shippedTooling(this.pom, this.dependencies)),
            new Findings(
                UNMIGRATED_SCHEMA,
                SpringModelRules.unmigratedSchema(this.pom, this.dependencies, this.repackaged)
            ),
            new Findings(
                MISSING_ACTUATOR,
                SpringModelRules.missingActuator(this.pom, this.dependencies, this.repackaged)
            ),
            new Findings(TWO_WEB_STACKS, SpringModelRules.doubledWebStack(this.pom, this.dependencies)),
            new Findings(
                OWN_AUTO_CONFIGURATION,
                SpringModelRules.ownAutoConfiguration(this.declarations, this.repackaged)
            )
        );
    }

    /*
     * The registration files the module carries. Both names are read from the resource trees rather than
     * from the built archive, so that the answer arrives at validate, before the archive exists.
     */
    private static List<String> registered(Path root, Collection<Path> resourceRoots) {
        List<Path> resolved = resourceRoots.stream().map(root::resolve).map(Path::normalize).toList();
        return Repository.trackedFiles(root).stream()
            .filter(file -> resolved.stream().anyMatch(file::startsWith))
            .filter(file -> REGISTRATIONS.contains(file.getFileName().toString()))
            .map(file -> root.relativize(file).toString())
            .toList();
    }
}
