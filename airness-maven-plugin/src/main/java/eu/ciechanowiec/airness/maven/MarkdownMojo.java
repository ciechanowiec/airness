package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.MarkdownCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Every Markdown document of the committable tree holds to the structure its readers rely on.
 */
@Mojo(name = "markdown", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class MarkdownMojo extends AbstractRepositoryMojo {

    @Override
    List<Findings> findings() {
        MarkdownCheck check = new MarkdownCheck(this.repositoryRoot());
        this.getLog().info("Markdown documents read " + check.scanned() + " Markdown file(s)");
        return check.findings();
    }
}
