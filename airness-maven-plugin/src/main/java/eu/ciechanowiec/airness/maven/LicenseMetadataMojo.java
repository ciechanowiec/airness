package eu.ciechanowiec.airness.maven;

import java.util.List;
import javax.inject.Inject;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.ProjectBuilder;

/**
 * Supplies verified missing metadata before the inherited license check evaluates project dependencies.
 */
@Mojo(
    name = "license-metadata", defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
    requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true
)
public final class LicenseMetadataMojo extends AbstractPreflightMojo {

    private final ProjectBuilder builder;

    /**
     * Reads supplier POMs with Maven's effective model builder.
     *
     * @param builder Maven model builder
     */
    @Inject
    public LicenseMetadataMojo(ProjectBuilder builder) {
        this.builder = builder;
    }

    @Override
    boolean applies() {
        return true;
    }

    @Override
    List<String> problems() {
        new MavenLicenseMetadata(this.builder, this.session().getProjectBuildingRequest()).prepare(this.project());
        return List.of();
    }
}
