package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import eu.ciechanowiec.airness.governance.DeclaredContainerImage;
import java.util.List;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

/**
 * Image discovery reads the effective Maven properties and keeps the property beside its reference for
 * actionable update output.
 */
class VersionImagesTest {

    @Test
    void readsEveryAirnessOwnedImageProperty() {
        MavenProject project = new MavenProject();
        project.getProperties().setProperty("gitleaks.image", "example/gitleaks:1.2@sha256:digest");
        project.getProperties().setProperty("qodana.image", "example/qodana:2.3@sha256:digest");

        assertEquals(
            List.of(
                new DeclaredContainerImage("gitleaks.image", "example/gitleaks:1.2@sha256:digest"),
                new DeclaredContainerImage("qodana.image", "example/qodana:2.3@sha256:digest")
            ),
            VersionImages.from(project)
        );
    }

    @Test
    void rejectsAMissingOwnedImageProperty() {
        MavenProject project = new MavenProject();
        project.getProperties().setProperty("gitleaks.image", "example/gitleaks:1.2@sha256:digest");

        assertThrows(IllegalStateException.class, () -> VersionImages.from(project));
    }
}
