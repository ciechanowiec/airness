package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.ImageAssetCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Every image the repository carries decodes, so none reaches a browser truncated, corrupt or empty.
 */
@Mojo(name = "image-assets", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class ImageAssetsMojo extends AbstractRepositoryMojo {

    @Override
    List<Findings> findings() {
        ImageAssetCheck check = new ImageAssetCheck(this.repositoryRoot());
        this.getLog().info("Image integrity read " + check.scanned() + " image(s)");
        return check.findings();
    }
}
