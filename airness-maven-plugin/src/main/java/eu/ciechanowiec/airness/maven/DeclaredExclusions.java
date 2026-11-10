package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.ciechanowiec.airness.governance.CoverageExclusions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.apache.maven.project.MavenProject;

/**
 * The coverage exclusions the project declares, one entry each, which the suppression ceilings count.
 *
 * <p>A Java exclusion is read from the project file that declares it, profiles included, rather than from the
 * effective model, so a parent's declaration counts once rather than once for every module that inherits it. A
 * node exclusion is read from every {@code package.json} of the node projects, workspace members included.
 */
@UtilityClass
final class DeclaredExclusions {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Every class pattern a project file declares in {@link CoverageExclusions#PROPERTY}.
     *
     * @param root     the repository root
     * @param projects every project of the build
     * @return one entry per pattern, naming the project file and the pattern
     */
    static List<String> java(Path root, Collection<MavenProject> projects) {
        return projects.stream().flatMap(project -> entries(root, project)).distinct().toList();
    }

    /**
     * Every file a node manifest lists under {@code airness.coverageExclude}.
     *
     * @param root        the repository root
     * @param directories the declared node project directories of every module
     * @return one entry per listed pattern, naming the manifest and the pattern
     */
    static List<String> node(Path root, Collection<Path> directories) {
        return directories.stream().distinct()
            .flatMap(directory -> NodeManifests.in(directory, List.of()).stream())
            .distinct()
            .flatMap(
                manifest -> excluded(manifest)
                    .map(pattern -> "%s: coverageExclude %s".formatted(root.relativize(manifest), pattern))
            ).toList();
    }

    private static Stream<String> entries(Path root, MavenProject project) {
        String file = Optional.ofNullable(project.getFile()).map(pom -> root.relativize(pom.toPath()).toString())
            .orElseGet(project::getId);
        return declared(project.getOriginalModel()).map(
            pattern -> "%s: coverage exclusion %s".formatted(file, pattern)
        );
    }

    private static Stream<String> declared(Model model) {
        Stream<Properties> profiles = model.getProfiles().stream().map(Profile::getProperties);
        return Stream.concat(Stream.of(model.getProperties()), profiles)
            .filter(Objects::nonNull)
            .map(properties -> properties.getProperty(CoverageExclusions.PROPERTY, ""))
            .flatMap(declared -> CoverageExclusions.patterns(declared).stream());
    }

    // A manifest that cannot be read adds nothing here: the node gates report it.
    private static Stream<String> excluded(Path manifest) {
        try {
            return JSON.readTree(manifest.toFile()).path("airness").path("coverageExclude").valueStream()
                .map(entry -> entry.path("pattern").asText("").strip())
                .filter(pattern -> !pattern.isEmpty());
        } catch (IOException _) {
            return Stream.empty();
        }
    }
}
