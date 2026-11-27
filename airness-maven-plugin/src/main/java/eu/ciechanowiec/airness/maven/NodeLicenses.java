package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.ProjectLicenses;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Every {@code package.json} of a module declares a license the repository's root pom names, and one that declares
 * the code proprietary is private.
 *
 * <p>The root pom is the project's one license statement, so a node project stating another would contradict it in
 * the software bill of materials and on the registry. The string form is the one npm and the SPDX tooling read,
 * since the object and array forms are deprecated. A proprietary package is kept off the public registry by marking it
 * private, which is the one npm setting that refuses a publish outright.
 */
final class NodeLicenses {

    private static final String HEADLINE = "Each package.json declares a license the root pom names";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String LEGACY = "licenses";

    private final List<String> offences;

    /**
     * Reads every manifest of one module.
     *
     * @param module    the module directory
     * @param manifests the module's {@code package.json} files
     * @param root      the license names the root pom declares, none when it declares none
     */
    NodeLicenses(Path module, Collection<Path> manifests, List<String> root) {
        this.offences = root.isEmpty()
            ? List.of()
            : manifests.stream().flatMap(manifest -> problems(module, manifest, root)).toList();
    }

    /**
     * The verdict over the module's manifests.
     *
     * @return the single verdict this check produces
     */
    List<Findings> findings() {
        return List.of(new Findings(HEADLINE, this.offences));
    }

    private static Stream<String> problems(Path module, Path manifest, List<String> root) {
        String name = module.relativize(manifest).toString().replace('\\', '/');
        JsonNode document = read(manifest);
        return form(name, document, root).map(Stream::of).orElseGet(
            () -> declared(name, document, root, document.path(NpmPackage.LICENSE).asText())
        );
    }

    // Why a manifest states no license in the string form, which is the one the other rules can read.
    private static Optional<String> form(String name, JsonNode document, List<String> root) {
        boolean deprecated = document.has(LEGACY) || document.path(NpmPackage.LICENSE).isContainerNode();
        if (deprecated) {
            return Optional.of(
                name + " writes its license in the deprecated object form; write \"license\" as a string"
            );
        }
        return Optional.of(name + " declares no license; write \"license\": \"" + root.getFirst() + '"')
            .filter(_ -> !document.path(NpmPackage.LICENSE).isTextual());
    }

    private static Stream<String> declared(String name, JsonNode document, List<String> root, String license) {
        Stream<String> unnamed = root.contains(license)
            ? Stream.empty()
            : Stream.of(name + " declares license " + license + ", but the root pom names " + String.join(", ", root));
        boolean published = !document.path("private").isBoolean() || !document.path("private").asBoolean();
        Stream<String> publishable = ProjectLicenses.PROPRIETARY.equals(license) && published
            ? Stream.of(name + " declares the code proprietary but is not \"private\": true")
            : Stream.empty();
        return Stream.concat(unnamed, publishable);
    }

    private static JsonNode read(Path manifest) {
        try {
            return MAPPER.readTree(manifest.toFile());
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + manifest, exception);
        }
    }
}
