package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * The project settings with which pnpm would pick a pnpm or a Node of its own instead of the harness's.
 *
 * <p>pnpm downloads and runs the pnpm a {@code packageManager} or {@code devEngines} field names, and the Node
 * a {@code useNodeVersion}, {@code nodeVersion} or {@code executionEnv} setting names, from a project's
 * {@code package.json}, its {@code pnpm-workspace.yaml} or its {@code .npmrc}. Every gate and every
 * {@code node-run} script would then run on that Node. This is the problem the refusal of frontend-maven-plugin
 * solves, written as configuration, so each such setting is refused where it is written.
 */
@UtilityClass
final class NodeSelections {

    private static final Set<String> MANIFEST_FIELDS = Set.of("packageManager", "devEngines");
    private static final Set<String> PNPM_SETTINGS = Set.of(
        "useNodeVersion", "nodeVersion", "executionEnv", "managePackageManagerVersions"
    );
    private static final Set<String> NPMRC_KEYS = Set.of(
        "use-node-version", "node-version", "execution-env", "manage-package-manager-versions"
    );
    private static final String REPAIR = " lets pnpm choose a pnpm or Node of its own; remove it, since Airness "
        + "supplies both";

    /**
     * Every Node- or pnpm-selecting setting in a module's node projects.
     *
     * @param module    the module directory
     * @param manifests every {@code package.json} that belongs to the module
     * @param declared  the node projects the module declares
     * @return one problem per setting, naming the file and the setting
     */
    static Stream<String> problems(Path module, Collection<Path> manifests, NodeProjects declared) {
        List<Path> roots = declared.projects().stream()
            .filter(project -> !project.outside())
            .map(NodeProjects.NodeProject::directory)
            .toList();
        return Stream.of(
            manifests.stream().flatMap(manifest -> manifest(module, manifest)),
            roots.stream().flatMap(root -> workspace(module, root.resolve("pnpm-workspace.yaml"))),
            roots.stream().flatMap(root -> npmrc(module, root.resolve(".npmrc")))
        ).flatMap(problems -> problems);
    }

    private static Stream<String> manifest(Path module, Path manifest) {
        try {
            JsonNode document = new ObjectMapper().readTree(manifest.toFile());
            JsonNode pnpm = document.path("pnpm");
            return Stream.concat(
                MANIFEST_FIELDS.stream().filter(document::has),
                PNPM_SETTINGS.stream().filter(pnpm::has).map(setting -> "pnpm." + setting)
            ).sorted().map(setting -> named(module, manifest) + ": " + setting + REPAIR);
        } catch (IOException exception) {
            return Stream.of(named(module, manifest) + ": cannot be read as JSON (" + exception.getMessage() + ")");
        }
    }

    private static Stream<String> workspace(Path module, Path file) {
        return Files.isRegularFile(file) ? workspaceSettings(module, file) : Stream.empty();
    }

    private static Stream<String> workspaceSettings(Path module, Path file) {
        try {
            Object document = new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(file));
            Map<?, ?> settings = document instanceof Map<?, ?> mapping ? mapping : Map.of();
            return PNPM_SETTINGS.stream()
                .filter(settings::containsKey)
                .sorted()
                .map(setting -> named(module, file) + ": " + setting + REPAIR);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        } catch (YAMLException _) {
            return Stream.of(named(module, file) + ": cannot be read as YAML");
        }
    }

    private static Stream<String> npmrc(Path module, Path file) {
        if (!Files.isRegularFile(file)) {
            return Stream.empty();
        }
        try (Stream<String> lines = Files.lines(file)) {
            List<String> keys = lines.map(String::strip)
                .filter(line -> line.contains("="))
                .map(line -> line.substring(0, line.indexOf('=')).strip())
                .filter(NPMRC_KEYS::contains)
                .toList();
            return keys.stream().map(key -> named(module, file) + ": " + key + REPAIR);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }

    private static String named(Path module, Path file) {
        return module.relativize(file).toString().replace('\\', '/');
    }
}
