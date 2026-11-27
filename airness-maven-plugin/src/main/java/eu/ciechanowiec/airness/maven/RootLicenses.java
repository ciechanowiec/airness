package eu.ciechanowiec.airness.maven;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * The license names the repository's root pom declares, which every {@code package.json} in the repository repeats.
 *
 * <p>The root pom is the project's one license statement. A module below it reaches it up its parent chain. A
 * module that inherits an Airness parent directly does not, so its root pom is read from the file instead.
 */
@UtilityClass
final class RootLicenses {

    /**
     * The license names the root pom declares itself, in the order it lists them.
     *
     * @param module the module being built
     * @param root   the repository root
     * @return the names, none when the root pom declares no license or there is no root pom
     */
    static List<String> of(MavenProject module, Path root) {
        Path tree = root.toAbsolutePath().normalize();
        return Stream.iterate(module, Objects::nonNull, MavenProject::getParent)
            .filter(project -> Optional.of(tree).equals(directory(project)))
            .map(MavenProject::getOriginalModel)
            .filter(Objects::nonNull)
            .findFirst()
            .or(() -> read(tree.resolve(RepositorySites.POM)))
            .map(RootLicenses::names)
            .orElse(List.of());
    }

    private static Optional<Path> directory(MavenProject project) {
        return Optional.ofNullable(project.getBasedir())
            .map(File::toPath)
            .map(path -> path.toAbsolutePath().normalize());
    }

    private static Optional<Model> read(Path pom) {
        if (!Files.isRegularFile(pom)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(pom)) {
            return Optional.of(new MavenXpp3Reader().read(reader));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + pom, exception);
        } catch (XmlPullParserException exception) {
            throw new IllegalStateException("Could not parse " + pom, exception);
        }
    }

    private static List<String> names(Model model) {
        return model.getLicenses().stream()
            .map(License::getName)
            .filter(Objects::nonNull)
            .map(String::strip)
            .filter(name -> !name.isEmpty())
            .toList();
    }
}
