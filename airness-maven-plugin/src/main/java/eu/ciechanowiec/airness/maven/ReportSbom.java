package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.airness.governance.ReportPackage;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.cyclonedx.Version;
import org.cyclonedx.exception.ParseException;
import org.cyclonedx.parsers.JsonParser;

/**
 * The aggregate resolved-input inventory, separate from published production-scope module SBOMs.
 *
 * <p>It is one document for the whole repository: every Maven module's resolved artifacts and build tools,
 * every node project's npm packages as its own CycloneDX document lists them, the Node and pnpm that ran the
 * node gates, and one dependency graph joining the Maven modules to the node projects they declare.
 */
@UtilityClass
final class ReportSbom {

    private static final int VERSION = 1;
    private static final String PACKAGE_URL = "pkg:";
    private static final String REFERENCE = "bom-ref";

    static String assemble(Collection<ReportPackage> packages, Collection<ReportModule> modules) {
        Set<String> coordinates = packages.stream().map(ReportPackage::coordinate).collect(
            Collectors.toUnmodifiableSet()
        );
        Stream<Map<String, Object>> projects = modules.stream().filter(
            module -> !coordinates.contains(module.coordinate())
        ).map(module -> moduleComponent(module.coordinate()));
        List<JsonNode> documents = modules.stream().flatMap(ReportSbom::documents).toList();
        // One component per reference: an npm document describes its packages more fully than the license
        // inventory does, so its entry wins, and a package two node projects resolve is listed once.
        Map<String, Map<String, Object>> merged = Stream.of(
            documents.stream().flatMap(ReportSbom::npmComponents),
            packages.stream().map(ReportSbom::component), projects
        ).flatMap(stream -> stream).collect(
            Collectors.toMap(ReportSbom::reference, component -> component, ReportSbom::richer, LinkedHashMap::new)
        );
        List<Map<String, Object>> components = List.copyOf(merged.values());
        List<Map<String, Object>> dependencies = graph(modules, documents);
        String json = ReportJson.encode(
            Map.of(
                "bomFormat", "CycloneDX", "specVersion", "1.6", "version", VERSION,
                "components", components, "dependencies", dependencies
            )
        );
        validateAggregate(json);
        return json;
    }

    @SneakyThrows
    static void validateAggregate(String json) {
        List<ParseException> errors = new JsonParser().validate(
            json.getBytes(StandardCharsets.UTF_8), Version.VERSION_16
        );
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid aggregate CycloneDX inventory: " + errors);
        }
    }

    static void validate(JsonNode sbom, Collection<ReportPackage> dependencies) {
        JsonNode components = sbom.path("components");
        Set<String> covered = components.valueStream().map(
            component -> component.path("group").asText() + ':'
                + component.path("name").asText() + ':' + component.path("version").asText()
        ).collect(Collectors.toUnmodifiableSet());
        for (ReportPackage entry : dependencies) {
            String[] coordinate = entry.coordinate().split(":", -1);
            String identity = coordinate[0] + ':' + coordinate[1] + ':' + coordinate[coordinate.length - 1];
            boolean included = entry.uses().stream().anyMatch(
                use -> List.of("compile", "runtime", "provided")
                    .contains(use.scope())
            );
            if (included && !covered.contains(identity)) {
                throw new IllegalStateException("The published SBOM omitted a resolved dependency: " + identity);
            }
        }
    }

    @SneakyThrows
    private static Stream<Map.Entry<String, List<String>>> edges(ReportModule module) {
        JsonNode graph = new ObjectMapper().readTree(module.evidence().getOrDefault("Graph", "{}"));
        return graph.properties().stream().map(
            entry -> Map.entry(
                entry.getKey(),
                entry.getValue().valueStream().map(JsonNode::asText).toList()
            )
        );
    }

    private static List<Map<String, Object>> graph(Collection<ReportModule> modules, Collection<JsonNode> documents) {
        Stream<Map.Entry<String, List<String>>> edges = Stream.concat(
            modules.stream().flatMap(ReportSbom::edges), documents.stream().flatMap(ReportSbom::documentEdges)
        );
        return edges.collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue, ReportGraph::union))
            .entrySet().stream().sorted(Map.Entry.comparingByKey())
            .map(ReportSbom::dependency).toList();
    }

    @SneakyThrows
    private static Stream<JsonNode> documents(ReportModule module) {
        JsonNode documents = new ObjectMapper().readTree(
            module.evidence().getOrDefault(NpmReportInputs.DOCUMENTS, "[]")
        );
        return documents.valueStream();
    }

    // The packages of one npm document, then the node project it describes, as the application it is.
    private static Stream<Map<String, Object>> npmComponents(JsonNode document) {
        JsonNode root = document.path("metadata").path("component");
        Stream<JsonNode> project = root.path(REFERENCE).asText().isEmpty() ? Stream.empty() : Stream.of(root);
        return Stream.concat(document.path("components").valueStream(), project).map(ReportSbom::plain);
    }

    private static Stream<Map.Entry<String, List<String>>> documentEdges(JsonNode document) {
        return document.path("dependencies").valueStream().map(
            entry -> Map.entry(
                entry.path("ref").asText(), entry.path("dependsOn").valueStream().map(JsonNode::asText).toList()
            )
        );
    }

    // The npm document's entry, which carries the package's scope and its direct-dependency mark, with the module
    // uses the inventory's entry records, so neither half's facts are lost.
    @SuppressWarnings("unchecked")
    @Justification("JSON objects read as maps hold lists of maps under properties, as the CycloneDX schema has them")
    private static Map<String, Object> richer(Map<String, Object> first, Map<String, Object> second) {
        List<Object> properties = Stream.of(first, second)
            .flatMap(component -> ((Collection<Object>) component.getOrDefault("properties", List.of())).stream())
            .distinct().toList();
        Map<String, Object> merged = new LinkedHashMap<>(first);
        merged.put("properties", properties);
        return merged;
    }

    private static Map<String, Object> plain(JsonNode component) {
        ObjectMapper json = new ObjectMapper();
        return json.convertValue(
            component, json.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
        );
    }

    private static String reference(Map<String, Object> component) {
        return String.valueOf(component.get(REFERENCE));
    }

    private static Map<String, Object> dependency(Map.Entry<String, List<String>> entry) {
        return Map.of("ref", entry.getKey(), "dependsOn", entry.getValue());
    }

    private static Map<String, Object> moduleComponent(String coordinate) {
        String[] parts = coordinate.split(":", -1);
        return Map.of(
            "type", "library", "bom-ref", coordinate, "group", parts[0], "name", parts[1],
            "version", parts[parts.length - 1]
        );
    }

    private static Map<String, Object> component(ReportPackage entry) {
        if (entry.coordinate().startsWith(PACKAGE_URL)) {
            return PackageUrl.parse(entry.coordinate()).component(
                ReportLicenseChoice.from(entry.licenses()), properties(entry)
            );
        }
        String[] coordinate = entry.coordinate().split(":", -1);
        List<Map<String, Object>> licenses = ReportLicenseChoice.from(entry.licenses());
        return Map.of(
            "type", "library", "bom-ref", entry.coordinate(), "group", coordinate[0],
            "name", coordinate[1], "version", coordinate[coordinate.length - 1], "licenses", licenses,
            "properties", properties(entry)
        );
    }

    private static List<Map<String, Object>> properties(ReportPackage entry) {
        return entry.uses().stream().map(
            use -> Map.<String, Object>of(
                "name", "airness:use",
                "value", use.module() + " / " + use.scope() + " / " + use.relation()
            )
        ).toList();
    }
}
