package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

/**
 * Which parent binds which rule, written out so that moving a rule between them is a visible decision.
 *
 * <p>The generic parent carries what makes sense in a repository with no code, and the Java parent adds
 * what needs Java. A binding moved from one file to the other leaves every consumer of the Java parent
 * with the same effective build, so no other test notices, while a repository on the generic parent alone
 * silently gains or loses the rule. Each entry is a bound plugin and goal, or a bound plugin alone where it
 * runs through its packaging defaults, read from the builds and profiles but not from pluginManagement.
 */
class ParentPlacementTest {

    private static final String AIRNESS = "airness-maven-plugin:";

    @Test
    void bindsOnlyRepositoryRulesInTheGenericParent() {
        Set<String> expected = new TreeSet<>(
            Stream.concat(
                Stream.of(
                    "actions", "assets-check", "blocklist", "build-inputs", "build-practices", "check-parameters",
                    "checkov", "commit-history", "commit-typography", "compliance-report-collect",
                    "dependency-freshness", "docker", "docs", "editorconfig-excludes", "entry-files", "environment",
                    "generated-denial", "image-assets", "license-metadata", "linear-history", "node-dependencies",
                    "node-gate", "node-install", "node-projects", "plugin-inputs",
                    "publication-content", "publication-metadata", "repository-policy", "require-full-history",
                    "require-online", "required-profiles", "scan-secrets", "shellcheck", "skills",
                    "suppression-budget", "tree-snapshot", "tree-verify", "typography",
                    /*
                     * Java checks bound here only for their position: a child parent's verify executions run
                     * after the compliance report this file binds. Both skip a module with nothing to read.
                     */
                    "coverage-evidence", "artifact-content"
                ).map(AIRNESS::concat),
                Stream.of(
                    "dependency-check-maven", "dependency-check-maven:check", "editorconfig-maven-plugin:check",
                    "license-maven-plugin:add-third-party", "maven-enforcer-plugin:enforce"
                )
            ).toList()
        );
        assertEquals(expected, bound(ProjectFiles.parentPom()));
    }

    @Test
    void bindsTheJavaRulesInTheJavaParent() {
        Set<String> expected = new TreeSet<>(
            Stream.concat(
                Stream.of(
                    "build-inputs", "comment-prose", "compiler-parameters", "javadoc-links", "message-parity",
                    "package-cycles", "prepare-test-evidence", "qodana", "source-formatting",
                    "stylesheet-selectors", "template-calls", "template-expressions", "template-fragments",
                    "template-links", "template-output", "template-parse", "template-reach",
                    "template-replacements", "template-scope", "test-assertions", "web-accessibility"
                ).map(AIRNESS::concat),
                Stream.of(
                    "jacoco-maven-plugin:check", "jacoco-maven-plugin:prepare-agent", "jacoco-maven-plugin:report",
                    "maven-checkstyle-plugin:check", "maven-compiler-plugin", "maven-dependency-plugin:analyze-only",
                    "maven-dependency-plugin:properties", "maven-pmd-plugin:check", "maven-pmd-plugin:cpd-check",
                    "maven-surefire-plugin", "prettier-maven-plugin:${airness.prettier.goal}",
                    "spotbugs-maven-plugin:check"
                )
            ).toList()
        );
        assertEquals(expected, bound(ProjectFiles.javaParentPom()));
    }

    @Test
    void bindsNothingInTheNodeParent() {
        assertEquals(Set.of(), bound(ProjectFiles.nodeParentPom()));
    }

    @Test
    void makesTheModuleItselfTheNodeProjectInTheNodeParent() {
        assertEquals(
            List.of("."),
            ProjectFiles.descendants(ProjectFiles.document(ProjectFiles.nodeParentPom()), "airness.node.projects")
                .map(Element::getTextContent)
                .map(String::strip)
                .toList(),
            "the node parent changes one default: the module's own directory is its node project"
        );
    }

    @Test
    void bindsEveryNodeGateOnceInTheGenericParent() {
        List<String> gates = ProjectFiles.descendants(ProjectFiles.document(ProjectFiles.parentPom()), "execution")
            .filter(ParentPlacementTest::runsNodeGate)
            .map(ParentPlacementTest::idAtPhase)
            .toList();
        assertEquals(
            List.of(
                "node-gate-wiring@prepare-package", "node-gate-npm-inventory@prepare-package", "node-gate-types@verify",
                "node-gate-biome@verify", "node-gate-oxlint@verify", "node-gate-eslint@verify",
                "node-gate-source-rules@verify", "node-gate-config-refs@verify",
                "node-gate-javascript-allowlist@verify", "node-gate-arch@verify", "node-gate-type-coverage@verify",
                "node-gate-knip@verify", "node-gate-tests@verify", "node-gate-install-scripts@verify",
                "node-gate-release-age@verify"
            ),
            gates,
            "wiring judges the harness before npm-inventory writes evidence the package-phase policies read"
        );
    }

    private static String idAtPhase(Element execution) {
        return Xml.text(execution, "id").orElseThrow() + '@' + Xml.text(execution, "phase").orElseThrow();
    }

    private static boolean runsNodeGate(Element execution) {
        return ProjectFiles.descendants(execution, "goal")
            .anyMatch(goal -> "node-gate".equals(goal.getTextContent().strip()));
    }

    private static Set<String> bound(Path pom) {
        return plugins(ProjectFiles.document(pom))
            .flatMap(ParentPlacementTest::entries)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Stream<Element> plugins(Element from) {
        return ProjectFiles.elementChildren(from)
            .filter(child -> !"pluginManagement".equals(child.getTagName()))
            .flatMap(
                child -> "plugin".equals(child.getTagName()) ? Stream.of(child) : plugins(child)
            );
    }

    private static Stream<String> entries(Element plugin) {
        String artifact = Xml.text(plugin, "artifactId").orElseThrow();
        List<String> goals = ProjectFiles.descendants(plugin, "goal")
            .map(Element::getTextContent)
            .map(String::strip)
            .toList();
        return goals.isEmpty() ? Stream.of(artifact) : goals.stream().map(goal -> artifact + ':' + goal);
    }
}
