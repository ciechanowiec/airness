package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.PublicationContentCheck;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

/**
 * Inspects the complete file set required for a Maven Central JAR publication.
 */
@Mojo(name = "publication-content", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public final class PublicationContentMojo extends AbstractPublicationMojo {

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.jar", readonly = true)
    private @Nullable String artifact;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}-sources.jar", readonly = true)
    private @Nullable String sources;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}-javadoc.jar", readonly = true)
    private @Nullable String javadocs;

    @Override
    boolean applies() {
        return JarPackaging.produced(this.project().getPackaging());
    }

    @Override
    List<Findings> findings() {
        List<Path> files = List.of(
            this.project().getFile().toPath(), Path.of(this.artifact()), Path.of(this.sources()),
            Path.of(this.javadocs())
        );
        return new PublicationContentCheck(files, this.repositoryRoot()).findings();
    }

    private String artifact() {
        return Objects.requireNonNull(this.artifact, "Maven did not inject the artifact path");
    }

    private String sources() {
        return Objects.requireNonNull(this.sources, "Maven did not inject the sources path");
    }

    private String javadocs() {
        return Objects.requireNonNull(this.javadocs, "Maven did not inject the Javadoc path");
    }
}
