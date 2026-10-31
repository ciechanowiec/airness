package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.StylesheetSelectorCheck;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

/**
 * A selector a module writes selects what it names, whatever the line it was wrapped onto.
 *
 * <p>This runs per module rather than once for the repository, for the same reason template-parse
 * does: the resources it reads are the module's own, and a module that declares no resource directory
 * and no node project is passed over. The checked source roots of the module's node projects are read
 * beside its resources, so a stylesheet moved from the Java resources into a frontend tooling project
 * keeps this rule.
 */
@Mojo(name = "stylesheet-selectors", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class StylesheetSelectorsMojo extends AbstractGovernanceMojo {

    /**
     * The module's node project directories, comma-separated and relative to the module.
     */
    @Parameter(property = "airness.node.projects")
    private @Nullable String declared;

    @Override
    boolean applies() {
        return !this.roots().isEmpty();
    }

    @Override
    List<Findings> findings() {
        StylesheetSelectorCheck check = new StylesheetSelectorCheck(this.repositoryRoot(), this.roots());
        this.getLog().info("Stylesheet selectors read " + check.scanned() + " stylesheet(s)");
        return check.findings();
    }

    private List<Path> roots() {
        return Stream.concat(
            this.moduleResourceRoots().stream(),
            NodeSourceRoots.of(NodeProjects.of(this.project().getBasedir().toPath(), this.declared)).stream()
        ).distinct().toList();
    }
}
