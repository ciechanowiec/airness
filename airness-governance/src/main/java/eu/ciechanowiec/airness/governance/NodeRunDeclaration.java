package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Whether a child declaration of the Airness plugin is nothing but the project's own node-run build steps.
 *
 * <p>Only the project knows which directory and script build its frontend, so only it can bind node-run. Such
 * a declaration names, for each execution, only an id, a phase, the one goal and the {@code project} and
 * {@code script} it runs. The id must be its own: Maven merges an execution into an inherited one of the same
 * id, keeping the child's phase, so a reused id could move inherited goals anywhere. The phase must come
 * before {@code prepare-package}, so a script cannot rewrite node evidence or run after the closing checks.
 * And no {@code combine} attribute may change how the declaration merges into what it inherits.
 */
@UtilityClass
final class NodeRunDeclaration {

    private static final Set<String> NODE_RUN_PLUGIN = Set.of("groupId", "artifactId", "executions");

    private static final Set<String> NODE_RUN_EXECUTION = Set.of("id", "phase", "goals", "configuration");

    private static final Set<String> NODE_RUN_CONFIGURATION = Set.of("project", "script");

    private static final List<String> RESERVED = List.of("airness-", "default-");

    private static final Set<String> NODE_RUN_PHASES = Set.of(
        "generate-sources", "process-sources", "generate-resources", "process-resources", "compile",
        "process-classes", "generate-test-sources", "process-test-sources", "generate-test-resources",
        "process-test-resources", "test-compile", "process-test-classes", "test"
    );

    /**
     * Whether a plugin declaration holds only the project's own node-run executions.
     *
     * @param plugin    the declaration of the Airness plugin
     * @param inherited the ids of the Airness plugin executions the project inherits
     * @return whether the declaration is nothing but frontend builds
     */
    static boolean only(Node plugin, Set<String> inherited) {
        List<Element> executions = Xml.firstChild(plugin, "executions").stream()
            .flatMap(listed -> Xml.children(listed, "execution").stream())
            .toList();
        boolean shaped = children(plugin, NODE_RUN_PLUGIN) && !merges((Element) plugin);
        return shaped && !executions.isEmpty()
            && executions.stream().allMatch(execution -> execution(execution, inherited));
    }

    // Only the project's own step before packaging: an id of its own, so it merges into nothing it inherits,
    // and a phase before prepare-package, so it cannot rewrite what the node evidence and later checks read.
    private static boolean execution(Element execution, Set<String> inherited) {
        List<String> goals = Xml.firstChild(execution, "goals").stream()
            .flatMap(listed -> Xml.children(listed, "goal").stream())
            .map(goal -> goal.getTextContent().strip())
            .toList();
        boolean shaped = children(execution, NODE_RUN_EXECUTION) && List.of("node-run").equals(goals);
        boolean placed = ownId(Xml.idTextOrEmpty(execution), inherited) && earlyPhase(execution);
        return shaped && placed && configured(execution);
    }

    private static boolean earlyPhase(Element execution) {
        return Xml.text(execution, "phase").map(NODE_RUN_PHASES::contains).orElse(true);
    }

    private static boolean configured(Element execution) {
        return Xml.firstChild(execution, "configuration").stream()
            .allMatch(configuration -> children(configuration, NODE_RUN_CONFIGURATION));
    }

    private static boolean ownId(String id, Set<String> inherited) {
        return !(id.isEmpty() || inherited.contains(id) || RESERVED.stream().anyMatch(id::startsWith));
    }

    // A combine attribute changes how Maven merges the declaration into what it inherits.
    private static boolean merges(Element plugin) {
        NodeList elements = plugin.getElementsByTagName("*");
        return Stream.concat(
            Stream.of(plugin), IntStream.range(0, elements.getLength()).mapToObj(elements::item).map(
                Element.class::cast
            )
        ).anyMatch(element -> element.hasAttribute("combine.self") || element.hasAttribute("combine.children"));
    }

    private static boolean children(Node parent, Set<String> allowed) {
        NodeList children = parent.getChildNodes();
        return IntStream.range(0, children.getLength())
            .mapToObj(children::item)
            .filter(child -> child.getNodeType() == Node.ELEMENT_NODE)
            .allMatch(child -> allowed.contains(child.getNodeName()));
    }
}
