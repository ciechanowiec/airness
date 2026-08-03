package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.EntryFileCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * {@code AGENTS.md} holds instructions and {@code CLAUDE.md} contains only {@code @AGENTS.md}.
 */
@Mojo(name = "entry-files", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class EntryFilesMojo extends RepositoryMojo {

    @Override
    protected List<Findings> findings() {
        return new EntryFileCheck(this.repositoryRoot()).findings();
    }
}
