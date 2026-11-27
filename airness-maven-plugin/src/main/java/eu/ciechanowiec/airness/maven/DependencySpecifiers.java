package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.ciechanowiec.airness.governance.Findings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Every registry dependency of a module's node projects names the exact version it installs.
 *
 * <p>The frozen lockfile fixes what a build installs, but a range lets each refresh of the lockfile move a
 * dependency without a word in the manifest: the refresh after a harness update, an update bot, or
 * {@code pnpm update}. An exact version changes only in a diff that names it, as a Maven dependency does, and the
 * manifest states what runs. A catalog of {@code pnpm-workspace.yaml} stands in for the version a manifest would
 * write, so its entries are held to the same rule.
 *
 * <p>Peer dependencies state what a library accepts from its user, which is a range by design. A specifier that
 * names a protocol or a path, such as {@code workspace:}, {@code link:} or a git address, is no registry version,
 * and where it resolves from is the {@code release-age} gate's to judge. An alias through {@code npm:} is judged by
 * the version after its package name.
 */
final class DependencySpecifiers {

    private static final String HEADLINE = "Name the exact version of every registry dependency";
    private static final String ALIAS = "npm:";
    private static final String CATALOG = "catalog";
    private static final String CATALOGS = "catalogs";
    private static final Pattern EXACT = Pattern.compile(
        "=?v?\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.-]+)?(\\+[0-9A-Za-z.-]+)?"
    );
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<String> offences;

    /**
     * Reads every manifest and workspace file of one module.
     *
     * @param module the module directory
     * @param files  the module's files, of which each {@code package.json} and {@code pnpm-workspace.yaml} is read
     */
    DependencySpecifiers(Path module, Collection<Path> files) {
        this.offences = files.stream().flatMap(file -> problems(module, file)).toList();
    }

    /**
     * The verdict over the module's dependency specifiers.
     *
     * @return the single verdict this check produces
     */
    List<Findings> findings() {
        return List.of(new Findings(HEADLINE, this.offences));
    }

    private static Stream<String> problems(Path module, Path file) {
        String name = module.relativize(file).toString().replace('\\', '/');
        return specifiers(file)
            .filter(specifier -> loose(specifier.getValue()))
            .map(
                specifier -> name + ": " + specifier.getKey() + " asks for " + specifier.getValue()
                    + "; name the exact version"
            );
    }

    // Each dependency a file declares, labelled by where it is declared, with the specifier it asks for.
    private static Stream<Map.Entry<String, String>> specifiers(Path file) {
        String name = String.valueOf(file.getFileName());
        if (NodeManifests.MANIFEST.equals(name)) {
            return manifest(file);
        }
        return PnpmWorkspace.FILE.equals(name) ? catalogs(file) : Stream.empty();
    }

    private static boolean loose(String specifier) {
        String version = specifier.startsWith(ALIAS) ? aliased(specifier) : specifier;
        boolean located = version.contains(":") || version.contains("/");
        return !located && !EXACT.matcher(version).matches();
    }

    // The version an alias asks for: npm:name@version or npm:@scope/name@version, none when it names no version.
    private static String aliased(String specifier) {
        int at = specifier.lastIndexOf('@');
        return at > ALIAS.length() ? specifier.substring(at + 1) : "";
    }

    private static Stream<Map.Entry<String, String>> manifest(Path file) {
        JsonNode document = json(file);
        return PackageManifests.INSTALLED.stream().flatMap(
            section -> document.path(section).propertyStream()
                .map(dependency -> Map.entry(section + " " + dependency.getKey(), dependency.getValue().asText()))
        );
    }

    private static Stream<Map.Entry<String, String>> catalogs(Path file) {
        Map<?, ?> settings = PnpmWorkspace.settings(file);
        Stream<Map.Entry<String, String>> named = mapping(settings.get(CATALOGS)).entrySet().stream()
            .flatMap(catalog -> entries(CATALOGS + "." + catalog.getKey(), catalog.getValue()));
        return Stream.concat(entries(CATALOG, settings.get(CATALOG)), named);
    }

    private static Stream<Map.Entry<String, String>> entries(String label, @Nullable Object catalog) {
        return mapping(catalog).entrySet().stream()
            .map(entry -> Map.entry(label + " " + entry.getKey(), String.valueOf(entry.getValue())));
    }

    private static Map<?, ?> mapping(@Nullable Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    // A file another check refuses as unreadable declares nothing here, so it is reported once.
    private static JsonNode json(Path file) {
        try {
            return MAPPER.readTree(file.toFile());
        } catch (JsonProcessingException _) {
            return MAPPER.missingNode();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }
}
