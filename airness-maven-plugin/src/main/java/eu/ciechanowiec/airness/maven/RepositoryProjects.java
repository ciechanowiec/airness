package eu.ciechanowiec.airness.maven;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.maven.project.MavenProject;

/**
 * Selects the project that owns repository-wide work in an ordinary consumer and in Airness itself.
 */
@UtilityClass
final class RepositoryProjects {

    /*
     * Every parent Airness publishes for a consumer to inherit. Each one declares what a consumer may not,
     * so the child policy that reads a consumer pom passes over all of them. Kept apart from the generic
     * parent alone, which selects the one module that owns repository-wide work and stays one: a second
     * owner would report every repository finding twice.
     */
    private static final Set<String> HARNESS_PARENTS = Set.of(
        AirnessCoordinates.GENERIC_PARENT, AirnessCoordinates.JAVA_PARENT, "airness-parent-spring-boot",
        "airness-parent-node", "airness-parent-payload"
    );

    static boolean owns(MavenProject topLevel, MavenProject current) {
        return topLevel.equals(current) || selfBuild(topLevel, current);
    }

    static boolean selfBuild(MavenProject topLevel, MavenProject current) {
        return AirnessCoordinates.named(current, AirnessCoordinates.GENERIC_PARENT)
            && selfModule(current)
            && isAirnessEntry(topLevel, current);
    }

    /**
     * Whether the build is Airness's own reactor, whose library modules inherit its aggregator rather than an
     * Airness parent and are built ahead of the parents that close the build.
     *
     * @param topLevel the session's top-level project
     * @return whether that project is the Airness aggregator
     */
    static boolean selfRepository(MavenProject topLevel) {
        return AirnessCoordinates.isAggregator(topLevel);
    }

    /**
     * Whether a module belongs to the Airness build itself, whose modules inherit its aggregator directly
     * rather than the parent they publish.
     *
     * @param module the module
     * @return whether its parent is the Airness aggregator
     */
    static boolean selfModule(MavenProject module) {
        return Optional.ofNullable(module.getParent())
            .filter(AirnessCoordinates::isAggregator)
            .isPresent();
    }

    /**
     * Whether the working tree being built is Airness's own, whichever of its modules runs the goal and
     * wherever the build started.
     *
     * <p>The session cannot answer this: a build resumed with {@code -rf} or narrowed with {@code -pl} makes
     * the first module it kept the top-level project, so neither the aggregator nor the generic parent need
     * take part. The parent chain answers it in every build, since every Airness module descends from the
     * aggregator. A consumer descends from it too, through the parent it inherits, so the aggregator counts
     * only when its own pom lies at the root of the working tree rather than in the local repository.
     *
     * @param current the module being built
     * @param root    the working tree root
     * @return whether the aggregator above that module is the working tree's own root project
     */
    static boolean selfTree(MavenProject current, Path root) {
        Path tree = root.toAbsolutePath().normalize();
        return Stream.iterate(current, Objects::nonNull, MavenProject::getParent)
            .filter(AirnessCoordinates::isAggregator)
            .map(MavenProject::getBasedir)
            .filter(Objects::nonNull)
            .anyMatch(basedir -> tree.equals(basedir.toPath().toAbsolutePath().normalize()));
    }

    // The top-level project owns the repository-wide checks when it is this repository's own aggregator or
    // inherits an Airness parent somewhere up its chain.
    private static boolean ownsRepositoryChecks(MavenProject topLevel) {
        return selfRepository(topLevel)
            || Stream.iterate(topLevel, Objects::nonNull, MavenProject::getParent).anyMatch(
                RepositoryProjects::harnessParent
            );
    }

    /**
     * Why the repository-wide checks would run in no module of this build, when they would.
     *
     * <p>They run only in the session's top-level project. A plain aggregator above governed modules inherits
     * no Airness parent and binds none of them, so the secret scan, the history and managed-file checks and
     * the licence report would each pass over the build with nothing but a debug line. A build of one module
     * on its own is its own top-level project, and so is a repository without an aggregator.
     *
     * <p>Only a top-level project that is the execution root is judged. A build that leaves the root out, as
     * {@code -rf} and {@code -pl} do, makes the first module it kept the top-level project, which says nothing
     * about the root. Such a build is partial, and the complete build judges the root.
     *
     * @param topLevel the session's top-level project
     * @param current  the governed module being built
     * @return the refusal, naming the repair, or empty when some module owns the repository-wide checks
     */
    static Optional<String> unowned(MavenProject topLevel, MavenProject current) {
        boolean owned = !topLevel.isExecutionRoot() || topLevel.equals(current) || ownsRepositoryChecks(topLevel);
        return owned ? Optional.empty() : Optional.of(
            "The top-level project " + topLevel.getGroupId() + ':' + topLevel.getArtifactId() + " inherits no "
                + "Airness parent, so the repository-wide checks would run in no module of this build; make its "
                + "pom inherit " + AirnessCoordinates.GROUP + ':' + AirnessCoordinates.GENERIC_PARENT
                + ", as the agent guide requires of a monorepo root"
        );
    }

    static boolean harnessParent(MavenProject project) {
        return AirnessCoordinates.GROUP.equals(project.getGroupId())
            && HARNESS_PARENTS.contains(project.getArtifactId());
    }

    private static boolean isAirnessEntry(MavenProject topLevel, MavenProject current) {
        return topLevel.equals(current) || AirnessCoordinates.isAggregator(topLevel);
    }
}
