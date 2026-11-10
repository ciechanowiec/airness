package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.model.Activation;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

/**
 * Format mode, which switches the tree comparisons off, is what the invocation selected and never what a project's
 * own model makes active.
 */
class ReportProjectsTest {

    @Test
    void aFormatProfileActiveByDefaultInTheProjectsModelIsNotFormatMode() {
        Profile declared = new Profile();
        declared.setId(ReportProjects.FORMAT);
        Activation always = new Activation();
        always.setActiveByDefault(true);
        declared.setActivation(always);
        Model model = new Model();
        model.addProfile(declared);
        MavenProject project = new MavenProject(model);
        project.setActiveProfiles(List.of(declared));
        assertTrue(
            ReportProjects.active(project, ReportProjects.FORMAT),
            "the project's own profile is active for it, which is what made the comparisons switch off"
        );
        assertFalse(
            ReportProjects.formatting(new DefaultMavenExecutionRequest()),
            "an invocation that named no format profile keeps both tree comparisons on"
        );
    }

    @Test
    void theInvocationSelectsFormatModeAndCanDeselectIt() {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.addActiveProfile(ReportProjects.FORMAT);
        assertTrue(ReportProjects.formatting(request), "-Pformat is format mode");
        request.addInactiveProfile(ReportProjects.FORMAT);
        assertFalse(ReportProjects.formatting(request), "-P!format deselects it again");
        MavenExecutionRequest other = new DefaultMavenExecutionRequest();
        other.addActiveProfile("formatting");
        assertFalse(ReportProjects.formatting(other), "only the id itself selects format mode");
    }
}
