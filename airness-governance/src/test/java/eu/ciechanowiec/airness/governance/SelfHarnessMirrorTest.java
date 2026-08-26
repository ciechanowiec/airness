package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

/**
 * The harness holds itself to what it publishes, and writes each shared list once.
 *
 * <p>Airness cannot inherit the parent it publishes: {@code airness-parent} depends on
 * {@code airness-annotations}, and a module cannot both be depended on by its own parent and inherit it.
 * The root project file therefore binds the harness to its own modules directly, and what a consumer
 * receives it receives through {@code airness-parent}. Two audiences, one repository.
 *
 * <p>A list serving both audiences is written in the pluginManagement of the root project file and
 * nowhere else. Writing it a second time below that file is what this class forbids. Two copies drift,
 * and the drift is silent and one-sided: a consumer goes on being held to a rule the repository
 * publishing it has quietly stopped answering to, and every report on both sides stays green. Holding
 * the copies equal instead, which is what stood here before, catches the drift but leaves the reader
 * two answers to one question and leaves each new entry to be written twice.
 *
 * <p>How a second copy combines with the first is a further reason not to write one. Whether it
 * replaces the list above it or adds to it depends on a {@code combine.self} attribute, so a copy that
 * is correct and a copy that doubles every entry differ by an attribute that is invisible when absent.
 */
class SelfHarnessMirrorTest {

    private static final String ENFORCER = "maven-enforcer-plugin";
    private static final String LICENCES = "license-maven-plugin";
    private static final List<String> EVERY_MODULE = List.of(
        "airness-self-enforce-dependencies",
        "airness-self-analyze-dependencies",
        "airness-self-check-dependency-licenses",
        "airness-self-known-vulnerabilities"
    );
    private static final List<Map.Entry<String, List<String>>> ROOT_ONLY = List.of(
        Map.entry(ENFORCER, List.of("rules")),
        Map.entry(LICENCES, List.of("includedLicenses", "licenseMerges"))
    );
    private static final List<String> WITH_PRODUCTION_JAVA = List.of(
        "airness-self-rewrite-verify",
        "airness-self-pmd",
        "airness-self-spotbugs",
        "airness-self-coverage-agent",
        "airness-self-coverage-report",
        "airness-self-coverage-check"
    );

    @Test
    void buildsTheConfigurationModuleFirst() {
        assertEquals(
            "airness-config", ProjectFiles.modules().getFirst(),
            "a module built before airness-config cannot carry the analyzer configurations it supplies"
        );
    }

    @Test
    void holdsEveryModuleToTheDependencyRulesAConsumerGets() {
        assertEquals(
            List.of(), absent(EVERY_MODULE, ProjectFiles.moduleFiles()),
            "a module binding none of these resolves its dependencies under no rule at all"
        );
    }

    @Test
    void holdsEveryModuleWithProductionJavaToTheAnalyzers() {
        assertEquals(
            List.of(), absent(WITH_PRODUCTION_JAVA, ProjectFiles.withProductionJava()),
            "production Java answers to the analyzers and the coverage floor in this repository too"
        );
    }

    @Test
    void givesEveryModuleWithProductionJavaSomethingThatTestsIt() {
        assertEquals(
            List.of(),
            ProjectFiles.withProductionJava().stream()
                .filter(pom -> !Files.isDirectory(pom.resolveSibling("src/test/java")))
                .map(ProjectFiles::moduleName)
                .toList(),
            "a coverage floor is met by tests, so a module with none of them meets it by having nothing"
        );
    }

    /*
     * Scoped to the plugin, because rules is a tag several plugins use: the coverage floors of every
     * module that holds production Java are written in one, under jacoco-maven-plugin, and are the
     * module's own business rather than a copy of anything.
     */
    @Test
    void leavesTheSharedListsToTheOneFileThatWritesThem() {
        Stream<Path> below = Stream.concat(
            Stream.of(ProjectFiles.parentPom()), ProjectFiles.moduleFiles().stream()
        );
        assertEquals(
            List.of(), below.flatMap(SelfHarnessMirrorTest::rewritten).toList(),
            "a list written twice is a list that drifts from the one above it, and reads as two answers"
        );
    }

    private static List<String> absent(Collection<String> required, Collection<Path> poms) {
        return poms.stream()
            .flatMap(
                pom -> required.stream()
                    .filter(id -> !executionIds(pom).contains(id))
                    .map(id -> ProjectFiles.moduleName(pom) + " is missing " + id)
            )
            .toList();
    }

    private static List<String> executionIds(Path pom) {
        return ProjectFiles.descendants(ProjectFiles.document(pom), "execution")
            .map(execution -> Xml.text(execution, "id").orElse(""))
            .toList();
    }

    private static Stream<String> rewritten(Path pom) {
        return ROOT_ONLY.stream().flatMap(
            shared -> ProjectFiles.descendants(ProjectFiles.document(pom), "plugin")
                .filter(plugin -> shared.getKey().equals(Xml.text(plugin, "artifactId").orElse("")))
                .flatMap(
                    plugin -> shared.getValue().stream()
                        .filter(parameter -> written(plugin, parameter))
                        .map(
                            parameter -> "%s writes %s of %s".formatted(
                                ProjectFiles.moduleName(pom), parameter, shared.getKey()
                            )
                        )
                )
        );
    }

    private static boolean written(Element plugin, String parameter) {
        return ProjectFiles.descendants(plugin, parameter).findAny().isPresent();
    }
}
