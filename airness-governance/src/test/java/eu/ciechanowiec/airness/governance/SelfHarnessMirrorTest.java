package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The two project files that carry the harness say the same thing.
 *
 * <p>Airness cannot inherit the parent it publishes: {@code airness-parent} depends on
 * {@code airness-annotations}, and a module cannot both be depended on by its own parent and inherit it.
 * So the rules a consumer receives from {@code airness-parent} are mirrored in the root project file for
 * the modules that build the harness, and a mirror is a thing that drifts. When it drifts the failure is
 * silent and one-sided: a consumer goes on being held to a rule that the repository publishing it has
 * quietly stopped answering to, and every report on both sides stays green.
 *
 * <p>What is compared is what the two say rather than how they are written. Two copies of a rule list
 * can differ in whitespace and in comments and still be one contract.
 */
class SelfHarnessMirrorTest {

    private static final String CONFIGURATION = "configuration";
    private static final String ENFORCER = "maven-enforcer-plugin";
    private static final String LICENCES = "license-maven-plugin";
    private static final List<String> EVERY_MODULE = List.of(
        "airness-self-enforce-dependencies",
        "airness-self-analyze-dependencies",
        "airness-self-check-dependency-licenses",
        "airness-self-known-vulnerabilities"
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

    @Test
    void mirrorsTheDependencyRulesTheParentBinds() {
        Element parent = ProjectFiles.child(
            ProjectFiles.execution(ProjectFiles.parentPom(), "airness-enforce-dependencies"),
            CONFIGURATION, "rules"
        );
        Element root = ProjectFiles.child(
            ProjectFiles.managed(ProjectFiles.rootPom(), ENFORCER), CONFIGURATION, "rules"
        );
        assertEquals(
            tagsOf(parent), tagsOf(root),
            "the rules a consumer is held to and the rules this repository is held to are one list"
        );
    }

    @Test
    void mirrorsTheLicenceAllowlistTheParentBinds() {
        Element parent = ProjectFiles.child(
            ProjectFiles.execution(ProjectFiles.parentPom(), "airness-check-dependency-licenses"),
            CONFIGURATION, "includedLicenses"
        );
        Element root = ProjectFiles.child(
            ProjectFiles.managed(ProjectFiles.rootPom(), LICENCES), CONFIGURATION, "includedLicenses"
        );
        assertEquals(
            allowedIn(parent), allowedIn(root),
            "a licence this repository accepts for itself is one it accepts for a consumer, and no more"
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

    private static List<String> tagsOf(Node rules) {
        return ProjectFiles.elementChildren(rules).map(Element::getTagName).sorted().toList();
    }

    private static List<String> allowedIn(Node licences) {
        return Xml.children(licences, "includedLicense").stream()
            .map(Element::getTextContent)
            .map(String::strip)
            .sorted()
            .toList();
    }
}
