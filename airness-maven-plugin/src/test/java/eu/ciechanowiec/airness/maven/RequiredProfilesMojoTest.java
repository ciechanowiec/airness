package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

class RequiredProfilesMojoTest {

    @Test
    void reportsRequiredActivationAndDeactivationInRequestOrder() {
        assertEquals(
            List.of("missing-active", "missing-inactive"),
            RequiredProfilesMojo.missingProfiles(
                List.of(project("available")), List.of(),
                List.of("missing-active", "missing-active"), List.of("missing-inactive")
            )
        );
    }

    @Test
    void acceptsProfilesFromEveryMavenModelSource() {
        MavenProject child = project("child-profile");
        MavenProject parent = project("parent-profile");
        child.setParent(parent);
        child.setInjectedProfileIds("external", List.of("injected-profile"));
        Profile settings = profile("settings-profile");

        assertEquals(
            List.of(),
            RequiredProfilesMojo.missingProfiles(
                List.of(child), List.of(settings),
                List.of("child-profile", "parent-profile", "injected-profile"),
                List.of("settings-profile")
            )
        );
    }

    @Test
    void acceptsADeclaredProfileThatIsNotActive() {
        assertEquals(
            List.of(),
            RequiredProfilesMojo.missingProfiles(
                List.of(project("format")), List.of(), List.of(), List.of("format")
            )
        );
    }

    @Test
    void delegatesRequiredAndOptionalProfileSemanticsToMavenFour() {
        assertAll(
            () -> assertFalse(RequiredProfilesMojo.nativeValidation("3.9.16")),
            () -> assertTrue(RequiredProfilesMojo.nativeValidation("4.0.0-rc-6"))
        );
    }

    private static MavenProject project(String... profiles) {
        Model model = new Model();
        List<Profile> declared = Stream.of(profiles)
            .map(RequiredProfilesMojoTest::profile)
            .toList();
        model.setProfiles(declared);
        MavenProject project = new MavenProject(model);
        project.setOriginalModel(model);
        return project;
    }

    private static Profile profile(String id) {
        Profile profile = new Profile();
        profile.setId(id);
        return profile;
    }
}
