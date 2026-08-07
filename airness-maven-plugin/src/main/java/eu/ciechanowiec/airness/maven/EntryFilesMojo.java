package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.EntryFileCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * {@code AGENTS.md} starts with the managed Airness section and {@code CLAUDE.md} contains only
 * {@code @AGENTS.md}.
 */
@Mojo(name = "entry-files", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class EntryFilesMojo extends AbstractRepositoryMojo {

    @Override
    List<Findings> findings() {
        return new EntryFileCheck(
            this.repositoryRoot(), new AgentMaterials(EntryFilesMojo.class.getClassLoader()).instructions()
        ).findings();
    }
}
