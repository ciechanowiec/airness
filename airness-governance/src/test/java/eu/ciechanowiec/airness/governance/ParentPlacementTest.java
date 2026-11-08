package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

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

    private static final String AIRNESS_PLUGIN = "airness-maven-plugin";
    private static final String AIRNESS = AIRNESS_PLUGIN + ':';
    private static final String PROFILE = "profile";
    private static final String PACKAGE = "package";
    /*
     * The Airness goals a profile or a child parent binds at package. They merge after the generic build's own
     * executions, so each runs after the linter's exclusions are listed again and before the linter reads the
     * tree, and one that wrote an ignored file outside the build directory would put it in front of the linter.
     * A project's own Airness executions are refused at prepare-package and later, so none of them can join.
     */
    private static final Map<String, String> BETWEEN_LISTING_AND_LINTER = Map.of(
        "extended:airness-governance-extended@package",
        "the history and secret checks read the repository, the scanner through a read-only mount",
        "extended:airness-qodana@package", "the inspection engine writes its results under target/qodana"
    );

    @Test
    void bindsOnlyRepositoryRulesInTheGenericParent() {
        Set<String> expected = new TreeSet<>(
            Stream.concat(
                Stream.of(
                    "actions", "assets-check", "blocklist", "build-inputs", "build-practices",
                    "checkov", "commit-history", "commit-typography", "completion-order",
                    "dependency-freshness", "docker", "editorconfig-excludes", "entry-files", "environment",
                    "generated-denial", "image-assets", "instruction-references", "license-metadata", "linear-history",
                    "module-complete",
                    "node-dependencies", "node-gate", "node-install", "node-projects", "npm-dependencies",
                    "plugin-inputs", "project-settings",
                    "publication-content", "publication-metadata", "repository-policy", "require-full-history",
                    "require-online", "required-profiles", "scan-secrets", "shellcheck", "skills",
                    "tree-snapshot", "tree-verify", "typography",
                    /*
                     * Java checks bound here only for their position: a child parent's verify executions run
                     * after module-complete, which this file binds last. Both skip a module with nothing to read.
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
                    "assertions", "build-inputs", "comment-prose", "compiler-parameters", "javadoc-links",
                    "message-parity", "package-cycles", "prepare-test-evidence", "qodana", "source-formatting",
                    "stylesheet-selectors", "template-calls", "template-expressions", "template-fragments",
                    "template-inline", "template-links", "template-output", "template-parse", "template-reach",
                    "template-replacements", "template-scope", "web-accessibility", "web-interface"
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
                "node-gate-wiring@prepare-package", "node-gate-npm-inventory@prepare-package",
                "node-gate-types@verify", "node-gate-biome@verify",
                "node-gate-oxlint@verify", "node-gate-eslint@verify",
                "node-gate-source-rules@verify", "node-gate-config-refs@verify",
                "node-gate-javascript-allowlist@verify", "node-gate-css@verify",
                "node-gate-tailwind-tokens@verify", "node-gate-arch@verify",
                "node-gate-type-coverage@verify", "node-gate-knip@verify",
                "node-gate-tests@verify", "node-gate-install-scripts@verify",
                "node-gate-release-age@verify", "extended:node-gate-npm-sbom@prepare-package",
                "extended:node-gate-build@post-integration-test", "extended:node-gate-bundle@post-integration-test",
                "extended:node-gate-e2e@post-integration-test"
            ),
            gates,
            "wiring judges the harness before npm-inventory writes evidence the package-phase policies read, the "
                + "facet gates of Default run beside the core gates in verify, and the Extended ones only in its "
                + "profile, before verify, since a profile's verify executions would follow module-complete"
        );
    }

    @Test
    void endsEveryVerifyPhaseWithModuleCompletion() {
        List<String> verify = Stream.of(
            ProjectFiles.rootPom(), ProjectFiles.parentPom(), ProjectFiles.javaParentPom(),
            ProjectFiles.parentPom().resolveSibling("../airness-parent-spring-boot/pom.xml").normalize(),
            ProjectFiles.nodeParentPom()
        ).flatMap(pom -> ProjectFiles.descendants(ProjectFiles.document(pom), "execution"))
            .filter(execution -> Xml.text(execution, "phase").map("verify"::equals).orElse(false))
            .map(ParentPlacementTest::idAtPhase)
            .toList();
        assertEquals(
            List.of(
                "release:sign-artifacts@verify", "node-gate-types@verify", "node-gate-biome@verify",
                "node-gate-oxlint@verify", "node-gate-eslint@verify", "node-gate-source-rules@verify",
                "node-gate-config-refs@verify", "node-gate-javascript-allowlist@verify", "node-gate-css@verify",
                "node-gate-tailwind-tokens@verify", "node-gate-arch@verify", "node-gate-type-coverage@verify",
                "node-gate-knip@verify", "node-gate-tests@verify", "node-gate-install-scripts@verify",
                "node-gate-release-age@verify", "airness-coverage-evidence@verify", "airness-governance@verify",
                "airness-artifact@verify", "airness-module-complete@verify", "airness-coverage-report@verify",
                "airness-coverage-check@verify"
            ),
            verify,
            "a profile's and a child parent's airness executions in verify run after every one the generic build "
                + "binds, so only that build binds the plugin in verify, ending with module-complete; the JaCoCo "
                + "plugin precedes it, and signing alone may follow it"
        );
    }

    @Test
    void listsTheIgnoredFilesAgainBeforeTheLinterReadsTheTree() {
        List<String> packaged = ProjectFiles.elementChildren(
            ProjectFiles.child(ProjectFiles.document(ProjectFiles.parentPom()), "build", "plugins")
        ).flatMap(ParentPlacementTest::packagedGoals).toList();
        assertEquals(
            List.of(AIRNESS + "editorconfig-excludes", "editorconfig-maven-plugin:check"),
            packaged.subList(Math.max(0, packaged.size() - 2), packaged.size()),
            "Maven runs the goals of one phase in the order of the plugins that bind them, and an inherited plugin "
                + "keeps the parent's place whatever a child declares, so with the linter declared after the Airness "
                + "plugin and the listing last among its package executions here, the check follows the listing"
        );
    }

    @Test
    void runsOnlyNamedAirnessGoalsBetweenTheIgnoredListAndTheLinter() {
        Set<String> between = ProjectFiles.parentFiles().stream()
            .flatMap(ParentPlacementTest::mergedAfterTheListing)
            .map(ParentPlacementTest::idAtPhase)
            .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(
            new TreeSet<>(BETWEEN_LISTING_AND_LINTER.keySet()),
            between,
            "an Airness goal a profile or a child parent binds at package runs after the ignored files are listed "
                + "again and before the linter reads the tree, so each is named with why it writes no ignored file "
                + "outside the build directory: " + BETWEEN_LISTING_AND_LINTER
        );
    }

    // The Airness executions one project file binds at package that Maven merges after the generic build's own:
    // every one a child parent binds, and every one a profile binds, the generic parent's profiles included.
    private static Stream<Element> mergedAfterTheListing(Path pom) {
        boolean generic = pom.equals(ProjectFiles.parentPom());
        return plugins(ProjectFiles.document(pom))
            .filter(plugin -> Xml.text(plugin, "artifactId").map(AIRNESS_PLUGIN::equals).orElse(false))
            .flatMap(plugin -> ProjectFiles.descendants(plugin, "execution"))
            .filter(ParentPlacementTest::packaged)
            .filter(execution -> !generic || profileOf(execution).isPresent());
    }

    private static Stream<String> packagedGoals(Element plugin) {
        String artifact = Xml.text(plugin, "artifactId").orElseThrow();
        return ProjectFiles.descendants(plugin, "execution")
            .filter(ParentPlacementTest::packaged)
            .flatMap(execution -> ProjectFiles.descendants(execution, "goal"))
            .map(goal -> artifact + ':' + goal.getTextContent().strip());
    }

    private static boolean packaged(Element execution) {
        return Xml.text(execution, "phase").map(PACKAGE::equals).orElse(false);
    }

    private static String idAtPhase(Element execution) {
        return profileOf(execution).map(profile -> profile + ':').orElse("")
            + Xml.text(execution, "id").orElseThrow() + '@' + Xml.text(execution, "phase").orElseThrow();
    }

    private static Optional<String> profileOf(Node node) {
        return Optional.ofNullable(node.getParentNode()).flatMap(
            parent -> parent instanceof Element element && PROFILE.equals(element.getTagName())
                ? Xml.text(element, "id")
                : profileOf(parent)
        );
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
