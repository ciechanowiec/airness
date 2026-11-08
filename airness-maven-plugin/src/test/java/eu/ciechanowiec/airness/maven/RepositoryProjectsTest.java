package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

class RepositoryProjectsTest {

    private static final String GROUP = "eu.ciechanowiec";
    private static final String SAMPLE = "sample";
    private static final String APPLICATION = "application";
    private static final String AGGREGATOR = "airness";
    private static final String CONSUMER_PARENT = "airness-parent";
    private static final String JAVA_PARENT = "airness-parent-java";
    private static final String SPRING_PARENT = "airness-parent-spring-boot";

    @Test
    void selectsAnOrdinaryTopLevelProject() {
        MavenProject project = project(SAMPLE, APPLICATION);

        assertTrue(RepositoryProjects.owns(project, project));
    }

    @Test
    void selectsTheConsumerParentDuringTheAirnessSelfBuild() {
        MavenProject root = project(GROUP, AGGREGATOR);
        MavenProject parent = project(GROUP, CONSUMER_PARENT);
        parent.setParent(root);

        assertTrue(RepositoryProjects.owns(root, parent));
        assertTrue(RepositoryProjects.selfRepository(root));
        assertFalse(RepositoryProjects.selfRepository(parent));
    }

    @Test
    void recognizesTheConsumerParentBuiltOutsideTheReactor() {
        MavenProject root = project(GROUP, AGGREGATOR);
        MavenProject parent = project(GROUP, CONSUMER_PARENT);
        parent.setParent(root);

        assertTrue(RepositoryProjects.selfBuild(parent, parent));
    }

    @Test
    void rejectsAnOrdinaryChildModule() {
        MavenProject root = project(SAMPLE, APPLICATION);
        MavenProject child = project(SAMPLE, "module");

        assertFalse(RepositoryProjects.owns(root, child));
    }

    @Test
    void rejectsAnAirnessGroupBuildWithAnotherAggregator() {
        MavenProject root = project(GROUP, "another-aggregator");
        MavenProject parent = project(GROUP, CONSUMER_PARENT);
        parent.setParent(root);

        assertFalse(RepositoryProjects.owns(root, parent));
    }

    /*
     * The Spring parent inherits the consumer parent and so inherits its bindings, which is what would
     * otherwise make it a second owner of the repository-wide goals and report every finding twice.
     */
    @Test
    void leavesRepositoryWorkToTheConsumerParentWhenTheSpringParentBuilds() {
        MavenProject root = project(GROUP, AGGREGATOR);
        MavenProject spring = project(GROUP, SPRING_PARENT);
        spring.setParent(project(GROUP, CONSUMER_PARENT));

        assertFalse(RepositoryProjects.owns(root, spring));
    }

    /*
     * The Java parent inherits the generic one, so it inherits the repository-wide bindings as well. Only
     * the generic parent, whose own parent is the aggregator, owns them during the self-build.
     */
    @Test
    void leavesRepositoryWorkToTheConsumerParentWhenTheJavaParentBuilds() {
        MavenProject root = project(GROUP, AGGREGATOR);
        MavenProject java = project(GROUP, JAVA_PARENT);
        MavenProject parent = project(GROUP, CONSUMER_PARENT);
        parent.setParent(root);
        java.setParent(parent);

        assertFalse(RepositoryProjects.owns(root, java));
    }

    @Test
    void readsTheJavaParentAsAParentTheHarnessPublishes() {
        assertTrue(RepositoryProjects.harnessParent(project(GROUP, JAVA_PARENT)));
    }

    @Test
    void readsTheConsumerParentAsAParentTheHarnessPublishes() {
        assertTrue(RepositoryProjects.harnessParent(project(GROUP, CONSUMER_PARENT)));
    }

    @Test
    void readsTheSpringParentAsAParentTheHarnessPublishes() {
        assertTrue(RepositoryProjects.harnessParent(project(GROUP, SPRING_PARENT)));
    }

    @Test
    void readsAConsumingProjectAsNoParentTheHarnessPublishes() {
        assertFalse(RepositoryProjects.harnessParent(project(SAMPLE, APPLICATION)));
    }

    @Test
    void readsAnotherGroupsSameNamedProjectAsNoParentTheHarnessPublishes() {
        assertFalse(RepositoryProjects.harnessParent(project("com.example", CONSUMER_PARENT)));
    }

    @Test
    void refusesAPlainAggregatorAboveAGovernedModule() {
        MavenProject root = project(SAMPLE, APPLICATION);
        root.setExecutionRoot(true);
        MavenProject module = governed(project(SAMPLE, "service"), JAVA_PARENT);
        String refusal = RepositoryProjects.unowned(root, module).orElseThrow();
        assertTrue(refusal.startsWith("The top-level project sample:application inherits no Airness parent"), refusal);
        assertTrue(refusal.contains("inherit eu.ciechanowiec:airness-parent"), refusal);
    }

    @Test
    void acceptsEveryTopLevelProjectThatOwnsTheRepositoryWideChecks() {
        MavenProject module = governed(project(SAMPLE, "service"), JAVA_PARENT);
        MavenProject root = governed(project(SAMPLE, APPLICATION), CONSUMER_PARENT);
        assertEquals(Optional.empty(), RepositoryProjects.unowned(root, module), "a root inheriting the parent");
        assertEquals(Optional.empty(), RepositoryProjects.unowned(module, module), "a module built on its own");
        assertEquals(
            Optional.empty(), RepositoryProjects.unowned(project(GROUP, AGGREGATOR), module), "Airness itself"
        );
    }

    @Test
    void leavesAPartialBuildThatKeptNoRootUnjudged() {
        MavenProject kept = project(SAMPLE, "library");
        MavenProject module = governed(project(SAMPLE, "service"), JAVA_PARENT);
        assertEquals(
            Optional.empty(), RepositoryProjects.unowned(kept, module),
            "under -rf or -pl the first kept module stands in for the root, which says nothing about the root"
        );
    }

    private static MavenProject governed(MavenProject project, String parent) {
        MavenProject harness = project(GROUP, parent);
        if (!CONSUMER_PARENT.equals(parent)) {
            harness.setParent(project(GROUP, CONSUMER_PARENT));
        }
        project.setParent(harness);
        return project;
    }

    private static MavenProject project(String group, String artifact) {
        MavenProject project = new MavenProject();
        project.setGroupId(group);
        project.setArtifactId(artifact);
        return project;
    }
}
