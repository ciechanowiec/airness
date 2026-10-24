package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

/**
 * Every {@code package.json} in the module is a node project a gate checks, and every declared node project
 * exists.
 *
 * <p>Runs in every module during Default and Extended verification, whether or not the module declares a
 * node project, because the module that declares nothing is the one where a new, undeclared frontend would
 * otherwise go unchecked. Only Skip mode turns it off.
 */
@Mojo(name = "node-projects", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public final class NodeProjectsMojo extends AbstractGovernanceMojo {

    /**
     * The module's node project directories, comma-separated and relative to the module.
     */
    @Parameter(property = "airness.node.projects")
    private @Nullable String declared;

    @Override
    boolean applies() {
        return !RepositoryProjects.harnessParent(this.project());
    }

    @Override
    List<Findings> findings() {
        Path basedir = this.project().getBasedir().toPath();
        NodeProjects projects = NodeProjects.of(basedir, this.declared);
        NodeProjectCoverage coverage = new NodeProjectCoverage(basedir, projects, this.skipped(basedir));
        this.getLog().info(
            "Node projects: " + projects.projects().size() + " declared, " + coverage.found()
                + " package.json file(s) found in " + this.project().getArtifactId()
        );
        return coverage.findings();
    }

    // A child module walks its own directory in its own build, and the build directory holds output.
    private List<Path> skipped(Path basedir) {
        Stream<Path> modules = this.project().getModules().stream()
            .map(module -> basedir.resolve(module).normalize())
            .map(module -> Files.isRegularFile(module) ? module.resolveSibling("") : module);
        return Stream.concat(modules, Stream.of(Path.of(this.project().getBuild().getDirectory()))).toList();
    }
}
