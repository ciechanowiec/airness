package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.IndentationCheck;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.ProjectBuilder;
import org.jspecify.annotations.Nullable;

/**
 * Every committable text file indents as the {@code .editorconfig} it answers to declares: with the whitespace
 * {@code indent_style} names, and in a YAML file by steps of {@code indent_size}. A file a generator owns, as the
 * stack layers, the {@code airness.generated.files} property or a node project's {@code airness.generatedArtefacts}
 * declare it, is left unread, and so is a path the file linter's configuration exempts by role.
 */
@Mojo(name = "indentation", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class IndentationMojo extends AbstractRepositoryMojo {

    /**
     * Repository-relative paths of further files a generator owns, comma-separated, which the check leaves unread
     * beside the files the stack layers and the node projects declare generated.
     */
    @Parameter(property = "airness.generated.files")
    private @Nullable String generated;

    private final ProjectBuilder builder;

    /**
     * Receives the builder that reads the models of every module in the repository.
     *
     * @param builder the Maven project builder
     */
    @Inject
    public IndentationMojo(ProjectBuilder builder) {
        this.builder = builder;
    }

    @Override
    List<Findings> findings() {
        Path root = this.repositoryRoot();
        List<String> owned = GeneratedDenialMojo.generated(
            root, RepositorySites.read(this.builder, this.session().getProjectBuildingRequest(), root),
            Sentinel.optional(this.generated)
        );
        LinterExemptions exemptions = LinterExemptions.from(this.project());
        IndentationCheck check = new IndentationCheck(root, owned, exemptions::exempts);
        this.getLog().info("Indentation left " + check.generated() + " generated file(s) unread");
        this.getLog().info(
            "Indentation exempts the " + exemptions.patterns().size() + " role pattern(s) the file linter excludes"
        );
        this.getLog().info("Indentation read " + check.scanned() + " tracked file(s)");
        Scope.requireRead(check.scanned(), "files", exemptions.patterns());
        return check.findings();
    }
}
