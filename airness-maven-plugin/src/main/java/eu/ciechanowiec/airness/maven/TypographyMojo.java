package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.TypographyScanCheck;
import java.util.List;
import java.util.stream.Stream;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Every committable file uses plain ASCII typography: the hyphen rather than a dash, three periods
 * rather than an ellipsis character, and straight quotation marks.
 */
@Mojo(name = "typography", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class TypographyMojo extends AbstractRepositoryMojo {

    private static final List<String> SELF_FIXTURES = List.of(
        ".vale/styles/LanguageNeutral/NoCurlyQuotes.yml",
        ".vale/styles/LanguageNeutral/NoDashes.yml",
        ".vale/styles/LanguageNeutral/NoUnicodeEllipsis.yml",
        "airness-it/typography-fixture.txt"
    );

    /**
     * Repository-relative path prefixes to leave unread, comma-separated.
     *
     * <p>This defaults to nothing on purpose. A default naming the vendored directories a project
     * usually has would fail in a project that has none of them, since a prefix excluding nothing is
     * itself reported, and a harness whose first build fails on its own default teaches a consumer to
     * reach for the switch rather than the list.
     */
    @Parameter(property = "airness.typography.excludes")
    private String excludes;

    @Override
    List<Findings> findings() {
        TypographyScanCheck check = new TypographyScanCheck(
            this.repositoryRoot(), this.exclusions()
        );
        check.skipped().forEach(
            (prefix, count) -> this.getLog().info(
                "Typography exemption " + prefix + " left " + count + " file(s) unread"
            )
        );
        return check.findings();
    }

    private List<String> exclusions() {
        Stream<String> configured = Sentinel.optional(this.excludes).stream();
        Stream<String> fixtures = RepositoryProjects.selfBuild(
            this.session().getTopLevelProject(), this.project()
        ) ? SELF_FIXTURES.stream() : Stream.empty();
        return Stream.concat(configured, fixtures).distinct().toList();
    }
}
