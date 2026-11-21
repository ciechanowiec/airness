package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The committable inputs of the external scanners, independent of their native ignore conventions.
 *
 * <p>Which files a scanner would read as its own configuration is decided by {@link ToolConfigurationFiles},
 * the one inventory of such files, which also refuses them on every build rather than only before a scan.
 *
 * <p>No scanner follows a symbolic link: an input is a regular file, copied into the scanner's own tree. A link
 * within the committed tree, such as one that replaces a copied document, is passed over, since what it leads to
 * is committed and scanned as itself. A link that dangles, leaves the repository or leads to something nothing
 * commits is refused, because it would put input before a scanner that the inventory never selected.
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
            + "(?:checkov|bridgecrew)\\s*:\\s*skip|hadolint\\s+(?:global\\s+)?ignore\\s*=)|checkov\\.io/skip"
    );

    /**
     * Every present committable regular file, including ordinary unstaged work, without the symbolic links.
     *
     * @return absolute paths in Git's order
     */
    public List<Path> files() {
        return Repository.trackedFiles(this.root).stream().filter(file -> !Files.isSymbolicLink(file)).toList();
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
     * Configuration that could hide findings, and links that lead outside the committed tree.
     *
     * @return violations that must be repaired before starting a scanner
     */
    public List<String> problems() {
        List<Path> tracked = Repository.trackedFiles(this.root);
        Set<Path> committed = tracked.stream()
            .filter(file -> !Files.isSymbolicLink(file))
            .map(ScannerInventory::real)
            .flatMap(Optional::stream)
            .collect(Collectors.toUnmodifiableSet());
        return tracked.stream().flatMap(file -> this.problems(file, committed)).sorted().toList();
    }

    private Stream<String> problems(Path file, Set<Path> committed) {
        if (Files.isSymbolicLink(file)) {
            return this.linkProblem(file, committed).stream();
        }
        return this.problems(file);
    }

    private Stream<String> problems(Path file) {
        return this.structureProblem(file).map(Stream::of).orElseGet(() -> this.contentProblems(file));
    }

    private Optional<String> structureProblem(Path file) {
        String relative = this.root.relativize(file).toString();
        if (ToolConfigurationFiles.scannerConfiguration(this.root.relativize(file))) {
            return Optional.of(relative + ": scanner configuration belongs to Airness; remove the local copy");
        }
        return Optional.empty();
    }

    private Optional<String> linkProblem(Path link, Set<Path> committed) {
        Optional<Path> root = real(this.root);
        boolean selected = real(link)
            .filter(target -> root.filter(target::startsWith).isPresent())
            .filter(target -> leadsToCommitted(target, committed))
            .isPresent();
        return selected ? Optional.empty() : Optional.of(
            this.root.relativize(link) + ": a symbolic link may lead only to a committed file, or a directory holding"
                + " one, inside the repository; scanners never follow it, so commit what it leads to or remove it"
        );
    }

    private static boolean leadsToCommitted(Path target, Set<Path> committed) {
        return committed.contains(target)
            || (Files.isDirectory(target) && committed.stream().anyMatch(file -> file.startsWith(target)));
    }

    private static Optional<Path> real(Path path) {
        try {
            return Optional.of(path.toRealPath());
        } catch (IOException _) {
            return Optional.empty();
        }
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
