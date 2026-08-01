package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.TypographyScanCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Every committable file uses plain ASCII typography: the hyphen rather than a dash, three periods
 * rather than an ellipsis character, and straight quotation marks.
 */
@Mojo(name = "typography", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class TypographyMojo extends RepositoryMojo {

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
    protected List<Findings> findings() {
        TypographyScanCheck check = new TypographyScanCheck(
            this.repositoryRoot(), Sentinel.optional(this.excludes)
        );
        check.skipped().forEach(
            (prefix, count) -> this.getLog().info("Typography exemption " + prefix + " left " + count + " file(s) unread")
        );
        return check.findings();
    }
}
