package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.InstructionReferenceCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Every repository file and npm script an {@code AGENTS.md} names still exists.
 */
@Mojo(name = "instruction-references", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class InstructionReferencesMojo extends AbstractRepositoryMojo {

    @Override
    List<Findings> findings() {
        InstructionReferenceCheck check = new InstructionReferenceCheck(this.repositoryRoot(), PackageManifests::in);
        this.getLog().info("Agent documentation references read " + check.scanned() + " AGENTS.md file(s)");
        return check.findings();
    }
}
