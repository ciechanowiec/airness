package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Requires current-build coverage evidence whenever a module has production Java sources.
 */
@Mojo(name = "coverage-evidence", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true)
public final class CoverageEvidenceMojo extends AbstractGovernanceMojo {

    @Parameter(property = "jacoco.dataFile", defaultValue = "${project.build.directory}/jacoco.exec")
    private String dataFile;

    @Override
    boolean applies() {
        return this.moduleProductionSourceRoots().stream().anyMatch(CoverageEvidenceMojo::containsJava);
    }

    @Override
    List<Findings> findings() {
        Path evidence = Path.of(this.dataFile);
        boolean current = Files.isRegularFile(evidence)
            && modified(evidence) >= this.session().getStartTime().getTime();
        List<String> offences = current ? List.of() : List.of(
            evidence + " is missing or predates this build; production code requires tests from this run"
        );
        return List.of(new Findings("Missing current-build JaCoCo evidence", offences));
    }

    private static boolean containsJava(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.anyMatch(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not inspect production sources under " + root, exception);
        }
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the age of " + file, exception);
        }
    }
}
