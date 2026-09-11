package eu.ciechanowiec.airness.governance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The committable inputs of the external scanners, independent of their native ignore conventions.
 *
 * @param root repository root
 */
public record ScannerInventory(Path root) {

    private static final Pattern SHELL_NAME = Pattern.compile(".+\\.(?:sh|bash|dash|ksh|bats)$");
    private static final Pattern SHEBANG = Pattern.compile(
        "^#![^\\r\\n]*[/\\s](?:ba|da|k)?sh(?:\\s|$)"
    );
    private static final Pattern DISABLE = Pattern.compile(
        "(?m)^\\s*#\\s*(?:shellcheck\\s+.*(?:disable|external-sources|extended-analysis|source-path)\\s*=|"
            + "(?:checkov|bridgecrew)\\s*:\\s*skip)|checkov\\.io/skip"
    );
    private static final Set<String> CONFIGURATIONS = Set.of(
        ".shellcheckrc", "shellcheckrc", ".checkov.yml", ".checkov.yaml", ".checkov.baseline"
    );

    /**
     * Every present committable file, including ordinary unstaged work.
     *
     * @return absolute paths in Git's order
     */
    public List<Path> files() {
        return Repository.trackedFiles(this.root);
    }

    /**
     * Supported shell files identified by name or interpreter declaration.
     *
     * @return shell inputs
     */
    public List<Path> scripts() {
        return this.files().stream().filter(ScannerInventory::script).toList();
    }

    /**
     * Configuration that could hide findings, and links that escape the input tree.
     *
     * @return violations that must be repaired before starting a scanner
     */
    public List<String> problems() {
        return this.files().stream().flatMap(this::problems).sorted().toList();
    }

    private Stream<String> problems(Path file) {
        return this.structureProblem(file).map(Stream::of).orElseGet(() -> this.contentProblems(file));
    }

    private Optional<String> structureProblem(Path file) {
        String relative = this.root.relativize(file).toString();
        if (CONFIGURATIONS.contains(file.getFileName().toString())) {
            return Optional.of(relative + ": scanner configuration belongs to Airness; remove the local copy");
        }
        return Files.isSymbolicLink(file)
            ? Optional.of(relative + ": scanner inputs must be regular repository files, not symbolic links") : Optional
                .empty();
    }

    private Stream<String> contentProblems(Path file) {
        String relative = this.root.relativize(file).toString();
        Stream<String> hints = script(file) ? ShellSourceHints.problems(this.root, file).stream() : Stream.empty();
        Stream<String> directives = Repository.readText(file).stream()
            .filter(_ -> script(file) || configuration(file))
            .filter(text -> DISABLE.matcher(text).find())
            .map(_ -> relative + ": scanner suppressions belong to Airness; remove the disable directive");
        return Stream.concat(hints, directives);
    }

    private static boolean script(Path file) {
        return SHELL_NAME.matcher(file.getFileName().toString()).matches()
            || Repository.readText(file).filter(text -> SHEBANG.matcher(text).find()).isPresent();
    }

    private static boolean configuration(Path file) {
        String name = file.getFileName().toString();
        return Stream.of(".yaml", ".yml", ".tf", ".json", ".bicep", ".dockerfile").anyMatch(name::endsWith)
            || name.contains("Dockerfile");
    }
}
