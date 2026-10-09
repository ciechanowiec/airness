package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.PublicationContentCheck;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

/**
 * Inspects the POM, SBOM, and archives required for Maven publication.
 */
@Mojo(name = "publication-content", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public final class PublicationContentMojo extends AbstractPublicationMojo {

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.jar", readonly = true)
    private @Nullable String artifact;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}-sources.jar", readonly = true)
    private @Nullable String sources;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}-javadoc.jar", readonly = true)
    private @Nullable String javadocs;

    @Parameter(defaultValue = "${project.build.directory}/bom.json", readonly = true)
    private @Nullable String sbom;

    @Override
    boolean applies() {
        return JarPackaging.produced(this.project().getPackaging()) || "pom".equals(this.project().getPackaging());
    }

    @Override
    List<Findings> findings() {
        List<Path> files = Stream.concat(
            Stream.of(this.project().getFile().toPath(), Path.of(this.sbom())), this.archives()
        ).toList();
        return new PublicationContentCheck(files, this.repositoryRoot()).findings();
    }

    private Stream<Path> archives() {
        return JarPackaging.produced(this.project().getPackaging())
            ? Stream.of(Path.of(this.artifact()), Path.of(this.sources()), Path.of(this.javadocs()))
            : Stream.empty();
    }

    private String sbom() {
        return Objects.requireNonNull(this.sbom, "Maven did not inject the SBOM path");
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
