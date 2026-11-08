package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.TypographyScanCheck;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.ProjectBuilder;
import org.jspecify.annotations.Nullable;

/**
 * Every committable file uses plain ASCII typography: the hyphen rather than a dash, three periods
 * rather than an ellipsis character, and straight quotation marks. A file a generator owns, as the stack
 * layers, the {@code airness.generated.files} property or a node project's {@code airness.generatedArtefacts}
 * declare it, carries its generator's text and is left unread, since the agent runtime may not edit it.
 */
@Mojo(name = "typography", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class TypographyMojo extends AbstractRepositoryMojo {

    private static final List<String> SELF_FIXTURES = List.of(
        "airness-it/typography-fixture.txt"
    );

    /**
     * Repository-relative path prefixes to leave unread, comma-separated. A prefix is matched one whole
     * path segment at a time, so {@code src} leaves {@code src-generated} in the scan.
     *
     * <p>This defaults to nothing on purpose. A default naming the vendored directories a project
     * usually has would fail in a project that has none of them, since a prefix excluding nothing is
     * itself reported, and a harness whose first build fails on its own default teaches a consumer to
     * reach for the switch rather than the list.
     */
    @Parameter(property = "airness.typography.excludes")
    private @Nullable String excludes;

    /**
     * Repository-relative paths of further files a generator owns, comma-separated, which the scan leaves unread
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
    public TypographyMojo(ProjectBuilder builder) {
        this.builder = builder;
    }

    @Override
    List<Findings> findings() {
        Path root = this.repositoryRoot();
        List<String> owned = GeneratedDenialMojo.generated(
            root, RepositorySites.read(this.builder, this.session().getProjectBuildingRequest(), root),
            CommaList.entries(this.generated)
        );
        TypographyScanCheck check = new TypographyScanCheck(root, this.exclusions(), owned);
        this.getLog().info("Typography left " + check.generated() + " generated file(s) unread");
        check.skipped().forEach(
            (prefix, count) -> this.getLog().info(
                "Typography exemption " + prefix + " left " + count + " file(s) unread"
            )
        );
        this.getLog().info("Typography read " + check.scanned() + " tracked file(s)");
        Scope.requireRead(check.scanned(), "files", this.exclusions());
        return check.findings();
    }

    private List<String> exclusions() {
        Stream<String> configured = CommaList.entries(this.excludes).stream();
        Stream<String> fixtures = RepositoryProjects.selfTree(this.project(), this.repositoryRoot())
            ? SELF_FIXTURES.stream() : Stream.empty();
        return Stream.concat(configured, fixtures).distinct().toList();
    }
}
