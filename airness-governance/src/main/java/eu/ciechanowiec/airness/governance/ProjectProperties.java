package eu.ciechanowiec.airness.governance;

import java.util.Collection;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The settings a project may declare for itself, and the ones the harness keeps.
 *
 * <p>The Airness namespace is refused by default and opened one name at a time. A list of the settings a
 * project may not touch fails in the worse direction: a setting added to the harness later is absent from
 * that list, so the first project to set it bypasses a check and nothing says so. An allowlist fails the
 * other way, on the day the setting is written, which is the day somebody is in a position to ask why.
 *
 * <p>Read from the raw model rather than the effective one, for the reason {@link MavenModelPolicy}
 * gives: a value in a profile that is not active today is a bypass waiting for the day it is.
 */
@UtilityClass
public final class ProjectProperties {

    private static final String NAMESPACE = "airness.";
    private static final String COVERAGE_EXCLUDES = "airness.coverage.excluded.classes";
    /**
     * The only Airness settings a project may declare. Every entry is documented as a project key in the
     * user guide, and nothing reaches this list by being merely harmless.
     */
    private static final Set<String> PROJECT_OWNED = Set.of(
        "airness.assets.unmanaged",
        COVERAGE_EXCLUDES,
        "airness.dependency-check.suppression.file",
        "airness.package.root",
        "airness.test.timeout",
        "airness.typography.excludes"
    );
    /**
     * Settings outside the Airness namespace that decide a verdict just as much as one inside it.
     * {@link ManagedVersions} owns every airness.* name it protects, so nothing there is repeated here.
     */
    private static final Set<String> RESERVED = Set.of(
        "jacoco.dataFile",
        "jacoco.reportFile",
        "maven.test.skip",
        "skipTests"
    );

    /**
     * Reads every property block of a raw project file.
     *
     * @param root the project element
     * @return one problem per declared setting the project does not own
     */
    public static Stream<String> problems(Element root) {
        return refusals(
            DeclaredCoordinates.propertyBlocks(root).flatMap(ProjectProperties::elements).toList()
        );
    }

    private static Stream<String> refusals(Collection<Element> declared) {
        return declared.stream()
            .map(Element::getTagName)
            .filter(ProjectProperties::refused)
            .distinct()
            .map(property -> "Remove child property " + property + "; it can bypass the Airness verdict");
    }

    private static boolean refused(String property) {
        return RESERVED.contains(property) || unowned(property);
    }

    private static boolean unowned(String property) {
        return property.startsWith(NAMESPACE) && !PROJECT_OWNED.contains(property);
    }

    private static Stream<Element> elements(Node block) {
        return IntStream.range(0, block.getChildNodes().getLength())
            .mapToObj(index -> block.getChildNodes().item(index))
            .filter(node -> node.getNodeType() == Node.ELEMENT_NODE)
            .map(Element.class::cast);
    }
}
