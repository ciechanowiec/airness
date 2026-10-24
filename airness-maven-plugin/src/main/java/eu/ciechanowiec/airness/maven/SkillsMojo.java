package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.SkillManifestCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Every agent skill manifest carries the frontmatter an agent needs to discover and invoke the skill.
 */
@Mojo(name = "skills", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class SkillsMojo extends AbstractRepositoryMojo {

    @Override
    List<Findings> findings() {
        SkillManifestCheck check = new SkillManifestCheck(this.repositoryRoot());
        this.getLog().info("Agent skill manifests read " + check.scanned() + " SKILL.md file(s)");
        return check.findings();
    }
}
