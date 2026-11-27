package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import eu.ciechanowiec.airness.governance.MetadataSource;
import eu.ciechanowiec.airness.governance.ModuleIdentity;
import eu.ciechanowiec.airness.governance.ProjectMetadata;
import eu.ciechanowiec.airness.governance.ProjectOwnership;
import eu.ciechanowiec.airness.governance.ScmCoordinates;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apache.maven.model.Developer;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.Organization;
import org.apache.maven.model.Scm;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectFactsTest {

    private static final String GROUP = "eu.ciechanowiec";
    private static final String SAMPLE = "org.sample";
    private static final String WEB = "web";

    @Test
    void copiesEveryMetadataValueAModelStates() {
        Model model = new Model();
        model.setName(" Task Tracker ");
        model.setDescription("Tracks tasks.");
        model.setUrl("https://git.sample.org/tracker");
        model.setInceptionYear("2025");
        model.addLicense(license("LicenseRef-Proprietary"));
        Developer developer = new Developer();
        developer.setName("Sample Organization");
        model.addDeveloper(developer);
        Organization organization = new Organization();
        organization.setName("Sample Organization");
        model.setOrganization(organization);
        Scm scm = new Scm();
        scm.setUrl("https://git.sample.org/tracker");
        scm.setConnection("scm:git:https://git.sample.org/tracker.git");
        scm.setDeveloperConnection("scm:git:https://git.sample.org/tracker.git");
        model.setScm(scm);
        assertEquals(
            new ProjectMetadata(
                "Task Tracker", "Tracks tasks.", "https://git.sample.org/tracker", "2025",
                new ProjectOwnership(
                    List.of("LicenseRef-Proprietary"), List.of("Sample Organization"),
                    "Sample Organization"
                ),
                new ScmCoordinates(
                    "https://git.sample.org/tracker", "scm:git:https://git.sample.org/tracker.git",
                    "scm:git:https://git.sample.org/tracker.git"
                )
            ),
            ProjectFacts.metadata(model)
        );
    }

    @Test
    void readsEveryValueAModelLeavesOutAsEmpty() {
        Model model = new Model();
        model.addDeveloper(new Developer());
        model.setOrganization(new Organization());
        model.setScm(new Scm());
        assertEquals(
            new ProjectMetadata(
                "", "", "", "", new ProjectOwnership(List.of(), List.of(""), ""), new ScmCoordinates("", "", "")
            ),
            ProjectFacts.metadata(model)
        );
    }

    @Test
    void readsAModelWithoutSourceControlOrOrganizationAsEmpty() {
        assertEquals(
            new ProjectMetadata(
                "", "", "", "", new ProjectOwnership(List.of(), List.of(), ""), new ScmCoordinates("", "", "")
            ),
            ProjectFacts.metadata(new Model())
        );
    }

    @Test
    void passesOverAnAirnessPomWhoseOwnModelMavenDidNotKeep() {
        MavenProject aggregator = project(GROUP, "airness", null);
        MavenProject module = project(SAMPLE, WEB, null);
        module.setParent(aggregator);
        assertEquals(List.of(), ProjectFacts.harness(module));
    }

    @Test
    void readsTheModelVersionTheModuleWritesAndItsDirectory(@TempDir Path directory) {
        MavenProject project = project(SAMPLE, WEB, null);
        project.setVersion("1.0.0");
        Model raw = new Model();
        raw.setModelVersion("4.0.0");
        project.setOriginalModel(raw);
        project.setFile(new File(directory.resolve(WEB).toFile(), "pom.xml"));
        assertEquals(
            new ModuleIdentity("4.0.0", SAMPLE, WEB, "1.0.0", WEB, false),
            ProjectFacts.identity(project, false)
        );
        assertEquals("", ProjectFacts.identity(project(SAMPLE, "bare", null), true).modelVersion());
    }

    @Test
    void readsTheOwnMetadataOfEveryAirnessPomUpTheChainAndNoOther() {
        MavenProject aggregator = project(GROUP, "airness", described("The harness"));
        MavenProject generic = project(GROUP, "airness-parent", described("The generic parent"));
        generic.setParent(aggregator);
        MavenProject company = project(SAMPLE, "company-parent", described("The company parent"));
        company.setParent(generic);
        MavenProject module = project(SAMPLE, WEB, described("The web module"));
        module.setParent(company);
        assertEquals(
            List.of("The generic parent", "The harness"),
            ProjectFacts.harness(module).stream().map(ProjectMetadata::description).toList()
        );
    }

    @Test
    void namesTheNearestPomThatDeclaresEachInheritedField() {
        Model aggregatorModel = described("The harness");
        aggregatorModel.addLicense(license("MIT-0"));
        aggregatorModel.setUrl("https://github.com/ciechanowiec/airness");
        MavenProject aggregator = project(GROUP, "airness", aggregatorModel);
        MavenProject java = project(GROUP, "airness-parent-java", described("The Java parent"));
        java.setParent(aggregator);
        Model own = new Model();
        own.setUrl("https://git.sample.org/web");
        MavenProject module = project(SAMPLE, WEB, own);
        module.setParent(java);
        MavenProject unread = new MavenProject();
        unread.setGroupId(SAMPLE);
        unread.setArtifactId("unread");
        unread.setParent(module);
        assertEquals(
            List.of(
                new MetadataSource("description", "eu.ciechanowiec:airness-parent-java", true),
                new MetadataSource("licenses", "eu.ciechanowiec:airness", true),
                new MetadataSource("url", "org.sample:web", false)
            ),
            ProjectFacts.sources(unread)
        );
    }

    @Test
    void passesOverAPomThatDeclaresNoFieldToTheOneThatDeclaresEach() {
        Model full = new Model();
        full.setDescription("Tracks tasks.");
        full.setUrl("https://git.sample.org/tracker");
        full.setInceptionYear("2025");
        full.addLicense(license("LicenseRef-Proprietary"));
        Developer developer = new Developer();
        developer.setName("Sample Organization");
        full.addDeveloper(developer);
        Organization organization = new Organization();
        organization.setName("Sample Organization");
        full.setOrganization(organization);
        Scm scm = new Scm();
        scm.setUrl("https://git.sample.org/tracker");
        scm.setConnection("scm:git:https://git.sample.org/tracker.git");
        scm.setDeveloperConnection("scm:git:https://git.sample.org/tracker.git");
        full.setScm(scm);
        MavenProject root = project(SAMPLE, "tracker", full);
        MavenProject module = project(SAMPLE, WEB, new Model());
        module.setParent(root);
        assertEquals(
            List.of(
                "description", "developers", "inceptionYear", "licenses", "organization", "scm/connection",
                "scm/developerConnection", "scm/url", "url"
            ),
            ProjectFacts.sources(module).stream().map(MetadataSource::field).toList()
        );
        assertEquals(
            List.of("org.sample:tracker"),
            ProjectFacts.sources(module).stream().map(MetadataSource::coordinate).distinct().toList()
        );
    }

    private static MavenProject project(String group, String artifact, @Nullable Model raw) {
        MavenProject project = new MavenProject();
        project.setGroupId(group);
        project.setArtifactId(artifact);
        Optional.ofNullable(raw).ifPresent(project::setOriginalModel);
        return project;
    }

    private static Model described(String description) {
        Model model = new Model();
        model.setDescription(description);
        return model;
    }

    private static License license(String name) {
        License license = new License();
        license.setName(name);
        return license;
    }
}
