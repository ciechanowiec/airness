package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.DeclaredContainerImage;
import eu.ciechanowiec.airness.governance.ManagedVersions;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import lombok.experimental.UtilityClass;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

/**
 * Reads Airness-owned container images from Maven's effective root properties.
 *
 * <p>The effective model matters here for the same reason it matters when Maven launches the image:
 * interpolation and inheritance have already produced the reference the build actually uses.
 */
@UtilityClass
final class VersionImages {

    static List<DeclaredContainerImage> from(MavenSession session) {
        return from(session.getTopLevelProject());
    }

    static List<DeclaredContainerImage> from(MavenProject project) {
        Properties properties = project.getProperties();
        return ManagedVersions.imageProperties().stream()
            .map(property -> declaration(property, properties))
            .toList();
    }

    private static DeclaredContainerImage declaration(String property, Properties properties) {
        String reference = Optional.ofNullable(properties.getProperty(property)).orElseThrow(
            () -> new IllegalStateException("Missing Airness-owned container property: " + property)
        );
        return new DeclaredContainerImage(property, reference);
    }
}
