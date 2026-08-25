package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.PublicationContentCheck;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Inspects the complete file set required for a Maven Central JAR publication.
 */
@Mojo(name = "publication-content", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public final class PublicationContentMojo extends AbstractPublicationMojo {

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.jar", readonly = true)
    private String artifact;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}-sources.jar", readonly = true)
    private String sources;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}-javadoc.jar", readonly = true)
    private String javadocs;

    @Override
    boolean applies() {
        return JarPackaging.produced(this.project().getPackaging());
    }

    @Override
    List<Findings> findings() {
        List<Path> files = List.of(
            this.project().getFile().toPath(), Path.of(this.artifact), Path.of(this.sources), Path.of(this.javadocs)
        );
        return new PublicationContentCheck(files, this.repositoryRoot()).findings();
    }
}
