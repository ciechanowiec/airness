package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.eclipse.aether.DefaultSessionData;
import org.eclipse.aether.SessionData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the small support objects that guard Maven-session and agent-material scope.
 */
class HarnessSupportTest {

    @TempDir
    private Path directory;

    @Test
    void readsTheManagedAgentMaterialFromTheRealPluginClasspath() {
        String instructions = new AgentMaterials(AgentMaterials.class.getClassLoader()).instructions();
        assertTrue(instructions.startsWith("<!-- BEGIN AIRNESS MANAGED INSTRUCTIONS -->"));
    }

    @Test
    void failsWhenAgentMaterialIsMissingOrBecomesUnreadable() {
        URL[] empty = {};
        assertThrows(
            IllegalStateException.class,
            () -> new AgentMaterials(new URLClassLoader(empty, null)).instructions()
        );
        assertThrows(UncheckedIOException.class, this::readDeletedMaterial);
    }

    @Test
    void refusesAnEmptyJavaSourceScope() {
        assertThrows(IllegalStateException.class, () -> Scope.requireJavaSources(0, "source roots"));
        assertDoesNotThrow(() -> Scope.requireJavaSources(1, "source roots"));
    }

    @Test
    void namesEveryPackagingWhoseBuildLeavesAJar() {
        assertTrue(JarPackaging.produced("jar"), "the ordinary case");
        assertTrue(JarPackaging.produced("maven-plugin"), "a plugin is a jar with a descriptor in it");
        assertTrue(JarPackaging.produced("bundle"), "and so is an OSGi bundle");
        assertFalse(
            JarPackaging.produced("pom"),
            "an aggregator packages nothing, so a goal that reads a jar has nothing to read here"
        );
    }

    @Test
    void admitsEachGoalAndScopeOnlyOncePerSession() {
        SessionData data = new DefaultSessionData();
        assertTrue(OncePerSession.firstRun(data, HarnessSupportTest.class, "first"));
        assertFalse(OncePerSession.firstRun(data, HarnessSupportTest.class, "first"));
        assertTrue(OncePerSession.firstRun(data, HarnessSupportTest.class, "second"));
        assertTrue(OncePerSession.firstRun(data, AgentMaterials.class, "first"));
        assertTrue(OncePerSession.firstRun(data, HarnessSupportTest.class));
        assertFalse(OncePerSession.firstRun(data, HarnessSupportTest.class));
    }

    @Test
    void comparesImmutableTreeSnapshotsWithinOneSession() {
        SessionData data = new DefaultSessionData();
        assertThrows(IllegalStateException.class, () -> TreeState.unchanged(data, "module", "first"));
        TreeState.snapshot(data, "module", "first");
        assertTrue(TreeState.unchanged(data, "module", "first"));
        assertFalse(TreeState.unchanged(data, "module", "second"));
        TreeState.snapshot(data, "module", "second");
        assertTrue(TreeState.unchanged(data, "module", "second"));
        assertTrue(TreeState.scope(Path.of("pom.xml")).endsWith("pom.xml"));
    }

    @Test
    void fingerprintsARealRepositoryThroughSessionData() {
        SessionData data = new DefaultSessionData();
        Path root = repository();
        TreeState.snapshot(data, root, "repository");
        assertTrue(TreeState.unchanged(data, root, "repository"));
    }

    @SneakyThrows
    private void readDeletedMaterial() {
        Path material = Files.writeString(this.directory.resolve("instructions.md"), "instructions\n");
        URL location = material.toUri().toURL();
        AgentMaterials agent = new AgentMaterials(Optional.of(location));
        Files.delete(material);
        agent.instructions();
    }

    private static Path repository() {
        Path current = Path.of("").toAbsolutePath();
        return Stream.of(current, current.getParent())
            .filter(path -> Files.exists(path.resolve("airness-parent/pom.xml")))
            .findFirst()
            .orElseThrow();
    }
}
