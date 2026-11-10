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
 * <p>A setting outside the namespace is refused when it decides a verdict: one a bound plugin reads in place of
 * a parameter the parents leave open, which {@link PluginProperties} classifies for every such parameter, and
 * the few the Airness plugin reads beside those.
 *
 * <p>Read from the raw model rather than the effective one, for the reason {@link MavenModelPolicy}
 * gives: a value in a profile that is not active today is a bypass waiting for the day it is.
 */
@UtilityClass
public final class ProjectProperties {

    private static final String NAMESPACE = "airness.";
    /**
     * The only Airness settings a project may declare. Every entry is documented as a project key in the
     * user guide, and nothing reaches this list by being merely harmless.
     */
    private static final Set<String> PROJECT_OWNED = Set.of(
        "airness.assets.unmanaged",
        CoverageExclusions.PROPERTY,
        "airness.dependency-check.suppression.file",
        "airness.generated.files",
        "airness.node.projects",
        "airness.package.root",
        "airness.test.timeout",
        "airness.typography.excludes"
    );
    /**
     * Settings outside the Airness namespace that decide a verdict and that no third-party plugin reads, so
     * {@link PluginProperties} has no row for them. The Airness plugin reads this one beside the JaCoCo data it
     * takes from the JaCoCo plugin's own property. {@link ManagedVersions} owns every airness.* name it protects,
     * so nothing there is repeated here.
     */
    private static final Set<String> RESERVED = Set.of("jacoco.reportFile");

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

    /**
     * Whether a child project file may not declare a setting, read the way {@link #problems} reads one.
     *
     * @param property the property name
     * @return whether the harness keeps the setting
     */
    public static boolean reserves(String property) {
        return refused(property);
    }

    /**
     * Whether a setting in the Airness namespace is one the user guide documents as the project's own.
     *
     * @param property the property name
     * @return whether a child project file may declare it as a project key
     */
    public static boolean ownedByProject(String property) {
        return PROJECT_OWNED.contains(property);
    }

    private static Stream<String> refusals(Collection<Element> declared) {
        return declared.stream()
            .map(Element::getTagName)
            .filter(ProjectProperties::refused)
            .distinct()
            .map(property -> "Remove child property " + property + "; it can bypass the Airness verdict");
    }

    private static boolean refused(String property) {
        return RESERVED.contains(property) || PluginProperties.reserves(property) || unowned(property);
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
