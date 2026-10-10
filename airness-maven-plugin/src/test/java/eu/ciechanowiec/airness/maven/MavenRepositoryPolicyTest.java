package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.AssetCatalogue;
import eu.ciechanowiec.airness.governance.AssetSync;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.settings.Mirror;
import org.eclipse.aether.repository.MirrorSelector;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MavenRepositoryPolicyTest {

    private static final String SETTINGS = ".mvn/settings.xml";
    private static final String CONFIGURATION = ".mvn/maven.config";
    private static final String CENTRAL = "https://repo.maven.apache.org/maven2/";

    @TempDir
    private Path directory;

    @BeforeEach
    void materializesTheActualShippedFiles() {
        new AssetSync(this.directory, catalogue(), List.of()).write();
    }

    @Test
    void acceptsTheManagedFilesAndEffectiveCatchAllMirror() {
        assertEquals(List.of(), this.policy(this.request(), selector(mirror())).problems());
    }

    @ParameterizedTest
    @ValueSource(strings = {CONFIGURATION, SETTINGS})
    @SneakyThrows
    void rejectsMissingStartupFiles(String path) {
        Files.delete(this.directory.resolve(path));

        assertTrue(this.problems().stream().anyMatch(problem -> problem.startsWith(path + " must match")));
    }

    @ParameterizedTest
    @ValueSource(strings = {CONFIGURATION, SETTINGS})
    @SneakyThrows
    void rejectsStartupFilesThatNoLongerMatchTheHarness(String path) {
        Files.writeString(this.directory.resolve(path), "changed\n");

        assertTrue(this.problems().stream().anyMatch(problem -> problem.startsWith(path + " must match")));
    }

    @Test
    @SneakyThrows
    void rejectsAnAlternateGlobalSettingsPathEvenWithTheSameBytes() {
        Path alternate = Files.copy(this.directory.resolve(SETTINGS), this.directory.resolve("alternate.xml"));
        MavenExecutionRequest request = this.request().setGlobalSettingsFile(alternate.toFile());

        assertTrue(
            this.policy(request, selector(mirror())).problems().stream()
                .anyMatch(problem -> problem.contains("remove the -gs/--global-settings override"))
        );
    }

    @Test
    void rejectsARequestWithoutGlobalSettings() {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest().setMirrors(List.of(mirror()));

        assertTrue(
            this.policy(request, selector(mirror())).problems().stream()
                .anyMatch(problem -> problem.contains("Maven must load the Airness global settings"))
        );
    }

    @Test
    @SneakyThrows
    void acceptsAnAliasForTheSameGlobalSettingsFile() {
        Path alias = Files.createSymbolicLink(this.directory.resolve("alias.xml"), this.directory.resolve(SETTINGS));
        MavenExecutionRequest request = this.request().setGlobalSettingsFile(alias.toFile());

        assertEquals(List.of(), this.policy(request, selector(mirror())).problems());
    }

    @Test
    void rejectsAnAbsentSelectedSettingsFile() {
        MavenExecutionRequest request = this.request()
            .setGlobalSettingsFile(this.directory.resolve("missing.xml").toFile());

        assertTrue(
            this.policy(request, selector(mirror())).problems().stream()
                .anyMatch(problem -> problem.contains("Maven must load the Airness global settings"))
        );
    }

    @Test
    void rejectsAnAdditionalMirrorThatCouldOverrideTheWildcard() {
        Mirror extra = mirror();
        extra.setId("another-mirror");
        extra.setMirrorOf("one-dependency-repository");
        MavenExecutionRequest request = this.request().setMirrors(List.of(mirror(), extra));

        assertTrue(
            this.policy(request, selector(mirror())).problems().stream()
                .anyMatch(problem -> problem.contains("Effective Maven settings must contain only"))
        );
    }

    @Test
    void rejectsARequestWithoutMirrors() {
        MavenExecutionRequest request = this.request().setMirrors(List.of());

        assertTrue(
            this.policy(request, selector(mirror())).problems().stream()
                .anyMatch(problem -> problem.contains("Effective Maven settings must contain only"))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "url", "repositories", "layouts", "layout", "blocked"})
    void rejectsAWeakenedEffectiveMirror(String property) {
        Mirror changed = changed(property);
        MavenExecutionRequest request = this.request().setMirrors(List.of(changed));

        assertTrue(
            this.policy(request, selector(changed)).problems().stream()
                .anyMatch(problem -> problem.contains("Effective Maven settings must contain only"))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "url", "layout", "blocked"})
    void rejectsResolverRoutingThatDisagreesWithTheEffectiveSettings(String property) {
        assertTrue(
            this.policy(this.request(), selector(changed(property))).problems().stream()
                .anyMatch(problem -> problem.contains("active Maven resolver does not route"))
        );
    }

    @Test
    void rejectsAResolverThatDoesNotApplyAnyMirror() {
        assertTrue(
            this.policy(this.request(), new DefaultMirrorSelector()).problems().stream()
                .anyMatch(problem -> problem.contains("active Maven resolver does not route"))
        );
    }

    private List<String> problems() {
        return this.policy(this.request(), selector(mirror())).problems();
    }

    private MavenRepositoryPolicy policy(MavenExecutionRequest request, MirrorSelector selector) {
        return new MavenRepositoryPolicy(this.directory, request, selector, catalogue());
    }

    private MavenExecutionRequest request() {
        return new DefaultMavenExecutionRequest()
            .setGlobalSettingsFile(this.directory.resolve(SETTINGS).toFile())
            .setMirrors(List.of(mirror()));
    }

    private static AssetCatalogue catalogue() {
        return new AssetCatalogue(MavenRepositoryPolicyTest.class.getClassLoader());
    }

    private static MirrorSelector selector(Mirror mirror) {
        return new DefaultMirrorSelector().add(
            mirror.getId(), mirror.getUrl(), mirror.getLayout(), false, mirror.isBlocked(),
            mirror.getMirrorOf(), mirror.getMirrorOfLayouts()
        );
    }

    private static Mirror mirror() {
        Mirror mirror = new Mirror();
        mirror.setId("airness-central");
        mirror.setUrl(CENTRAL);
        mirror.setMirrorOf("*");
        mirror.setMirrorOfLayouts("*");
        return mirror;
    }

    private static Mirror changed(String property) {
        Mirror mirror = mirror();
        Map<String, Consumer<Mirror>> changes = Map.of(
            "id", changed -> changed.setId("other"),
            "url", changed -> changed.setUrl("https://unapproved.invalid/maven/"),
            "repositories", changed -> changed.setMirrorOf("central"),
            "layouts", changed -> changed.setMirrorOfLayouts("default"),
            "layout", changed -> changed.setLayout("legacy"),
            "blocked", changed -> changed.setBlocked(true)
        );
        Objects.requireNonNull(changes.get(property), "Unknown mirror property: " + property).accept(mirror);
        return mirror;
    }
}
