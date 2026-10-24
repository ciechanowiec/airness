package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.EntryFileCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * {@code AGENTS.md} holds instructions, and no {@code CLAUDE.md} exists anywhere in the working tree. Its
 * managed blocks are checked by {@code assets-check} with the other managed files.
 */
@Mojo(name = "entry-files", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class EntryFilesMojo extends AbstractRepositoryMojo {

    @Override
    List<Findings> findings() {
        return new EntryFileCheck(this.repositoryRoot()).findings();
    }
}
