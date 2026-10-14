package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.ciechanowiec.airness.governance.ReportPackage;
import eu.ciechanowiec.airness.governance.ReportUse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.SneakyThrows;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Profile;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportGraphTest {

    @TempDir
    private Path directory;

    @Test
    void excludesUnresolvedProvidedToolNodesFromTheInventoryGraph() {
        Map<String, List<String>> graph = Map.of(
            "plugin", List.of("runtime", "provided"), "provided", List.of("runtime")
        );
        assertEquals(Map.of("plugin", List.of("runtime")), ReportGraph.selected(graph, Set.of("plugin", "runtime")));
    }

    @Test
    void preservesResolvedTrailEdgesWithoutPromotingEveryTransitiveDependencyToDirect() {
        MavenProject project = InputProjects.project(this.directory);
        Artifact artifact = new DefaultArtifact(
            "example", "leaf", "1", "test", "jar", "",
            new DefaultArtifactHandler("jar")
        );
        artifact.setDependencyTrail(
            List.of("example:root:jar:1", "example:middle:jar:1:test", "example:leaf:jar:1:test")
        );
        project.setResolvedArtifacts(Set.of(artifact));
        assertEquals(
            Map.of(
                "example:root:jar:1", List.of("example:middle:jar:1"),
                "example:middle:jar:1", List.of("example:leaf:jar:1")
            ), ReportGraph.project(project)
        );
    }

    @Test
    void readsRealResolverNodesAndBoundsCycles() {
        DefaultDependencyNode root = node("root");
        DefaultDependencyNode child = node("child");
        root.setChildren(List.of(child));
        child.setChildren(List.of(root));
        assertEquals(
            Map.of(
                "example:root:jar:1", List.of("example:child:jar:1"),
                "example:child:jar:1", List.of("example:root:jar:1")
            ), ReportGraph.plugin(root)
        );
        assertEquals(List.of("first", "second"), ReportGraph.union(List.of("second"), List.of("first", "second")));
    }

    @Test
    @SneakyThrows
    void refusesAProductionDependencyOmittedFromThePublishedSbom() {
        ReportPackage production = new ReportPackage(
            "example:library:jar:1", List.of("MIT"),
            List.of(new ReportUse("root", "provided", "Direct")), Map.of()
        );
        ObjectMapper mapper = new ObjectMapper();
        assertThrows(
            IllegalStateException.class,
            () -> ReportSbom.validate(mapper.readTree("{\"components\":[]}"), List.of(production))
        );
        ReportSbom.validate(
            mapper.readTree(
                """
                    {"components":[{"group":"example","name":"library","version":"1"}]}
                    """
            ), List.of(production)
        );
        ReportPackage test = new ReportPackage(
            production.coordinate(), production.licenses(),
            List.of(new ReportUse("root", "test", "Direct")), Map.of()
        );
        ReportSbom.validate(mapper.readTree("{}"), List.of(test));
        assertFalse(test.production());
    }

    @Test
    void usesTheUnselectedExecutionRootForPartialReports() {
        MavenProject root = project("root");
        MavenProject child = project("child");
        root.setExecutionRoot(true);
        assertEquals(root, ReportProjects.root(List.of(child, root)));
        assertEquals(child, ReportProjects.root(List.of(child)));
    }

    @Test
    void recognizesExtendedProfilesInheritedFromTheParent() {
        MavenProject inherited = project("consumer");
        assertFalse(ReportProjects.extended(inherited));
        inherited.setInjectedProfileIds("parent", List.of("extended"));
        assertTrue(ReportProjects.extended(inherited));
        MavenProject declared = project("root");
        Profile profile = new Profile();
        profile.setId("extended");
        declared.setActiveProfiles(List.of(profile));
        assertTrue(ReportProjects.extended(declared));
    }

    @Test
    void selectsCompletedSelfBuildModulesWithoutPublishingAtTheRoot() {
        MavenProject root = project("airness");
        MavenProject first = project("airness-governance");
        MavenProject owner = project("airness-parent");
        assertEquals(List.of(first, owner), ReportProjects.ready(List.of(root, first), owner, true));
        assertEquals(List.of(owner), ReportProjects.ready(List.of(first), owner, false));
        assertEquals(first, ReportProjects.executed(first));
        assertTrue(ReportProjects.qualifications(false, "true").isEmpty());
        assertEquals(2, ReportProjects.qualifications(true, "false").size());
        assertTrue(ReportProjects.partial(List.of(owner), List.of(root, owner)));
        assertFalse(ReportProjects.partial(List.of(owner), List.of(owner)));
    }

    @Test
    @SneakyThrows
    void carriesDependencyEdgesAndModuleComponentsInTheAggregateInventory() {
        ReportModule module = new ReportModule(
            "example:root:pom:1", List.of(), List.of(), Map.of(
                "Graph",
                "{\"example:root:pom:1\":[]}"
            )
        );
        String json = ReportSbom.assemble(List.of(), List.of(module));
        assertEquals(
            "example:root:pom:1", new ObjectMapper().readTree(json).path("dependencies").path(0).path("ref").asText()
        );
        assertTrue(json.contains("\"bom-ref\":\"example:root:pom:1\""));
    }

    private static DefaultDependencyNode node(String name) {
        return ReportMavenFixture.node("example:" + name + ":1");
    }

    private static MavenProject project(String name) {
        MavenProject project = new MavenProject();
        project.setGroupId("eu.ciechanowiec");
        project.setArtifactId(name);
        project.setVersion("1");
        return project;
    }
}
