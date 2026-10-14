package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.ciechanowiec.airness.governance.ReportPackage;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
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
 */
@UtilityClass
final class ReportSbom {

    private static final int VERSION = 1;

    static String assemble(Collection<ReportPackage> packages, Collection<ReportModule> modules) {
        Set<String> coordinates = packages.stream().map(ReportPackage::coordinate).collect(
            Collectors.toUnmodifiableSet()
        );
        Stream<Map<String, Object>> projects = modules.stream().filter(
            module -> !coordinates.contains(module.coordinate())
        ).map(module -> moduleComponent(module.coordinate()));
        List<Map<String, Object>> components = Stream.concat(packages.stream().map(ReportSbom::component), projects)
            .toList();
        List<Map<String, Object>> dependencies = graph(modules);
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

    private static List<Map<String, Object>> graph(Collection<ReportModule> modules) {
        return modules.stream().flatMap(ReportSbom::edges)
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue, ReportGraph::union))
            .entrySet().stream().sorted(Map.Entry.comparingByKey())
            .map(ReportSbom::dependency).toList();
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
        String[] coordinate = entry.coordinate().split(":", -1);
        List<Map<String, Object>> licenses = ReportLicenseChoice.from(entry.licenses());
        return Map.of(
            "type", "library", "bom-ref", entry.coordinate(), "group", coordinate[0],
            "name", coordinate[1], "version", coordinate[coordinate.length - 1], "licenses", licenses,
            "properties", entry.uses().stream().map(
                use -> Map.of(
                    "name", "airness:use",
                    "value", use.module() + " / " + use.scope() + " / " + use.relation()
                )
            ).toList()
        );
    }
}
