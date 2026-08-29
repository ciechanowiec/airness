package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.SpringDependency;
import eu.ciechanowiec.airness.governance.SpringModelCheck;
import java.util.List;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * The Spring questions the Maven model answers on its own.
 *
 * <p>Bound at {@code validate} rather than at {@code package}, which is the one place these part company
 * with the other Spring goals. Every question here is settled before a compiler runs, so asking later
 * would mean building the archive in order to be told that it should not have been built that way.
 *
 * <p>It is still a governance goal rather than a preflight one, so {@code airness.enforce} withholds the
 * failure and prints the report. A preflight goal refuses that switch because a misconfigured harness
 * makes every later verdict a lie. These are verdicts about the project, and a project taking the
 * harness on has the same reason to read them before it has to satisfy them as it has anywhere else.
 */
@Mojo(name = "spring-model", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public final class SpringModelMojo extends AbstractGovernanceMojo {

    private static final String DEPLOYED = "a module the Boot plugin repackages";
    private static final String LIBRARY = "a module that is not repackaged";

    @Override
    List<Findings> findings() {
        SpringModelCheck check = new SpringModelCheck(
            this.project().getFile().toPath(), this.moduleResourceRoots(), this.declared(),
            BootRepackaging.applies(this.project().getBuildPlugins())
        );
        this.getLog().info(
            "Spring model read " + check.scanned() + " declared dependency(ies) of "
                + (check.repackaged() ? DEPLOYED : LIBRARY)
        );
        return check.findings();
    }

    private List<SpringDependency> declared() {
        return this.project().getDependencies().stream().map(SpringModelMojo::dependency).toList();
    }

    private static SpringDependency dependency(Dependency declared) {
        return new SpringDependency(declared.getGroupId(), declared.getArtifactId(), declared.isOptional());
    }
}
