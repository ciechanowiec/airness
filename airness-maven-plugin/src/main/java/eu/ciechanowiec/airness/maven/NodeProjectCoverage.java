package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.maven.NodeProjects.NodeProject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Every {@code package.json} in a module belongs to a node project the module declares, and every declared
 * project has one.
 *
 * <p>Without this, a frontend placed in a folder nobody declared would pass every check, because no check
 * would look at it. So the walk runs in every module, including one that declares nothing: that module
 * adding its first {@code package.json} is exactly the case to catch. A {@code package.json} is covered when
 * its directory is declared, or when a declared pnpm workspace root lists it as a member, since the gates of
 * that root check every member.
 *
 * <p>The files read are the ones a commit would carry, so build output git ignores is passed over, and so is
 * every directory a child module owns, which that module's own build reads. No node project may choose a
 * pnpm or Node of its own either, since the gates must run on the harness's.
 */
final class NodeProjectCoverage {

    private static final String HEADLINE = "Node projects the module declares, and package.json files no gate checks";
    private static final String PROPERTY = "airness.node.projects";

    private final int found;
    private final List<String> offences;

    /**
     * Walks one module.
     *
     * @param basedir  the module directory
     * @param declared the node projects the module declares
     * @param skipped  the directories the walk does not enter: child modules and the build directory
     */
    NodeProjectCoverage(Path basedir, NodeProjects declared, Collection<Path> skipped) {
        Path module = basedir.toAbsolutePath().normalize();
        List<Path> manifests = NodeManifests.in(
            module, skipped.stream().map(path -> path.toAbsolutePath().normalize()).toList()
        );
        List<PnpmWorkspace> workspaces = declared.projects().stream()
            .filter(project -> !project.outside())
            .flatMap(project -> PnpmWorkspace.at(project.directory()).stream())
            .toList();
        this.found = manifests.size();
        this.offences = Stream.of(
            declared.projects().stream().flatMap(project -> declarationProblem(project).stream()),
            manifests.stream()
                .filter(manifest -> !covered(manifest.resolveSibling(""), declared, workspaces))
                .map(manifest -> module.relativize(manifest).toString().replace('\\', '/'))
                .map(manifest -> manifest + " is a node project that no gate checks; add it to " + PROPERTY),
            NodeSelections.problems(module, manifests, declared)
        ).flatMap(problems -> problems).toList();
    }

    /**
     * How many {@code package.json} files the walk found, which the caller logs so a clean verdict states
     * its reach.
     *
     * @return the number of manifests found
     */
    int found() {
        return this.found;
    }

    /**
     * The verdict over the declaration and the walk.
     *
     * @return the single verdict this check produces
     */
    List<Findings> findings() {
        return List.of(new Findings(HEADLINE, this.offences));
    }

    private static Optional<String> declarationProblem(NodeProject project) {
        if (project.outside()) {
            return Optional.of(
                project.key() + " lies outside the module; list only directories inside it in " + PROPERTY
            );
        }
        return Optional.of(project.key() + " has no package.json; fix " + PROPERTY).filter(_ -> !project.hasManifest());
    }

    private static boolean covered(Path directory, NodeProjects declared, Collection<PnpmWorkspace> workspaces) {
        return declared.projects().stream().anyMatch(project -> project.directory().equals(directory))
            || workspaces.stream().anyMatch(workspace -> workspace.covers(directory));
    }
}
