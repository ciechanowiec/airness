package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The configuration blocks the root project file copies from {@code airness-parent} still say the same
 * thing, and the analyzer that reaches Airness's own sources by naming them still names all of them.
 *
 * <p>{@link SelfHarnessMirrorTest} makes the same argument about the rules and the licences. This is a
 * second class rather than six more methods in that one, for the reason {@link ProjectFiles} is a class
 * of its own: the rule set caps how many methods one class carries.
 *
 * <p>Each block below is copied by hand and carries a comment in the root project file saying so. A
 * comment is not a check. Every one of these was identical when this class was written, so what it adds
 * is the day one of them stops being identical, which is otherwise a day nothing reports.
 */
class SelfHarnessBlocksTest {

    private static final String SUREFIRE = "maven-surefire-plugin";
    private static final String DEPENDENCY = "maven-dependency-plugin";
    private static final String CONFIGURATION = "configuration";
    private static final String PROPERTIES = "properties";
    private static final String PARAMETERS = "configurationParameters";
    private static final String NULLAWAY_EXCLUSIONS = "airness.nullaway.excluded-field-annotations";
    private static final String CHECKSTYLE_EXECUTION = "airness-self-checkstyle";
    private static final String MODULE_SEPARATOR = "/../";

    @Test
    void mirrorsTheTestExecutionParametersTheParentBinds() {
        Element parent = ProjectFiles.child(
            ProjectFiles.bound(ProjectFiles.parentPom(), SUREFIRE), CONFIGURATION, PROPERTIES, PARAMETERS
        );
        Element root = ProjectFiles.child(
            ProjectFiles.managed(ProjectFiles.rootPom(), SUREFIRE), CONFIGURATION, PROPERTIES, PARAMETERS
        );
        assertEquals(
            settingsOf(parent), settingsOf(root),
            "the timeout, ordering and seed a consumer's tests run under are the ones this repository's run under"
        );
    }

    @Test
    void mirrorsTheDependencyAnalysisExemptionsTheParentBinds() {
        Element parent = ProjectFiles.child(
            ProjectFiles.managed(ProjectFiles.parentPom(), DEPENDENCY),
            CONFIGURATION, "ignoredUnusedDeclaredDependencies"
        );
        Element root = ProjectFiles.child(
            ProjectFiles.managed(ProjectFiles.rootPom(), DEPENDENCY),
            CONFIGURATION, "ignoredUnusedDeclaredDependencies"
        );
        assertEquals(
            exemptionsIn(parent), exemptionsIn(root),
            "a dependency this repository excuses for itself is one it excuses for a consumer, and no more"
        );
    }

    @Test
    void mirrorsTheNullnessFieldExclusionsTheParentBinds() {
        assertEquals(
            ProjectFiles.property(ProjectFiles.parentPom(), NULLAWAY_EXCLUSIONS),
            ProjectFiles.property(ProjectFiles.rootPom(), NULLAWAY_EXCLUSIONS),
            "an annotation the null checker treats as initializing a field does so on both sides of the harness"
        );
    }

    @Test
    void readsEveryModuleWithProductionJavaWithCheckstyle() {
        Element directories = ProjectFiles.child(
            ProjectFiles.execution(ProjectFiles.parentPom(), CHECKSTYLE_EXECUTION),
            CONFIGURATION, "sourceDirectories"
        );
        assertEquals(
            ProjectFiles.withProductionJava().stream().map(ProjectFiles::moduleName).sorted().toList(),
            modulesOf(directories),
            "Checkstyle reaches this repository's own sources only by naming the modules, so a module it "
                + "does not name is a module it does not read"
        );
    }

    private static List<String> settingsOf(Node parameters) {
        return Arrays.stream(parameters.getTextContent().split("\n"))
            .map(String::strip)
            .filter(setting -> !setting.isEmpty())
            .sorted()
            .toList();
    }

    private static List<String> exemptionsIn(Node ignored) {
        return ProjectFiles.elementChildren(ignored)
            .map(Element::getTextContent)
            .map(String::strip)
            .sorted()
            .toList();
    }

    /*
     * A named source directory reads ${project.basedir}/../<module>/src/main/java, so the module is the
     * segment after the step out of this one.
     */
    private static List<String> modulesOf(Node directories) {
        return ProjectFiles.elementChildren(directories)
            .map(Element::getTextContent)
            .map(String::strip)
            .map(SelfHarnessBlocksTest::moduleIn)
            .distinct()
            .sorted()
            .toList();
    }

    private static String moduleIn(String directory) {
        int step = directory.indexOf(MODULE_SEPARATOR);
        String named = directory.substring(step + MODULE_SEPARATOR.length());
        return Path.of(named).getName(0).toString();
    }
}
