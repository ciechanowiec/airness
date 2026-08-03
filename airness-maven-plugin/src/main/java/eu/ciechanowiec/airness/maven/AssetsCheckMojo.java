package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.AssetCatalogue;
import eu.ciechanowiec.airness.governance.AssetCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * The files the harness owns are where their policy says, with the bytes it ships.
 *
 * <p>The lifecycle runs {@code airness:assets-sync} before this read-only check. Calling this goal by
 * name remains useful when a project wants a verdict without first repairing the files.
 */
@Mojo(name = "assets-check", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public final class AssetsCheckMojo extends AbstractRepositoryMojo {

    /**
     * Repository-relative paths this project has taken over, comma-separated.
     *
     * <p>Say why beside each one. Nothing checks that you did, which is worth stating rather than
     * leaving to be assumed, and an opt-out whose reason nobody wrote down is one nobody can retire.
     */
    @Parameter(property = "airness.assets.unmanaged")
    private String unmanaged;

    @Override
    List<Findings> findings() {
        List<String> exempt = Sentinel.optional(this.unmanaged);
        exempt.forEach(path -> this.getLog().info("This project owns " + path + " rather than the harness"));
        return new AssetCheck(
            this.repositoryRoot(), new AssetCatalogue(AssetsCheckMojo.class.getClassLoader()), exempt
        ).findings();
    }
}
