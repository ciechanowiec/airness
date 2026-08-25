package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ArtifactContentCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Inspects the completed JAR for content that source-tree checks cannot see.
 */
@Mojo(name = "artifact-content", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class ArtifactContentMojo extends AbstractGovernanceMojo {

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.jar", readonly = true)
    private String artifact;

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    private String mainOutput;

    @Parameter(defaultValue = "${project.build.testOutputDirectory}", readonly = true)
    private String testOutput;

    @Override
    boolean applies() {
        return JarPackaging.produced(this.project().getPackaging());
    }

    @Override
    List<Findings> findings() {
        Path jar = Path.of(this.artifact);
        if (!Files.isRegularFile(jar)) {
            return List.of(new Findings("Missing packaged JAR", List.of(jar + " does not exist")));
        }
        return new ArtifactContentCheck(
            jar, Path.of(this.mainOutput), Path.of(this.testOutput), this.repositoryRoot()
        ).findings();
    }
}
